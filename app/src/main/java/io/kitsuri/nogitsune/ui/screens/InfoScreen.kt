package io.kitsuri.nogitsune.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Info") }) },
    ) { padding ->
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {}
    }
}
