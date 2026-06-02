package io.kitsuri.nogitsune.activities

import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.kitsuri.nogitsune.dao.InstanceEntity
import io.kitsuri.nogitsune.dao.InstanceRepo
import io.kitsuri.nogitsune.dao.SettingsRepo
import io.kitsuri.nogitsune.globals.BootHelper
import io.kitsuri.nogitsune.globals.BootStatus
import io.kitsuri.nogitsune.globals.InstanceLog
import io.kitsuri.nogitsune.globals.Paths
import io.kitsuri.nogitsune.globals.Renderer
import io.kitsuri.nogitsune.ui.components.BootLogTexture
import java.io.File
import java.util.concurrent.TimeUnit

class VmActivity : ComponentActivity(), View.OnTouchListener {

    private lateinit var instance: InstanceEntity
    private lateinit var repo: InstanceRepo
    private var surfaceView: SurfaceView? = null
    private var surfaceW = 0
    private var surfaceH = 0
    private var rendererStarted = false
    private val repaintHandler = Handler(Looper.getMainLooper())
    private var repaintRunning = false
    private var bootLogView: BootLogTexture? = null
    private var bootOverlay: FrameLayout? = null

    private val callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {}

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            surfaceW = width
            surfaceH = height
            if (rendererStarted) {
                Renderer.resetWindow(
                    holder.surface, 0, 0, width, height,
                    instance.displayWidth, instance.displayHeight,
                )
                return
            }
            rendererStarted = true
            startRenderer(holder)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            rendererStarted = false
            stopRepaintLoop()
            BootHelper.killGuestProcesses(spawnRootfs(instance.id))
            Renderer.resetRuntime()
            Renderer.shutdown()
            Renderer.removeWindow(holder.surface)
        }
    }

    private fun startRenderer(holder: SurfaceHolder) {
        val settings = SettingsRepo(this).load()
        Renderer.setDebugRenderer(if (settings.debugRenderer) 1 else 0)

        val wm = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val scaleX = metrics.widthPixels.toFloat() / instance.displayWidth
        val scaleY = metrics.heightPixels.toFloat() / instance.displayHeight
        val xdpi = metrics.xdpi * scaleX * instance.displayDpi / metrics.densityDpi
        val ydpi = metrics.ydpi * scaleY * instance.displayDpi / metrics.densityDpi

        val rootfsLink = spawnRootfs(instance.id)
        val rootfs = runCatching { rootfsLink.canonicalFile }.getOrDefault(rootfsLink)
        val log = Paths.hostLog
        repo.logFile(instance.id).parentFile?.mkdirs()

        BootStatus.reset()

        val legacyOk = BootHelper.ensureTwoyiLegacyCompat(this, rootfs)
        BootHelper.ensureHostGlesBridge(rootfs)
        val loader = File(applicationInfo.nativeLibraryDir, "libloader.so").absolutePath
        Log.i(
            TAG,
            "rootfs=${rootfsLink.absolutePath} init=${File(rootfs, "init").exists()} " +
                "loader=${File(loader).exists()} legacy_compat=$legacyOk",
        )

        Renderer.nativeSetPaths(Paths.hostRootfs.absolutePath, log.absolutePath, applicationInfo.nativeLibraryDir)
        Renderer.init(
            holder.surface,
            loader,
            instance.displayWidth,
            instance.displayHeight,
            xdpi,
            ydpi,
            60,
        )
        Renderer.repaint()
        Renderer.resetWindow(
            holder.surface, 0, 0, surfaceW, surfaceH,
            instance.displayWidth, instance.displayHeight,
        )
        val spawnCwd = BootHelper.spawnCwdRootfs(rootfsLink)
        Thread({
            Thread.sleep(400)
            runCatching { BootHelper.spawnInitTwoyiStyle(this@VmActivity, spawnCwd, log) }
                .onFailure { Log.e(TAG, "spawn init failed", it) }
            val booted = BootStatus.waitBoot(60, TimeUnit.SECONDS)
            runOnUiThread {
                if (booted) {
                    Log.i(TAG, "guest boot completed")
                    hideBootLogOverlay()
                } else {
                    Log.w(TAG, "guest boot timed out — repaint loop")
                    BootHelper.killGuestProcesses(spawnRootfs(instance.id))
                    Toast.makeText(this, "Guest still booting (see boot log)", Toast.LENGTH_SHORT).show()
                    startRepaintLoop(holder)
                }
                scheduleLogExport()
            }
        }, "guest-boot").start()
    }

    private fun showBootLogOverlay(logFile: File) {
        bootLogView?.visibility = View.VISIBLE
        bootOverlay?.visibility = View.VISIBLE
        bootLogView?.startLogTail(logFile)
    }

    private fun hideBootLogOverlay() {
        bootLogView?.stopLogTail()
        bootLogView?.visibility = View.GONE
        bootOverlay?.visibility = View.GONE
    }

    private fun startRepaintLoop(holder: SurfaceHolder) {
        if (repaintRunning) return
        repaintRunning = true
        val tick = object : Runnable {
            override fun run() {
                if (!repaintRunning || !rendererStarted) return
                Renderer.repaint()
                if (!BootStatus.isBooted()) {
                    repaintHandler.postDelayed(this, 2000)
                } else {
                    repaintRunning = false
                    Renderer.resetWindow(
                        holder.surface, 0, 0, surfaceW, surfaceH,
                        instance.displayWidth, instance.displayHeight,
                    )
                }
            }
        }
        repaintHandler.postDelayed(tick, 2000)
    }

    private fun stopRepaintLoop() {
        repaintRunning = false
        repaintHandler.removeCallbacksAndMessages(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        repo = InstanceRepo(this)
        val id = intent.getStringExtra(EXTRA_ID) ?: run {
            finish()
            return
        }
        instance = repo.get(id) ?: run {
            finish()
            return
        }

        if (!repo.hasRootfs(id)) {
            Toast.makeText(this, "No rootfs for this instance", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        repo.updateActiveSymlink(id)
        val rootfs = runCatching { spawnRootfs(id).canonicalFile }.getOrDefault(spawnRootfs(id))
        BootHelper.clearDalvikCacheIfNeeded(this, rootfs)
        if (!File(rootfs, "vendor/default.prop").exists()) {
            BootHelper.initRootfsVendor(this, rootfs)
        } else {
            BootHelper.ensureBootFiles(this, rootfs)
        }

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        val sv = SurfaceView(this).apply {
            holder.addCallback(callback)
            setOnTouchListener(this@VmActivity)
        }
        surfaceView = sv
        layoutSurface(sv)
        root.addView(sv)

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt())
            visibility = View.VISIBLE
        }
        val bootLog = BootLogTexture(this).apply {
            visibility = View.VISIBLE
        }
        bootLogView = bootLog
        bootOverlay = overlay
        overlay.addView(
            bootLog,
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        root.addView(overlay, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        setContentView(root)
        showBootLogOverlay(Paths.hostLog)

        if (intent.getBooleanExtra(EXTRA_REBOOT, false)) {
            BootHelper.killStaleGuests()
            Toast.makeText(this, "Rebooting VM", Toast.LENGTH_SHORT).show()
        }
    }

    private fun layoutSurface(sv: SurfaceView) {
        val wm = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val sw = metrics.widthPixels
        val sh = metrics.heightPixels
        val virtualAspect = instance.displayWidth.toFloat() / instance.displayHeight
        val screenAspect = sw.toFloat() / sh
        val (w, h) = if (virtualAspect > screenAspect) {
            sw to (sw / virtualAspect).toInt()
        } else {
            (sh * virtualAspect).toInt() to sh
        }
        surfaceW = w
        surfaceH = h
        sv.layoutParams = FrameLayout.LayoutParams(w, h).apply {
            gravity = android.view.Gravity.CENTER
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (surfaceW <= 0 || surfaceH <= 0) return true
        val copy = MotionEvent.obtain(event)
        val matrix = Matrix()
        val scaleX = instance.displayWidth.toFloat() / surfaceW
        val scaleY = instance.displayHeight.toFloat() / surfaceH
        matrix.postScale(scaleX, scaleY)
        copy.transform(matrix)
        val idx = copy.actionIndex
        Renderer.handleTouch(
            copy.actionMasked,
            copy.getPointerId(idx),
            copy.getX(idx),
            copy.getY(idx),
            copy.getPressure(idx),
        )
        copy.recycle()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Renderer.sendKeycode(keyCode)
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        stopRepaintLoop()
        hideBootLogOverlay()
        exportLogNow()
        super.onDestroy()
    }

    private fun scheduleLogExport() {
        Handler(Looper.getMainLooper()).postDelayed({ exportLogNow() }, 8000)
    }

    private fun exportLogNow() {
        val exported = InstanceLog.export(this, instance)
        Log.i(TAG, "log export: ${exported.absolutePath}")
    }

    private fun spawnRootfs(instanceId: String): File {
        val link = repo.activeRootfsLink()
        return if (link.exists()) link else repo.rootfsDir(instanceId)
    }

    companion object {
        const val EXTRA_ID = "instance_id"
        const val EXTRA_REBOOT = "reboot"
        private const val TAG = "VmActivity"
    }
}
