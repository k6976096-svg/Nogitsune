package io.kitsuri.nogitsune.globals

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object RootfsExtractor {

    suspend fun extractTar(context: Context, uri: Uri, targetRootfs: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                targetRootfs.mkdirs()
                if (targetRootfs.exists()) {
                    targetRootfs.deleteRecursively()
                    targetRootfs.mkdirs()
                }
                val temp = File(context.cacheDir, "rootfs_import.tar")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output)
                    }
                } ?: error("cannot open uri")

                val tempPath = temp.absolutePath
                val rootfsPath = targetRootfs.absolutePath
                if (";" in tempPath || "&" in tempPath || ";" in rootfsPath || "&" in rootfsPath) {
                    error("invalid path")
                }

                val process = ProcessBuilder("tar", "-xf", tempPath, "-C", rootfsPath).start()
                val code = process.waitFor()
                temp.delete()
                if (code != 0) error("tar exit $code")
                BootHelper.ensureInitExecutable(targetRootfs)
                BootHelper.initRootfsVendor(context, targetRootfs)
            }
        }
}
