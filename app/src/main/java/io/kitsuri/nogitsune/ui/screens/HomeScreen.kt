package io.kitsuri.nogitsune.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kitsuri.nogitsune.activities.VmActivity
import io.kitsuri.nogitsune.dao.InstanceRepo
import io.kitsuri.nogitsune.globals.deviceDisplay
import io.kitsuri.nogitsune.ui.components.AddInstanceCard
import io.kitsuri.nogitsune.ui.components.InstanceCard
import io.kitsuri.nogitsune.ui.dialogs.CreateInstanceDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val device = context.deviceDisplay()

    val pickRootfs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importRootfs(uri)
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            Toast.makeText(context, "Import ${uris.size} file(s) — wire to guest", Toast.LENGTH_SHORT).show()
        }
    }

    val pickApk = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Install APK in guest — wire to installer", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Instances") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            AddInstanceCard(onClick = { viewModel.showCreateDialog() })

            state.instances.forEach { instance ->
                InstanceCard(
                    instance = instance,
                    ready = viewModel.hasRootfs(instance.id),
                    onLaunch = {
                        if (!viewModel.hasRootfs(instance.id)) {
                            Toast.makeText(context, "Import a rootfs first", Toast.LENGTH_SHORT).show()
                            return@InstanceCard
                        }
                        InstanceRepo(context).updateActiveSymlink(instance.id)
                        context.startActivity(
                            Intent(context, VmActivity::class.java).putExtra(VmActivity.EXTRA_ID, instance.id),
                        )
                    },
                    onImportFiles = { pickFiles.launch(arrayOf("*/*")) },
                    onImportApps = { pickApk.launch(arrayOf("application/vnd.android.package-archive")) },
                    onReboot = {
                        context.startActivity(
                            Intent(context, VmActivity::class.java)
                                .putExtra(VmActivity.EXTRA_ID, instance.id)
                                .putExtra(VmActivity.EXTRA_REBOOT, true),
                        )
                    },
                    onDelete = { viewModel.deleteInstance(instance.id) },
                )
            }
        }
    }

    if (state.showCreateDialog) {
        CreateInstanceDialog(
            device = device,
            onDismiss = { viewModel.dismissCreateDialog() },
            onConfirm = { name, w, h, dpi ->
                viewModel.createInstance(name, w, h, dpi)
                pickRootfs.launch(arrayOf("application/x-tar", "application/octet-stream", "*/*"))
            },
        )
    }

    if (state.unpacking) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Unpacking rootfs") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("This may take a few minutes", Modifier.padding(top = 16.dp))
                }
            },
            confirmButton = {},
        )
    }

    state.unpackError?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearUnpackError() },
            title = { Text("Import failed") },
            text = { Text(msg) },
            confirmButton = {},
        )
    }
}
