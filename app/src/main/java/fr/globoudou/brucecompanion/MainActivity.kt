package fr.globoudou.brucecompanion

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.globoudou.brucecompanion.ui.BruceTheme
import fr.globoudou.brucecompanion.ui.BruceViewModel
import fr.globoudou.brucecompanion.ui.ConnectScreen
import fr.globoudou.brucecompanion.ui.FilesScreen
import fr.globoudou.brucecompanion.ui.Phase
import fr.globoudou.brucecompanion.ui.ResultScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { BruceTheme { App() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val vm: BruceViewModel = viewModel()

    // La connexion à un point d'accès (WifiNetworkSpecifier) est plus fiable avec
    // la localisation accordée ; on la demande une fois, sans bloquer.
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { perm.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    val title = when (vm.phase) {
        Phase.CONNECT -> "Bruce Companion"
        Phase.FILES -> "Fichiers de wardriving"
        Phase.RESULT -> "Résultat"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (vm.phase != Phase.CONNECT) {
                        IconButton(onClick = { vm.disconnect() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Déconnexion")
                        }
                    }
                },
                actions = {
                    if (vm.phase == Phase.FILES) {
                        IconButton(onClick = { vm.loadFiles() }, enabled = !vm.busy) {
                            Icon(Icons.Filled.Refresh, "Rafraîchir")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            when (vm.phase) {
                Phase.CONNECT -> ConnectScreen(vm)
                Phase.FILES -> FilesScreen(vm)
                Phase.RESULT -> ResultScreen(vm)
            }
        }
    }

    vm.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.dismissError() },
            confirmButton = { TextButton(onClick = { vm.dismissError() }) { Text("OK") } },
            title = { Text("Erreur") },
            text = { Text(msg) },
        )
    }
}
