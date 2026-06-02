package io.kitsuri.nogitsune.globals

import java.io.File
import java.nio.file.Files

object FileUtil {
    fun q(s: String) = s.replace("'", "'\\''")

    fun forceRemove(file: File) {
        if (!file.exists()) return
        runCatching {
            when {
                Files.isSymbolicLink(file.toPath()) -> Files.delete(file.toPath())
                file.isDirectory -> file.deleteRecursively()
                else -> file.delete()
            }
        }
    }
}
