package com.example.tracking.detector

import android.content.Context
import android.view.DisplayCutout
import android.view.Window
import android.view.WindowInsets
import com.example.data.repository.HardwareCutoutInfo

/**
 * Backward-compatible facade delegating directly to DisplayCutoutDetectionService.
 */
object NotchDetectionHelper {

    fun detectFromWindowInsets(insets: WindowInsets, context: Context): HardwareCutoutInfo {
        return DisplayCutoutDetectionService.extractFromWindowInsets(insets, context)
    }

    fun detectFromWindow(window: Window, context: Context): HardwareCutoutInfo {
        return DisplayCutoutDetectionService.queryCutout(context, window)
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.P)
    fun parseDisplayCutout(cutout: DisplayCutout, context: Context): HardwareCutoutInfo {
        return DisplayCutoutDetectionService.parseDisplayCutout(cutout, context)
    }

    fun getFallbackCutout(context: Context): HardwareCutoutInfo {
        return DisplayCutoutDetectionService.queryCutout(context, null)
    }
}
