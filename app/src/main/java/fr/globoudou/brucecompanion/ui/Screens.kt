package fr.globoudou.brucecompanion.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fr.globoudou.brucecompanion.core.GeoPackage
import java.io.File

// --- Connexion ---------------------------------------------------------------

@Composable
fun ConnectScreen(vm: BruceViewModel, modifier: Modifier = Modifier) {
    val s = vm.settings
    var host by remember { mutableStateOf(s.host) }
    var user by remember { mutableStateOf(s.user) }
    var pwd by remember { mutableStateOf(s.password) }
    var apSsid by remember { mutableStateOf(s.apSsid) }
    var apPass by remember { mutableStateOf(s.apPass) }
    var showAp by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Se connecter au Bruce",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Récupère les fichiers de wardriving via la WebUI et les met en forme pour QField.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = host, onValueChange = { host = it; s.host = it },
            label = { Text("Adresse (IP ou bruce.local)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = user, onValueChange = { user = it; s.user = it },
                label = { Text("Utilisateur") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = pwd, onValueChange = { pwd = it; s.password = it },
                label = { Text("Mot de passe") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        TextButton(onClick = { showAp = !showAp }) {
            Text(if (showAp) "Masquer le point d'accès" else "Réglages du point d'accès")
        }
        if (showAp) {
            OutlinedTextField(
                value = apSsid, onValueChange = { apSsid = it; s.apSsid = it },
                label = { Text("SSID du point d'accès Bruce") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apPass, onValueChange = { apPass = it; s.apPass = it },
                label = { Text("Mot de passe du point d'accès") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = { vm.connect(ConnectMode.AP) },
            enabled = !vm.busy, modifier = Modifier.fillMaxWidth(),
        ) { Text("Se connecter au point d'accès Bruce") }

        OutlinedButton(
            onClick = { vm.connect(ConnectMode.CURRENT) },
            enabled = !vm.busy, modifier = Modifier.fillMaxWidth(),
        ) { Text("Je suis déjà sur le réseau du Bruce") }

        BusyRow(vm)
    }
}

// --- Liste des fichiers ------------------------------------------------------

@Composable
fun FilesScreen(vm: BruceViewModel, modifier: Modifier = Modifier) {
    var showTarget by remember { mutableStateOf(false) }
    val files = vm.csvFiles

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${files.size} fichier(s) · ${vm.selected.size} sélectionné(s)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.selectAll() }) { Text("Tout") }
            TextButton(onClick = { vm.selectNone() }) { Text("Rien") }
        }

        if (files.isEmpty() && !vm.busy) {
            Text(
                "Aucun CSV dans ${vm.settings.folder}. Lance d'abord un wardriving sur le Bruce.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(files, key = { it.path }) { f ->
                val checked = f.path in vm.selected
                Card(
                    onClick = { vm.toggle(f.path) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (checked)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { vm.toggle(f.path) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.name, fontFamily = Mono,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            f.size,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        BusyRow(vm)

        Button(
            onClick = { vm.refreshExisting(); showTarget = true },
            enabled = !vm.busy && vm.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        ) { Text("Traiter ${vm.selected.size} fichier(s)") }
    }

    if (showTarget) {
        TargetDialog(vm, onDismiss = { showTarget = false })
    }
}

// --- Choix de la cible + suppression ----------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetDialog(vm: BruceViewModel, onDismiss: () -> Unit) {
    var newFile by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf(vm.suggestedName()) }
    var chosen by remember { mutableStateOf(vm.existing.firstOrNull()) }
    var deleteAfter by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun launch() {
        val target = if (newFile || chosen == null)
            Target.New(name)
        else
            Target.Existing(chosen!!)
        onDismiss()
        vm.process(target, deleteAfter)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GeoPackage de destination") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = newFile, onClick = { newFile = true })
                    Text("Nouveau fichier", Modifier.weight(1f))
                }
                if (newFile) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Nom") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !newFile,
                        onClick = { newFile = false },
                        enabled = vm.existing.isNotEmpty(),
                    )
                    Text(
                        if (vm.existing.isEmpty()) "Compléter un existant (aucun)"
                        else "Compléter un existant",
                        Modifier.weight(1f),
                        color = if (vm.existing.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (!newFile && vm.existing.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = chosen?.name ?: "",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Fichier") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            vm.existing.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.name) },
                                    onClick = { chosen = f; expanded = false },
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteAfter, onCheckedChange = { deleteAfter = it })
                    Text("Supprimer les fichiers du Bruce ensuite", Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (deleteAfter) confirmDelete = true else launch() }) {
                Text("Lancer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer sur la carte ?") },
            text = {
                Text(
                    "${vm.selected.size} fichier(s) seront définitivement supprimés de la carte SD " +
                        "du Bruce après l'écriture du GeoPackage. Action irréversible."
                )
            },
            confirmButton = {
                Button(onClick = { confirmDelete = false; launch() }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            },
        )
    }
}

// --- Résultat ----------------------------------------------------------------

@Composable
fun ResultScreen(vm: BruceViewModel, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val stats = vm.lastStats
    val file = vm.lastFile

    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Terminé",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (stats != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatLine("Observations", stats.observations.toString())
                    StatLine("Émetteurs estimés", stats.emitters.toString())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {}, label = { Text("${stats.wifi} Wi-Fi") },
                            leadingIcon = { Icon(Icons.Filled.Wifi, null, Modifier.size(18.dp)) },
                        )
                        AssistChip(
                            onClick = {}, label = { Text("${stats.ble} BLE") },
                            leadingIcon = { Icon(Icons.Filled.Bluetooth, null, Modifier.size(18.dp)) },
                        )
                    }
                    if (stats.mobile > 0) {
                        StatLine("Dont mobiles (exclus)", stats.mobile.toString())
                    }
                    if (vm.deletedCount > 0) {
                        StatLine("Supprimés du Bruce", vm.deletedCount.toString())
                    }
                }
            }
        }
        file?.let {
            Text(
                "Fichier : ${it.name}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = Mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { file?.let { shareGpkg(context, it) } },
            enabled = file != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Ouvrir / partager vers QField") }

        OutlinedButton(
            onClick = { vm.backToFiles() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Nouveau traitement") }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
    }
}

@Composable
private fun BusyRow(vm: BruceViewModel) {
    if (vm.busy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text(vm.status ?: "…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// --- Partage -----------------------------------------------------------------

fun shareGpkg(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Ouvrir le GeoPackage avec…"))
}
