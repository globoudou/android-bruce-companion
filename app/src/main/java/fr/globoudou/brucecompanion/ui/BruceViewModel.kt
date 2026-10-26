package fr.globoudou.brucecompanion.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.globoudou.brucecompanion.core.GeoPackage
import fr.globoudou.brucecompanion.core.Observation
import fr.globoudou.brucecompanion.core.Wigle
import fr.globoudou.brucecompanion.data.Settings
import fr.globoudou.brucecompanion.net.BruceClient
import fr.globoudou.brucecompanion.net.WifiLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Phase { CONNECT, FILES, RESULT }
enum class ConnectMode { AP, CURRENT }

/** Cible d'écriture : un nouveau GeoPackage, ou un existant à compléter. */
sealed interface Target {
    data class New(val name: String) : Target
    data class Existing(val file: File) : Target
}

class BruceViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    private val wifi = WifiLink(app)
    private var client: BruceClient? = null

    private val exportsDir = File(app.filesDir, "exports").apply { mkdirs() }

    // --- État observable par l'UI ---
    var phase by mutableStateOf(Phase.CONNECT); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    var files by mutableStateOf<List<BruceClient.RemoteFile>>(emptyList()); private set
    var selected by mutableStateOf<Set<String>>(emptySet()); private set

    var existing by mutableStateOf<List<File>>(emptyList()); private set
    var lastStats by mutableStateOf<GeoPackage.Stats?>(null); private set
    var lastFile by mutableStateOf<File?>(null); private set
    var deletedCount by mutableStateOf(0); private set

    val csvFiles get() = files.filter { !it.isDir && it.name.endsWith(".csv", ignoreCase = true) }

    fun dismissError() { error = null }

    // --- Connexion ---
    fun connect(mode: ConnectMode) {
        if (busy) return
        error = null
        viewModelScope.launch {
            busy = true
            try {
                status = "Liaison Wi-Fi…"
                val linked = withContext(Dispatchers.IO) {
                    when (mode) {
                        ConnectMode.AP -> wifi.connectToAp(settings.apSsid, settings.apPass)
                        ConnectMode.CURRENT -> wifi.bindToCurrentWifi()
                    }
                }
                if (!linked) {
                    error = if (mode == ConnectMode.AP)
                        "Connexion au point d'accès « ${settings.apSsid} » refusée ou annulée."
                    else
                        "Aucun réseau Wi-Fi actif. Rejoins d'abord le Bruce dans les réglages Wi-Fi."
                    return@launch
                }
                status = "Connexion à ${settings.host}…"
                val c = BruceClient(settings.host)
                withContext(Dispatchers.IO) { c.login(settings.user, settings.password) }
                client = c
                settings.saveConnection(settings.host, settings.user, settings.password)
                loadFiles()
                phase = Phase.FILES
            } catch (e: Exception) {
                error = humanize(e)
            } finally {
                busy = false
                status = null
            }
        }
    }

    fun loadFiles() {
        val c = client ?: return
        viewModelScope.launch {
            busy = true; error = null
            try {
                status = "Lecture de ${settings.folder}…"
                val list = withContext(Dispatchers.IO) { c.listFiles(settings.folder) }
                files = list.sortedByDescending { it.name }
                // Présélection : tout.
                selected = csvFiles.map { it.path }.toSet()
            } catch (e: Exception) {
                error = humanize(e)
            } finally {
                busy = false; status = null
            }
        }
    }

    fun toggle(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    fun selectAll() { selected = csvFiles.map { it.path }.toSet() }
    fun selectNone() { selected = emptySet() }

    fun refreshExisting() {
        existing = exportsDir.listFiles { f -> f.extension == "gpkg" }
            ?.filter { GeoPackage.isCompatible(it) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    // --- Traitement ---
    fun process(target: Target, deleteAfter: Boolean) {
        val c = client ?: return
        val paths = csvFiles.map { it.path }.filter { it in selected }
        if (paths.isEmpty()) { error = "Aucun fichier sélectionné."; return }
        viewModelScope.launch {
            busy = true; error = null; deletedCount = 0
            try {
                val obs = ArrayList<Observation>()
                for ((i, path) in paths.withIndex()) {
                    status = "Téléchargement ${i + 1}/${paths.size}…"
                    val bytes = withContext(Dispatchers.IO) { c.download(path) }
                    obs.addAll(Wigle.parse(bytes.toString(Charsets.UTF_8)))
                }
                if (obs.isEmpty()) {
                    error = "Aucune observation géolocalisée dans les fichiers choisis."
                    return@launch
                }
                status = "Fusion et écriture du GeoPackage…"
                val file = when (target) {
                    is Target.New -> File(exportsDir, sanitize(target.name) + ".gpkg")
                    is Target.Existing -> target.file
                }
                val stats = withContext(Dispatchers.IO) {
                    when (target) {
                        is Target.New -> GeoPackage.writeNew(file, obs)
                        is Target.Existing -> GeoPackage.appendTo(file, obs)
                    }
                }

                if (deleteAfter) {
                    status = "Suppression sur la carte…"
                    var n = 0
                    for (path in paths) {
                        runCatching { withContext(Dispatchers.IO) { c.delete(path) }; n++ }
                    }
                    deletedCount = n
                    loadFiles()
                }

                lastStats = stats
                lastFile = file
                phase = Phase.RESULT
            } catch (e: Exception) {
                error = humanize(e)
            } finally {
                busy = false; status = null
            }
        }
    }

    fun backToFiles() { phase = Phase.FILES; loadFiles() }

    fun disconnect() {
        wifi.release()
        client = null
        files = emptyList()
        selected = emptySet()
        phase = Phase.CONNECT
    }

    override fun onCleared() {
        wifi.release()
    }

    fun suggestedName(): String =
        "wardriving_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    private fun sanitize(name: String): String =
        name.trim().ifBlank { suggestedName() }.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun humanize(e: Exception): String = when (e) {
        is BruceClient.HttpException -> e.message ?: "Erreur HTTP ${e.code}"
        is java.net.SocketTimeoutException ->
            "Le Bruce ne répond pas (${settings.host}). Vérifie l'adresse et que la WebUI tourne."
        is java.net.ConnectException ->
            "Connexion impossible à ${settings.host}. Es-tu bien lié au réseau du Bruce ?"
        else -> e.message ?: e.javaClass.simpleName
    }
}
