package com.example.tracking.detector

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.DisplayCutout
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.example.data.repository.HardwareCutoutInfo
import com.example.data.repository.NotchSettingsRepository
import com.example.data.repository.NotchShape

/**
 * DisplayCutoutDetectionService
 *
 * Dynamically queries WindowInsets and DisplayCutout to determine:
 *  - The exact pixel bounds of the hardware camera notch / punch-hole
 *  - The precise NotchShape (PunchHoleCenter, Left, Right, WideNotch, WaterDrop, NoNotch)
 *  - Safe inset margins so the overlay never overlaps the camera or status bar text
 *
 * Shape classification rules (measured in dp):
 *  - width < 40dp AND height < 28dp AND centerX ∈ (-18, +18) → PUNCH_HOLE_CENTER
 *  - width < 40dp AND height < 28dp AND centerX < -18         → PUNCH_HOLE_LEFT
 *  - width < 40dp AND height < 28dp AND centerX > +18         → PUNCH_HOLE_RIGHT
 *  - width > 60dp (wider than two fingers)                     → WIDE_NOTCH
 *  - height > width * 1.25 (taller than wide)                 → WATER_DROP
 *  - no bounding rect detected                                 → NO_NOTCH
 */
object DisplayCutoutDetectionService {

    private const val TAG = "DisplayCutoutService"

