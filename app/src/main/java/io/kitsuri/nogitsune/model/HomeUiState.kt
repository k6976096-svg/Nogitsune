package io.kitsuri.nogitsune.model

import io.kitsuri.nogitsune.dao.InstanceEntity

data class HomeUiState(
    val instances: List<InstanceEntity> = emptyList(),
    val rootfsReady: Map<String, Boolean> = emptyMap(),
    val unpacking: Boolean = false,
    val unpackError: String? = null,
    val pendingInstance: InstanceEntity? = null,
    val showCreateDialog: Boolean = false,
)
