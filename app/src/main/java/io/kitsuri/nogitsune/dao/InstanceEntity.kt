package io.kitsuri.nogitsune.dao

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "instances")
data class InstanceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val displayWidth: Int,
    val displayHeight: Int,
    val displayDpi: Int = 320,
    val createdAt: Long = System.currentTimeMillis(),
)
