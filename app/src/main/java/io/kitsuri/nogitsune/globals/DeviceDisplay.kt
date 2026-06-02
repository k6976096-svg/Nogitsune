package io.kitsuri.nogitsune.globals

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

data class DeviceDisplay(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
)

fun Context.deviceDisplay(): DeviceDisplay {
    val wm = getSystemService(WindowManager::class.java)
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    wm.defaultDisplay.getRealMetrics(metrics)
    return DeviceDisplay(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
}
