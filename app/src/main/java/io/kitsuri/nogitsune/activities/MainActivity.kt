package io.kitsuri.nogitsune.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.kitsuri.nogitsune.ui.pages.MainShell
import io.kitsuri.nogitsune.ui.themes.NogitsuneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NogitsuneTheme {
                MainShell()
            }
        }
    }
}
