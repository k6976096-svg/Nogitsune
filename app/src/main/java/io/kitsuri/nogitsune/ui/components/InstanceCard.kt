package io.kitsuri.nogitsune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kitsuri.nogitsune.dao.InstanceEntity

@Composable
fun InstanceCard(
    instance: InstanceEntity,
    ready: Boolean,
    onLaunch: () -> Unit,
    onImportFiles: () -> Unit,
    onImportApps: () -> Unit,
    onReboot: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(instance.name)
                Text("${instance.displayWidth}×${instance.displayHeight} @ ${instance.displayDpi} dpi")
                if (!ready) Text("No rootfs")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Import files") },
                        onClick = { menuOpen = false; onImportFiles() },
                    )
                    DropdownMenuItem(
                        text = { Text("Import apps") },
                        onClick = { menuOpen = false; onImportApps() },
                    )
                    DropdownMenuItem(
                        text = { Text("Reboot VM") },
                        onClick = { menuOpen = false; onReboot() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete instance") },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
                FloatingActionButton(
                    onClick = onLaunch,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Launch")
                }
            }
        }
    }
}
