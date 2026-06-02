package io.kitsuri.nogitsune.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.kitsuri.nogitsune.dao.SettingsRepo
import io.kitsuri.nogitsune.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepo(app)
    private val _settings = MutableStateFlow(repo.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        repo.save(next)
    }
}
