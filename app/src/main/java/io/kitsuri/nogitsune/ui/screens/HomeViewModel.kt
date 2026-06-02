package io.kitsuri.nogitsune.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.kitsuri.nogitsune.dao.InstanceRepo
import io.kitsuri.nogitsune.globals.RootfsExtractor
import io.kitsuri.nogitsune.model.HomeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = InstanceRepo(app)
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val instances = repo.list()
        val ready = instances.associate { it.id to repo.hasRootfs(it.id) }
        _state.update { it.copy(instances = instances, rootfsReady = ready, unpackError = null) }
    }

    fun clearUnpackError() {
        _state.update { it.copy(unpackError = null) }
    }

    fun showCreateDialog() {
        _state.update { it.copy(showCreateDialog = true) }
    }

    fun dismissCreateDialog() {
        _state.update { it.copy(showCreateDialog = false) }
    }

    fun createInstance(name: String, width: Int, height: Int, dpi: Int) {
        val instance = repo.create(name, width, height, dpi)
        _state.update { it.copy(showCreateDialog = false, pendingInstance = instance) }
    }

    fun importRootfs(uri: Uri) {
        val instance = _state.value.pendingInstance ?: return
        viewModelScope.launch {
            _state.update { it.copy(unpacking = true, unpackError = null) }
            val result = RootfsExtractor.extractTar(getApplication(), uri, repo.rootfsDir(instance.id))
            result.fold(
                onSuccess = {
                    repo.updateActiveSymlink(instance.id)
                    refresh()
                    _state.update { it.copy(unpacking = false, pendingInstance = null) }
                },
                onFailure = { e ->
                    repo.delete(instance.id)
                    refresh()
                    _state.update {
                        it.copy(
                            unpacking = false,
                            unpackError = e.message,
                            pendingInstance = null,
                        )
                    }
                },
            )
        }
    }

    fun deleteInstance(id: String) {
        repo.delete(id)
        refresh()
    }

    fun hasRootfs(id: String): Boolean = _state.value.rootfsReady[id] == true
}