    // ── Thresholds (dp) ────────────────────────────────────────────────────
    private const val PUNCH_HOLE_MAX_WIDTH_DP  = 40
    private const val PUNCH_HOLE_MAX_HEIGHT_DP = 28
    private const val WIDE_NOTCH_MIN_WIDTH_DP  = 60
    private const val CENTER_TOLERANCE_DP      = 18 // ±18dp from horizontal center → "center"
    private const val WATERDROP_RATIO          = 1.25f // height/width ratio for teardrop

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Queries display cutout dynamically from system WindowManager or a provided Window.
     * Tries multiple APIs in order of reliability:
     *  1. Window decorView rootWindowInsets (most accurate, needs attached window)
     *  2. WindowManager.currentWindowMetrics (Android R+)
     *  3. Display.cutout (Android Q+)
     *  4. Fallback status-bar height
     */
    fun queryCutout(context: Context, window: Window? = null): HardwareCutoutInfo {
        // 1. Try Window decorView rootWindowInsets if window is provided
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null) {
            try {
                val insets = window.decorView.rootWindowInsets
                if (insets != null) {
                    val info = extractFromWindowInsets(insets, context)
                    if (info.hasCutout) {
                        Log.d(TAG, "Cutout from Window insets: $info")
                        return info
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not query cutout from window", e)
            }
        }

        // 2. Query system WindowManager currentWindowMetrics (Android R+, API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (wm != null) {
                    val insets = wm.currentWindowMetrics.windowInsets
                    val info = extractFromWindowInsets(insets, context)
                    if (info.hasCutout) {
                        Log.d(TAG, "Cutout from WindowMetrics: $info")
                        return info
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not query cutout from WindowMetrics", e)
            }
        }

        // 3. Query Display cutout directly (Android Q+, API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
                }
                val cutout = display?.cutout
                if (cutout != null) {
                    val info = parseDisplayCutout(cutout, context)
                    Log.d(TAG, "Cutout from Display.cutout: $info")
                    return info
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not query cutout from Display", e)
            }
        }

        // 4. Fallback status bar calculation
        val fallback = getStatusBarFallback(context)
        Log.d(TAG, "Using status bar fallback: $fallback")
        return fallback
    }

    /**
     * Extracts cutout info from WindowInsets.
     */
    fun extractFromWindowInsets(insets: WindowInsets, context: Context): HardwareCutoutInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cutout = insets.displayCutout
            if (cutout != null) {
                return parseDisplayCutout(cutout, context)
            }
        }
        return getStatusBarFallback(context)
    }

    /**
     * Parses the DisplayCutout bounds to get exact metrics and classifies the NotchShape.
     *
     * All returned values in [HardwareCutoutInfo] use dp unless documented otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun parseDisplayCutout(cutout: DisplayCutout, context: Context): HardwareCutoutInfo {
        val density        = context.resources.displayMetrics.density
        val screenWidthPx  = context.resources.displayMetrics.widthPixels
        val safeTopPx      = cutout.safeInsetTop
        val safeTopDp      = (safeTopPx / density).toInt()

        val rects = cutout.boundingRects
        if (!rects.isNullOrEmpty()) {
            // Prefer a cutout along the top edge of the screen
            val topCutout = rects.firstOrNull { it.top <= safeTopPx + 4 }
                ?: rects.minByOrNull { it.top }
                ?: rects.first()

            val centerXPx      = (topCutout.left + topCutout.right) / 2f
            val screenCenterPx = screenWidthPx / 2f
            val offsetXPx      = centerXPx - screenCenterPx  // negative = left, positive = right

            val centerXDp  = (offsetXPx / density).toInt()
            val widthDp    = (topCutout.width()  / density).toInt().coerceAtLeast(18)
            val heightDp   = (topCutout.height() / density).toInt().coerceAtLeast(14)
            val topDp      = (topCutout.top    / density).toInt()
            val bottomDp   = (topCutout.bottom / density).toInt()

            val shape = classifyNotchShape(centerXDp, widthDp, heightDp)

            Log.d(
                TAG,
                "Hardware cutout parsed: bounds=[${topCutout.left},${topCutout.top}," +
                "${topCutout.right},${topCutout.bottom}]px  " +
                "size=${widthDp}×${heightDp}dp  centerX=$centerXDp dp  " +
                "safeTop=$safeTopDp dp  shape=$shape"
            )

            return HardwareCutoutInfo(
                hasCutout    = true,
                notchShape   = shape,
                centerX      = centerXDp,
                top          = topDp,
                bottom       = bottomDp.coerceAtLeast(heightDp),
                width        = widthDp,
                height       = heightDp,
                safeInsetTop = safeTopDp
            )
        }

        // No bounding rects but safeInsetTop > 0 → some OEM full-width notch or screen-edge sensor
        if (safeTopDp > 0) {
            Log.d(TAG, "No bounding rects. safeInsetTop=$safeTopDp → assuming WIDE_NOTCH")
            return HardwareCutoutInfo(
                hasCutout    = true,
                notchShape   = NotchShape.WIDE_NOTCH,
                centerX      = 0,
                top          = 0,
                bottom       = safeTopDp,
                width        = 80, // assume wide
                height       = safeTopDp,
                safeInsetTop = safeTopDp
            )
        }

        return getStatusBarFallback(context)
    }

    // ── Shape Classification ───────────────────────────────────────────────

    /**
     * Classifies the notch shape from its dp geometry.
     *
     * @param centerXDp  Horizontal offset of cutout center from screen center in dp (neg = left)
     * @param widthDp    Cutout width in dp
     * @param heightDp   Cutout height in dp
     */
    fun classifyNotchShape(centerXDp: Int, widthDp: Int, heightDp: Int): NotchShape {
        return when {
            // Wide horizontal notch spanning most of the top edge
            widthDp >= WIDE_NOTCH_MIN_WIDTH_DP ->
                NotchShape.WIDE_NOTCH

            // Teardrop: noticeably taller than wide
            heightDp >= (widthDp * WATERDROP_RATIO) ->
                NotchShape.WATER_DROP

            // Small round/oval punch-hole — determine left/center/right by X offset
            widthDp <= PUNCH_HOLE_MAX_WIDTH_DP && heightDp <= PUNCH_HOLE_MAX_HEIGHT_DP -> when {
                centerXDp < -CENTER_TOLERANCE_DP -> NotchShape.PUNCH_HOLE_LEFT
                centerXDp >  CENTER_TOLERANCE_DP -> NotchShape.PUNCH_HOLE_RIGHT
                else                              -> NotchShape.PUNCH_HOLE_CENTER
            }

            // Medium-sized, roughly square cutout near center → treat as center punch-hole
            else -> NotchShape.PUNCH_HOLE_CENTER
        }
    }

    // ── Fallback ──────────────────────────────────────────────────────────

    private fun getStatusBarFallback(context: Context): HardwareCutoutInfo {
        val density    = context.resources.displayMetrics.density
        val resId      = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusPx   = if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
        val statusBarDp = (statusPx / density).toInt().coerceAtLeast(28)

        return HardwareCutoutInfo(
            hasCutout    = false,
            notchShape   = NotchShape.NO_NOTCH,
            centerX      = 0,
            top          = 0,
            bottom       = statusBarDp,
            width        = 36,
            height       = statusBarDp,
            safeInsetTop = statusBarDp
        )
    }

    // ── Repository Sync ───────────────────────────────────────────────────

    /**
     * Synchronizes and applies the detected hardware cutout to the notch settings repository.
     */
    fun syncWithRepository(context: Context, repo: NotchSettingsRepository, window: Window? = null) {
        try {
            val detected = queryCutout(context, window)
            repo.autoCalibrateWithCutout(detected)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync cutout with repository", e)
        }
    }
}
