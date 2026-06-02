package io.kitsuri.nogitsune.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.kitsuri.nogitsune.globals.DeviceDisplay

@Composable
fun CreateInstanceDialog(
    device: DeviceDisplay,
    onDismiss: () -> Unit,
    onConfirm: (name: String, width: Int, height: Int, dpi: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf(device.widthPx.toString()) }
    var height by remember { mutableStateOf(device.heightPx.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New instance") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it.filter { c -> c.isDigit() } },
                    label = { Text("Display width") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it.filter { c -> c.isDigit() } },
                    label = { Text("Display height") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = width.toIntOrNull() ?: device.widthPx
                    val h = height.toIntOrNull() ?: device.heightPx
                    if (name.isNotBlank()) onConfirm(name.trim(), w, h, device.densityDpi)
                },
                enabled = name.isNotBlank(),
            ) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
