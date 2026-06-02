package io.kitsuri.nogitsune.globals

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.DisplayMetrics
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.util.Locale
import java.util.Properties
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

object BootHelper {

    private const val TAG = "BootHelper"
    private val guestSpawned = AtomicBoolean(false)
    private const val LOADER_FILE = "libloader.so"
    private const val PREF_HOST_FP = "host_build_fingerprint"

    fun clearDalvikCacheIfNeeded(context: Context, rootfs: File) {
        val prefs = context.getSharedPreferences("nogitsune_boot", Context.MODE_PRIVATE)
        val current = Build.FINGERPRINT
        val last = prefs.getString(PREF_HOST_FP, "") ?: ""
        if (current != last) {
            val cache = File(rootfs, "data/dalvik-cache")
            if (cache.exists()) cache.deleteRecursively()
            prefs.edit().putString(PREF_HOST_FP, current).apply()
            Log.i(TAG, "cleared dalvik-cache")
        }
    }

    fun ensureInitExecutable(rootfs: File) {
        val init = File(rootfs, "init")
        if (!init.exists()) return
        init.setExecutable(true, false)
        runCatching { Os.chmod(init.absolutePath, 493) }
        setPermissions(init.absolutePath, 493, -1, -1)
    }

    fun setPermissions(path: String, mode: Int, uid: Int, gid: Int) {
        runCatching {
            val cls = Class.forName("android.os.FileUtils")
            val m = cls.getDeclaredMethod(
                "setPermissions",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            m.isAccessible = true
            m.invoke(null, path, mode, uid, gid)
        }
    }

    fun ensureBootFiles(context: Context, rootfs: File) {
        ensureInitExecutable(rootfs)
        scrubBadGuestPropertyArea(rootfs)
        ensureGuestDevNodes(rootfs)
        stageGuestLoader(context, rootfs)
        ensureRootfsTwoyiPathAliases(rootfs)
        scrubOverriddenPropDefaults(rootfs)
        ensureVendorDefaultProp(context, rootfs)
        ensureTwoyiLegacyCompat(context, rootfs)
        ensureHostGlesBridge(rootfs)
        ensureDataLocalTmp(rootfs)
        File(context.dataDir, "socket").mkdirs()
        createLoaderSymlink(context)
        logRootfsBootHealth(rootfs)
    }

    fun killStaleGuests() {
        guestSpawned.set(false)
        ShellUtil.newSh().newJob()
            .add("ps -ef | awk '{if(\$3==1) print \$2}' | xargs kill -9 2>/dev/null")
            .exec()
    }

    private fun killGuestProcessPids(rootfs: File) {
        val path = runCatching { rootfs.canonicalFile.absolutePath }.getOrDefault(rootfs.absolutePath)
        ShellUtil.newSh().newJob()
            .add("pkill -9 -f '${FileUtil.q(path)}' 2>/dev/null || true")
            .add("ps -ef | awk '{if(\$3==1) print \$2}' | xargs kill -9 2>/dev/null || true")
            .exec()
    }

    fun killGuestProcesses(rootfs: File) {
        guestSpawned.set(false)
        killGuestProcessPids(rootfs)
    }

    fun scrubBadGuestPropertyArea(rootfs: File) {
        val rf = runCatching { rootfs.canonicalFile }.getOrDefault(rootfs)
        val props = File(rf, "dev/__properties__")
        if (!props.exists()) return
        val remove = when {
            props.isDirectory -> true
            props.isFile && props.length() < 4096L -> true
            else -> false
        }
        if (!remove) return
        val wasDir = props.isDirectory
        val size = props.length()
        val path = props.absolutePath
        FileUtil.forceRemove(props)
        ShellUtil.newSh().newJob().add("rm -rf '${FileUtil.q(path)}'").exec()
        Log.i(TAG, "removed bad guest dev/__properties__ wasDir=$wasDir size=$size")
    }

    fun stageGuestLoader(context: Context, rootfs: File): String {
        val rf = runCatching { rootfs.canonicalFile }.getOrDefault(rootfs)
        val info = context.applicationInfo
        val src = File(info.nativeLibraryDir, LOADER_FILE)
        if (!src.isFile) error("missing libloader at ${src.absolutePath}")
        if (src.length() < 20_000L) {
            Log.e(TAG, "staged loader is stub (${src.length()}b); guest boot needs Twoyi libloader.so in APK")
        } else {
            Log.i(TAG, "staged proprietary-class loader size=${src.length()}")
        }
        val dst = File(rf, "loader64")
        if (!dst.isFile || dst.length() != src.length()) {
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            dst.setExecutable(true, false)
            runCatching { Os.chmod(dst.absolutePath, 493) }
            setPermissions(dst.absolutePath, 493, -1, -1)
        }
        ensureGuestLoaderAliases(rf, dst)
        Log.i(TAG, "staged guest loader size=${dst.length()} path=${dst.absolutePath}")
        return dst.absolutePath
    }

    private fun ensureGuestLoaderAliases(rootfs: File, loader: File) {
        val rf = runCatching { rootfs.canonicalFile }.getOrDefault(rootfs)
        val dir = File(rf, "data/data/${Paths.TWOYI_PKG}")
        dir.mkdirs()
        val link = File(dir, "loader64")
        FileUtil.forceRemove(link)
        runCatching {
            Files.createSymbolicLink(link.toPath(), loader.toPath())
        }.onFailure {
            ShellUtil.newSh().newJob()
                .add("ln -sf '${FileUtil.q(loader.absolutePath)}' '${FileUtil.q(link.absolutePath)}'")
                .exec()
        }
    }

    fun spawnInitTwoyiStyle(context: Context, rootfs: File, log: File) {
        if (!guestSpawned.compareAndSet(false, true)) {
            Log.i(TAG, "spawn init skipped (already running)")
            return
        }
        val rf = runCatching { rootfs.canonicalFile }.getOrDefault(rootfs)
        val cwd = if (Files.isSymbolicLink(rootfs.toPath())) rootfs else rf
        val init = File(rf, "init")
        if (!init.exists()) error("missing init in ${rf.absolutePath}")
        log.parentFile?.mkdirs()
        log.writeText("--- nogitsune boot ${System.currentTimeMillis()} ---\n")
        killGuestProcessPids(rf)
        scrubBadGuestPropertyArea(rf)
        ensureInitExecutable(rf)
        val loader = stageGuestLoader(context, rf)
        val tyloader = File(context.applicationInfo.nativeLibraryDir, LOADER_FILE).absolutePath
        log.appendText("TYLOADER=$tyloader\nloader64=$loader\nrootfs=${rf.absolutePath}\ncwd=${cwd.absolutePath}\n")
        val pb = ProcessBuilder("./init")
        pb.directory(cwd)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
            .redirectError(ProcessBuilder.Redirect.appendTo(log))
        pb.environment()["TYLOADER"] = tyloader
        pb.start()
        Log.i(TAG, "spawn init cwd=${cwd.absolutePath} tyloader=$tyloader loader64=$loader")
    }

    fun ensureGuestDevNodes(rootfs: File) {
        val devDir = File(rootfs, "dev")
        File(devDir, "input").mkdirs()
        File(devDir, "socket").mkdirs()
        File(devDir, "maps").mkdirs()
    }

    private fun scrubOverriddenPropDefaults(rootfs: File) {
        val rf = runCatching { rootfs.canonicalFile }.getOrDefault(rootfs)
        for (rel in listOf("prop.default", "system/etc/prop.default")) {
            FileUtil.forceRemove(File(rf, rel))
        }
    }

    fun ensureVendorDefaultProp(context: Context, rootfs: File) {
        val rf = runCatching { rootfs.canonicalFile }.getOrDefault(rootfs)
        val propFile = File(rf, "vendor/default.prop")
        propFile.parentFile?.mkdirs()
        val props = Properties()
        if (propFile.isFile) {
            runCatching { propFile.inputStream().use { props.load(it) } }
        }
        props.setProperty("persist.sys.language", Locale.getDefault().language)
        props.setProperty("persist.sys.country", Locale.getDefault().country)
        props.setProperty("persist.sys.timezone", TimeZone.getDefault().id)
        props.setProperty("ro.sf.lcd_density", DisplayMetrics.DENSITY_DEVICE_STABLE.toString())
        props.setProperty("ro.zygote", "zygote64")
        props.setProperty("ro.hardware", "goldfish")
        FileWriter(propFile).use { props.store(it, null) }
    }

    fun ensureRootfsTwoyiPathAliases(rootfs: File) {
        val rf = runCatching { rootfs.canonicalFile }.getOrDefault(rootfs)
        val guestRoot = File(rf, "data/data/${Paths.TWOYI_PKG}/rootfs")
        guestRoot.parentFile?.mkdirs()
        FileUtil.forceRemove(guestRoot)
        runCatching {
            Files.createSymbolicLink(guestRoot.toPath(), rf.toPath())
        }.onFailure {
            val cmd = "mkdir -p '${FileUtil.q(guestRoot.parent!!)}' && ln -sf '${FileUtil.q(rf.absolutePath)}' '${FileUtil.q(guestRoot.absolutePath)}'"
            ShellUtil.newSh().newJob().add(cmd).exec()
        }
    }

    fun spawnCwdRootfs(rootfsLink: File): File {
        val host = Paths.hostRootfs
        if (host.exists()) return host
        return if (Files.isSymbolicLink(rootfsLink.toPath())) {
            rootfsLink
        } else {
            runCatching { rootfsLink.canonicalFile }.getOrDefault(rootfsLink)
        }
    }

    fun ensureHostGlesBridge(rootfs: File) {
        val rf = runCatching { rootfs.canonicalFile }.getOrDefault(rootfs)
        val hostRoot = Paths.hostRootfs
        val hostCanon = runCatching { hostRoot.canonicalFile }.getOrDefault(hostRoot)
        val sameTree = hostCanon == rf
        val links = listOf("opengles", "opengles2", "opengles3")
        var ok = 0
        rf.mkdirs()
        hostCanon.mkdirs()
        for (name in links) {
            val guestPath = File(rf, name)
            val hostPath = if (sameTree) guestPath else File(hostCanon, name)
            FileUtil.forceRemove(guestPath)
            if (!sameTree) FileUtil.forceRemove(hostPath)
            val parentReady = guestPath.parentFile?.isDirectory == true
            if (parentReady) ok++
        }
        Log.i(
            TAG,
            "gles bridge ok=$ok/3 sockets=files sameTree=$sameTree host=${hostCanon.absolutePath} rootfs=${rf.absolutePath}",
        )
    }

    private fun logRootfsBootHealth(rootfs: File) {
        val rf = runCatching { rootfs.canonicalFile }.getOrDefault(rootfs)
        val props = File(rf, "dev/__properties__")
        Log.i(
            TAG,
            "rootfs health init=${File(rf, "init").exists()} " +
                "props=${props.exists()} propsDir=${props.isDirectory}",
        )
    }

    fun ensureTwoyiLegacyCompat(context: Context, rootfs: File): Boolean {
        val target = runCatching { rootfs.canonicalFile }.getOrDefault(rootfs)
        val hostRoot = Paths.hostRootfs
        hostRoot.parentFile?.mkdirs()
        FileUtil.forceRemove(hostRoot)
        val linked = runCatching {
            Files.createSymbolicLink(hostRoot.toPath(), target.toPath())
        }.isSuccess
        Log.i(TAG, "host rootfs link ok=$linked host=${hostRoot.absolutePath} target=${target.absolutePath}")
        return linked && File(hostRoot, "init").exists()
    }

    private fun ensureDataLocalTmp(rootfs: File) {
        val path = File(rootfs, "data/local/tmp").absolutePath
        val result = ShellUtil.newSh().newJob()
            .add("mkdir -p '${FileUtil.q(path)}'")
            .add("chmod 777 '${FileUtil.q(path)}'")
            .exec()
        if (!result.isSuccess) {
            Log.w(TAG, "ensureDataLocalTmp failed: ${result.err}")
        }
    }

    private fun createLoaderSymlink(context: Context) {
        runCatching {
            val link = File(context.dataDir, "loader64").toPath()
            val target = File(context.applicationInfo.nativeLibraryDir, LOADER_FILE).toPath()
            Files.deleteIfExists(link)
            Files.createSymbolicLink(link, target)
        }.onFailure {
            Log.w(TAG, "loader64 symlink failed", it)
        }
    }

    fun initRootfsVendor(context: Context, rootfs: File) {
        ensureVendorDefaultProp(context, rootfs)
        ensureBootFiles(context, rootfs)
    }
}
