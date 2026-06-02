package io.kitsuri.nogitsune.dao

import android.app.Application
import android.content.Context
import io.kitsuri.nogitsune.globals.FileUtil
import io.kitsuri.nogitsune.globals.ShellUtil
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.util.UUID

class InstanceRepo(context: Context) {

    private val app: Context = when {
        context is Application -> context
        context.applicationContext != null -> context.applicationContext
        else -> context
    }
    private val db by lazy { AppDatabase.get(app) }
    private val instancesDir = File(app.dataDir, "instances")
    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (app is Application) {
            migrateJsonIndex()
            migratePrefsActive()
        }
    }

    fun list(): List<InstanceEntity> = runBlocking { db.instances().all() }

    fun get(id: String): InstanceEntity? = runBlocking { db.instances().get(id) }

    fun create(name: String, displayWidth: Int, displayHeight: Int, displayDpi: Int): InstanceEntity {
        val entity = InstanceEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            displayDpi = displayDpi,
        )
        runBlocking {
            db.instances().insert(entity)
        }
        instanceDir(entity.id).mkdirs()
        rootfsDir(entity.id).mkdirs()
        return entity
    }

    fun delete(id: String) {
        instanceDir(id).deleteRecursively()
        runBlocking { db.instances().delete(id) }
        if (activeId() == id) {
            setActive(null)
            FileUtil.forceRemove(activeRootfsLink())
        }
    }

    fun instanceDir(id: String) = File(instancesDir, id)

    fun rootfsDir(id: String) = File(instanceDir(id), "rootfs")

    fun logFile(id: String) = File(instanceDir(id), "log.txt")

    fun activeRootfsLink() = File(app.dataDir, "rootfs")

    fun activeId(): String? = runBlocking {
        db.settings().get()?.activeInstanceId
    }

    fun setActive(instanceId: String?) {
        runBlocking {
            val cur = db.settings().get() ?: SettingsEntity()
            db.settings().save(cur.copy(activeInstanceId = instanceId))
        }
    }

    fun updateActiveSymlink(instanceId: String) {
        setActive(instanceId)
        val target = rootfsDir(instanceId).absoluteFile.also { it.mkdirs() }
        val link = activeRootfsLink()
        if (link.absoluteFile == target) return

        FileUtil.forceRemove(link)
        if (link.exists()) return

        val linked = runCatching {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        }.isSuccess
        if (!linked && !link.exists()) {
            val cmd = "ln -sf '${FileUtil.q(target.absolutePath)}' '${FileUtil.q(link.absolutePath)}'"
            ShellUtil.newSh().newJob().add(cmd).exec()
        }
    }

    fun hasRootfs(id: String): Boolean {
        val rootfs = rootfsDir(id)
        return rootfs.exists() && rootfs.list()?.any { it == "init" } == true
    }

    private fun migrateJsonIndex() {
        val indexFile = File(instancesDir, "index.json")
        if (!indexFile.exists()) return
        runBlocking {
            val existing = db.instances().all()
            if (existing.isNotEmpty()) {
                indexFile.delete()
                return@runBlocking
            }
            runCatching {
                json.decodeFromString<List<LegacyInstance>>(indexFile.readText())
            }.getOrNull()?.forEach { legacy ->
                db.instances().insert(
                    InstanceEntity(
                        id = legacy.id,
                        name = legacy.name,
                        displayWidth = legacy.displayWidth,
                        displayHeight = legacy.displayHeight,
                        displayDpi = legacy.displayDpi,
                        createdAt = legacy.createdAt,
                    ),
                )
            }
            indexFile.delete()
        }
    }

    private fun migratePrefsActive() {
        val prefs = app.getSharedPreferences("nogitsune_instances", Context.MODE_PRIVATE)
        val legacyId = prefs.getString("active_instance_id", null) ?: return
        if (activeId() != null) {
            prefs.edit().remove("active_instance_id").apply()
            return
        }
        setActive(legacyId)
        prefs.edit().remove("active_instance_id").apply()
    }

    @kotlinx.serialization.Serializable
    private data class LegacyInstance(
        val id: String,
        val name: String,
        val displayWidth: Int,
        val displayHeight: Int,
        val displayDpi: Int = 320,
        val createdAt: Long = System.currentTimeMillis(),
    )
}
