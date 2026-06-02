package io.kitsuri.nogitsune.dao

import android.app.Application
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [InstanceEntity::class, SettingsEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun instances(): InstanceDao
    abstract fun settings(): SettingsDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            val app = when {
                context is Application -> context
                context.applicationContext != null -> context.applicationContext
                else -> context
            }
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    app,
                    AppDatabase::class.java,
                    "nogitsune.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
        }
    }
}
