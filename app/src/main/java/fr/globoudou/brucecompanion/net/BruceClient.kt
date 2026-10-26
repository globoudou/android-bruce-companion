package fr.globoudou.brucecompanion.net

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client HTTP de la WebUI de Bruce. Routes (firmware core/wifi/webInterface.cpp) :
 *   POST /login              username=&password=  → cookie BRUCESESSION
 *   GET  /listfiles?fs=&folder=                    → lignes pa:/Fo:/Fi:
 *   GET  /file?fs=&name=&action=download           → octets du fichier
 *   GET  /file?fs=&name=&action=delete
 *
 * HttpURLConnection sans dépendance externe. Toutes les méthodes sont bloquantes
 * (à appeler depuis un contexte IO).
 */
class BruceClient(
    private val host: String,
    private val timeoutMs: Int = 8000,
) {
    @Volatile private var cookie: String? = null

    class HttpException(val code: Int, msg: String) : Exception(msg)

    data class RemoteFile(val name: String, val path: String, val isDir: Boolean, val size: String)

    private fun base() = if (host.startsWith("http")) host.trimEnd('/') else "http://${host.trimEnd('/')}"

    private fun open(path: String, method: String): HttpURLConnection {
        val c = URL(base() + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = false
        cookie?.let { c.setRequestProperty("Cookie", "BRUCESESSION=$it") }
        return c
    }

    /** S'authentifie et mémorise le cookie de session. Lève en cas d'échec. */
    fun login(user: String, password: String) {
        val c = open("/login", "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = "username=${enc(user)}&password=${enc(password)}"
        c.outputStream.use { it.write(body.toByteArray()) }

        val code = c.responseCode
        // Succès comme échec renvoient un 302 ; seul le cookie distingue.
        val setCookie = c.headerFields["Set-Cookie"]?.firstOrNull { it.contains("BRUCESESSION=") }
        c.disconnect()

        val token = setCookie
            ?.substringAfter("BRUCESESSION=")
            ?.substringBefore(';')
            ?.takeIf { it.isNotBlank() && it != "0" }
            ?: throw HttpException(code, "Identifiants refusés")
        cookie = token
    }

    val isLoggedIn: Boolean get() = cookie != null

    /** Liste un dossier. `fs` vaut "SD" ou "LittleFS". */
    fun listFiles(folder: String, fs: String = "SD"): List<RemoteFile> {
        val c = open("/listfiles?fs=${enc(fs)}&folder=${enc(folder)}", "GET")
        val text = readText(c)
        val out = ArrayList<RemoteFile>()
        val root = folder.trimEnd('/')
        for (line in text.split('\n')) {
            val l = line.trim()
            if (l.isEmpty()) continue
            when {
                l.startsWith("Fo:") -> {
                    val name = l.removePrefix("Fo:").substringBeforeLast(':')
                    out.add(RemoteFile(name, "$root/$name", true, ""))
                }
                l.startsWith("Fi:") -> {
                    val rest = l.removePrefix("Fi:")
                    val size = rest.substringAfterLast(':')
                    val name = rest.substring(0, rest.length - size.length - 1)
                    out.add(RemoteFile(name, "$root/$name", false, size))
                }
                // "pa:" = dossier courant, ignoré.
            }
        }
        return out
    }

    /** Télécharge un fichier et renvoie son contenu. */
    fun download(path: String, fs: String = "SD"): ByteArray {
        val c = open("/file?fs=${enc(fs)}&action=download&name=${enc(path)}", "GET")
        if (c.responseCode != 200) {
            val m = runCatching { readText(c) }.getOrDefault("")
            throw HttpException(c.responseCode, m.ifBlank { "Téléchargement impossible" })
        }
        val out = ByteArrayOutputStream()
        c.inputStream.use { it.copyTo(out) }
        c.disconnect()
        return out.toByteArray()
    }

    /** Supprime un fichier sur la carte. */
    fun delete(path: String, fs: String = "SD") {
        val c = open("/file?fs=${enc(fs)}&action=delete&name=${enc(path)}", "GET")
        val resp = readText(c)
        if (!resp.startsWith("Deleted")) {
            throw HttpException(c.responseCode, resp.ifBlank { "Suppression impossible" })
        }
    }

    private fun readText(c: HttpURLConnection): String {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        c.disconnect()
        if (code == 401) throw HttpException(401, "Session expirée")
        return text
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
