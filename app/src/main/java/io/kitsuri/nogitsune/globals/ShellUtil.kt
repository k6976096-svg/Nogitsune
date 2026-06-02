package io.kitsuri.nogitsune.globals

import com.topjohnwu.superuser.Shell

object ShellUtil {
    fun newSh(): Shell =
        Shell.Builder.create()
            .setFlags(Shell.FLAG_NON_ROOT_SHELL)
            .build("sh")
}
