package io.kitsuri.nogitsune.dao

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val debugRenderer: Boolean = false,
    val activeInstanceId: String? = null,
)
