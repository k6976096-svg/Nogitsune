package io.kitsuri.nogitsune.globals

import java.io.File

object Paths {
    const val TWOYI_PKG = "io.twoyi"
    val hostRootfs: File get() = File("/data/data/$TWOYI_PKG/rootfs")
    val hostLog: File get() = File("/data/data/$TWOYI_PKG/log.txt")
}
