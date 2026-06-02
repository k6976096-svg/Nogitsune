package io.kitsuri.nogitsune.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.TextureView
import android.view.View
import java.io.File
import java.io.RandomAccessFile
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

class BootLogTexture @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : TextureView(context, attrs, defStyleAttr, defStyleRes),
    TextureView.SurfaceTextureListener {

    private val rendering = AtomicBoolean(false)
    private val logMessages = LimitedQueue<String>(160)
    private val snapShot = LinkedList<String>()
    private val paints = SparseArray<Paint>()
    private val defaultPaint = Paint()
    private var logTailThread: Thread? = null

    init {
        surfaceTextureListener = this
        for (i in 0 until COLOR_MAP.size()) {
            val key = COLOR_MAP.keyAt(i)
            val value = COLOR_MAP.valueAt(i)
            val paint = Paint()
            setPaint(paint, value)
            paints.put(key, paint)
        }
        setPaint(defaultPaint, Color.WHITE)
    }

    fun appendLine(line: String) {
        if (line.isBlank() || !shouldShowLogLine(line)) return
        synchronized(logMessages) {
            logMessages.add(line)
        }
    }

    private fun shouldShowLogLine(line: String): Boolean {
        val s = line.lowercase()
        if (s.contains("sched_fifo") || s.contains("sfeventthread")) return false
        if (line.contains(" pid-") && line.contains("DEBUG")) return false
        if (line.contains("#00 pc ") || line.contains("#01 pc ")) return false
        if (line.contains("libsurfaceflinger.so") || line.contains("allocator@2.0-impl.so")) {
            return false
        }
        if (s.contains("compat change id reported")) return false
        if (s.contains("blastbufferqueue") || s.contains("bufferqueuedebug")) return false
        return true
    }

    fun startLogTail(logFile: File) {
        logTailThread?.interrupt()
        logTailThread = Thread({
            var offset = 0L
            while (!Thread.currentThread().isInterrupted && rendering.get()) {
                runCatching {
                    if (!logFile.exists()) {
                        SystemClock.sleep(200)
                        return@runCatching
                    }
                    RandomAccessFile(logFile, "r").use { raf ->
                        if (offset > raf.length()) offset = 0
                        raf.seek(offset)
                        var line = raf.readLine()
                        while (line != null) {
                            appendLine(line)
                            line = raf.readLine()
                        }
                        offset = raf.filePointer
                    }
                }
                SystemClock.sleep(250)
            }
        }, "boot-log-tail").also { it.isDaemon = true; it.start() }
    }

    fun stopLogTail() {
        logTailThread?.interrupt()
        logTailThread = null
    }

    private fun setPaint(paint: Paint, color: Int) {
        paint.color = color
        paint.isAntiAlias = true
        paint.textSize = 16f
        paint.alpha = 128
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rendering.set(true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rendering.set(false)
        stopLogTail()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        rendering.set(visibility == VISIBLE)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        rendering.set(false)
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (!rendering.get()) return
        render()
    }

    private fun render() {
        val canvas = lockCanvas() ?: return
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            snapShot.clear()
            synchronized(logMessages) { snapShot.addAll(logMessages) }
            var count = 0
            for (log in snapShot) {
                val chr = log.firstOrNull() ?: continue
                val paint = paints[chr.code] ?: defaultPaint
                canvas.drawText(log, 0f, (count++ * 20).toFloat(), paint)
            }
        } finally {
            unlockCanvasAndPost(canvas)
        }
    }

    companion object {
        private val COLOR_MAP = SparseIntArray().apply {
            put('V'.code, 0xBBBBBB)
            put('D'.code, 0x5EBB1E)
            put('I'.code, 0x4CBBA2)
            put('W'.code, 0xFFD21C)
            put('E'.code, 0xFF6B68)
            put('F'.code, Color.RED)
            put('G'.code, 0x88CCFF)
        }
    }
}
