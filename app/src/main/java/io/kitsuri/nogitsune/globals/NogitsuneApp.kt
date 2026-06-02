package io.kitsuri.nogitsune.globals

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.topjohnwu.superuser.Shell
import io.kitsuri.nogitsune.dao.InstanceRepo
import java.io.File

class NogitsuneApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val rootfs = bootRootfs(base)
        if (rootfs.exists()) {
            BootHelper.ensureBootFiles(base, rootfs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        InstanceRepo(this)
        Shell.getShell()
        NogitsuneSocketServer.start()
        Handler(Looper.getMainLooper()).postDelayed({ NogitsuneMessenger.ping() }, 3000)
    }

    private fun bootRootfs(context: Context): File {
        val link = File(context.dataDir, "rootfs")
        if (link.exists()) return link
        val legacyId = context.getSharedPreferences("nogitsune_instances", MODE_PRIVATE)
            .getString("active_instance_id", null)
        if (legacyId != null) {
            val dir = File(context.dataDir, "instances/$legacyId/rootfs")
            if (dir.exists()) return dir
        }
        return link
    }
}
