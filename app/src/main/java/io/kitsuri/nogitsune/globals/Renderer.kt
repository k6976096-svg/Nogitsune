package io.kitsuri.nogitsune.globals

import android.view.Surface

object Renderer {
    init {
        System.loadLibrary("nogitsune")
    }

    external fun nativeSetPaths(rootfs: String, logPath: String, libDir: String)
    external fun init(surface: Surface, loader: String, width: Int, height: Int, xdpi: Float, ydpi: Float, fps: Int)
    external fun resetWindow(surface: Surface, top: Int, left: Int, width: Int, height: Int, fbWidth: Int, fbHeight: Int)
    external fun removeWindow(surface: Surface)
    external fun handleTouch(action: Int, pointerId: Int, x: Float, y: Float, pressure: Float)
    external fun sendKeycode(keycode: Int)
    external fun setDebugRenderer(debugEnabled: Int)
    external fun shutdown()
    external fun resetRuntime()
    external fun repaint()
}
