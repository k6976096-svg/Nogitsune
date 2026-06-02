package io.kitsuri.nogitsune.dao

import android.app.Application
import android.content.Context
import io.kitsuri.nogitsune.model.AppSettings
import kotlinx.coroutines.runBlocking

class SettingsRepo(context: Context) {

    private val app: Context = when {
        context is Application -> context
        context.applicationContext != null -> context.applicationContext
        else -> context
    }
    private val dao by lazy { AppDatabase.get(app).settings() }

    init {
        migrateLegacyPrefs()
    }

    fun load(): AppSettings = runBlocking {
        val entity = dao.get() ?: SettingsEntity()
        AppSettings(debugRenderer = entity.debugRenderer)
    }

    fun save(settings: AppSettings) {
        runBlocking {
            val cur = dao.get() ?: SettingsEntity()
            dao.save(cur.copy(debugRenderer = settings.debugRenderer))
        }
    }

    private fun migrateLegacyPrefs() {
        val prefs = app.getSharedPreferences("nogitsune_settings", Context.MODE_PRIVATE)
        val raw = prefs.getString("app_settings", null) ?: return
        runBlocking {
            if (dao.get() != null) {
                prefs.edit().remove("app_settings").apply()
                return@runBlocking
            }
            runCatching {
                val legacy = Json.decodeFromString<LegacySettings>(raw)
                dao.save(SettingsEntity(debugRenderer = legacy.debugRenderer))
            }
            prefs.edit().remove("app_settings").apply()
        }
    }

    @kotlinx.serialization.Serializable
    private data class LegacySettings(
        val displayDpi: Int = 320,
        val verboseLogging: Boolean = false,
        val debugRenderer: Boolean = false,
    )

    companion object {
        private val Json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
