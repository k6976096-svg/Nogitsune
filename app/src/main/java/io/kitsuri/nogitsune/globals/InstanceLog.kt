package io.kitsuri.nogitsune.globals

import android.content.Context
import android.util.Log
import io.kitsuri.nogitsune.dao.InstanceEntity
import io.kitsuri.nogitsune.dao.InstanceRepo
import java.io.File

object InstanceLog {

    private const val TAG = "InstanceLog"

    fun exportDir(context: Context): File {
        val dir = context.getExternalFilesDir("logs") ?: File(context.filesDir, "logs")
        dir.mkdirs()
        return dir
    }

    fun export(context: Context, instance: InstanceEntity): File {
        val repo = InstanceRepo(context)
        val hostLog = Paths.hostLog
        val src = when {
            hostLog.exists() && hostLog.length() > 0 -> hostLog
            else -> repo.logFile(instance.id)
        }
        val safeName = instance.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dst = File(exportDir(context), "${safeName}-${instance.id.take(8)}.log.txt")
        if (src.exists() && src.length() > 0) {
            src.copyTo(dst, overwrite = true)
        } else {
            dst.writeText("(empty — init may not have started)\nrootfs=${repo.rootfsDir(instance.id)}\n")
        }
        Log.i(TAG, "exported ${dst.absolutePath} (${dst.length()} bytes)")
        return dst
    }
}
