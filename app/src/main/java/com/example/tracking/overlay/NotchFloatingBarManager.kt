package com.example.tracking.overlay

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.ScrollyApp
import com.example.data.repository.HardwareCutoutInfo
import com.example.data.repository.IslandPlacementMode
import com.example.data.repository.NotchShape
import com.example.tracking.detector.DisplayCutoutDetectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * NotchFloatingBarManager
 *
 * Renders and manages the floating "Dynamic Island" pill overlay over social media apps.
 *
 * Dynamic Island positioning logic:
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  PUNCH_HOLE_CENTER  → WRAP_AROUND_NOTCH                            │
 *  │    Pill Y = notch.top (px) so pill is vertically level with notch  │
 *  │    Center spacer width = notch.width + 8dp clearance               │
 *  │    Result: [ 🔥 count • ]  ●  [ • 🔥 count ] — true Dynamic Island│
 *  │                                                                     │
 *  │  PUNCH_HOLE_LEFT / RIGHT → BELOW_NOTCH                             │
 *  │    Pill offset away from the corner camera                         │
 *  │                                                                     │
 *  │  WIDE_NOTCH / WATER_DROP / NO_NOTCH → BELOW_NOTCH                  │
 *  │    Pill Y = safeInsetTop + 4dp, centered horizontally              │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * WindowManager flags:
 *  - TYPE_ACCESSIBILITY_OVERLAY (from AccessibilityService context)
 *  - FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN
 *  - FLAG_LAYOUT_NO_LIMITS | FLAG_HARDWARE_ACCELERATED
 *  - LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES (allows drawing in cutout area)
 *  - fitInsetsTypes = 0 on R+ (no automatic inset adjustment)
 */
class NotchFloatingBarManager(
    private val context: Context,
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
) {
    companion object {
        private const val TAG = "NotchFloatingBar"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope       = CoroutineScope(Dispatchers.Main + Job())

    private var rootView:          FrameLayout?   = null
    private var pillContainer:     LinearLayout?  = null
    private var leftWingView:      LinearLayout?  = null
    private var centerSpacerView:  View?          = null
    private var rightWingView:     LinearLayout?  = null
    private var compactIconView:   TextView?      = null
    private var iconView:          TextView?      = null
    private var countTextView:     TextView?      = null
    private var statusDot:         View?          = null

    private var animationController: DynamicIslandAnimationController? = null

    private var isWindowAttached  = false
    var isReelActive              = false
        private set
    private var currentAppName    = "Shorts"
    private var currentScrollCount = 0
    private var targetDailyGoal   = 100

    private var statsJob:  Job? = null
    private var configJob: Job? = null

    init {
        // Observe live scroll count from repository
        statsJob = scope.launch {
            try {
                ScrollyApp.instance.trackingRepository.todayTotalScrolls.collectLatest { scrolls ->
                    currentScrollCount = scrolls
                    if (isReelActive) {
                        mainHandler.post {
                            updateUI(scrolls, currentAppName, targetDailyGoal)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in stats flow", e)
            }
        }

        // Observe notch configuration changes and reposition/resize overlay reactively
        configJob = scope.launch {
            try {
                ScrollyApp.instance.notchSettingsRepository.configFlow.collectLatest { config ->
                    mainHandler.post {
                        applyConfigUpdate(
                            config.offsetX,
                            config.offsetY,
                            config.cutoutGapWidth,
                            config.placementMode,
                            config.detectedCutout.notchShape
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in config flow", e)
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Ensures the overlay window is attached to WindowManager.
     * Safe to call from any thread.
     */
    fun attachWindowIfNeeded() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            attachWindowInternal()
        } else {
            mainHandler.post { attachWindowInternal() }
        }
    }

    /**
     * Shows the Dynamic Island counter when reel/short activity is detected.
     */
    fun show(appName: String, scrollCount: Int = currentScrollCount, dailyGoal: Int = targetDailyGoal) {
        currentAppName     = appName
        currentScrollCount = scrollCount
        targetDailyGoal    = dailyGoal
        isReelActive       = true

        val action = Runnable {
            attachWindowInternal()
            updateUI(scrollCount, appName, dailyGoal)
            pillContainer?.visibility = View.VISIBLE
            pillContainer?.alpha      = 1f
            animationController?.enforceStaticCompactPill()
            val config = ScrollyApp.instance.notchSettingsRepository.configFlow.value
            applyConfigUpdate(
                config.offsetX,
                config.offsetY,
                config.cutoutGapWidth,
                config.placementMode,
                config.detectedCutout.notchShape
            )
            Log.d(TAG, "[SHOW] app=$appName  count=$scrollCount  shape=${config.detectedCutout.notchShape}")
        }

        if (Looper.myLooper() == Looper.getMainLooper()) action.run() else mainHandler.post(action)
    }

    /**
     * Updates the live scroll count instantly without morphing animations.
     */
    fun updateCount(newCount: Int, appName: String = currentAppName, dailyGoal: Int = targetDailyGoal) {
        currentScrollCount = newCount
        currentAppName     = appName
        targetDailyGoal    = dailyGoal
        isReelActive       = true

        val action = Runnable {
            if (!isWindowAttached || rootView?.isAttachedToWindow != true) {
                show(appName, newCount, dailyGoal)
            } else {
                pillContainer?.visibility = View.VISIBLE
                pillContainer?.alpha      = 1f
                updateUI(newCount, appName, dailyGoal)
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) action.run() else mainHandler.post(action)
    }

    /**
     * Hides the Dynamic Island with an optional fade-out animation.
     * When [keepAttached] is false, the window is removed from WindowManager immediately.
     */
    fun hide(immediate: Boolean = false, keepAttached: Boolean = false) {
        isReelActive = false

        val action = Runnable {
            if (!isWindowAttached || rootView == null) return@Runnable
            if (immediate) {
                pillContainer?.visibility = View.GONE
                detachWindow()
            } else {
                animationController?.animateExit {
                    if (!isReelActive) detachWindow()
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) action.run() else mainHandler.post(action)
    }

    /**
     * Unconditionally removes the overlay from WindowManager — called on service interrupt,
     * destroy, or when a non-social app moves to the foreground.
     */
    fun forceHide() {
        isReelActive = false
        if (Looper.myLooper() == Looper.getMainLooper()) {
            pillContainer?.visibility = View.GONE
            detachWindow()
        } else {
            mainHandler.post {
                pillContainer?.visibility = View.GONE
                detachWindow()
            }
        }
        Log.d(TAG, "[FORCE_HIDE] Overlay removed from WindowManager")
    }

    fun onDestroy() {
        statsJob?.cancel()
        configJob?.cancel()
        scope.cancel()
        animationController?.onDestroy()
        detachWindow()
    }

    // ── Window management ─────────────────────────────────────────────────

    private fun attachWindowInternal() {
        if (isWindowAttached && rootView?.isAttachedToWindow == true) return

        try {
            if (rootView == null) createNotchView()

            val params = createLayoutParams()
            windowManager.addView(rootView, params)
            isWindowAttached = true
            Log.d(TAG, "[ATTACH] Overlay window attached")

            if (isReelActive) {
                pillContainer?.visibility = View.VISIBLE
                pillContainer?.alpha      = 1f
                animationController?.enforceStaticCompactPill()
                val config = ScrollyApp.instance.notchSettingsRepository.configFlow.value
                applyConfigUpdate(
                    config.offsetX, config.offsetY,
                    config.cutoutGapWidth, config.placementMode,
                    config.detectedCutout.notchShape
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "[ATTACH] Failed to attach overlay window", e)
            isWindowAttached = false
        }
    }

    private fun detachWindow() {
        try {
            if (rootView?.isAttachedToWindow == true) {
                windowManager.removeViewImmediate(rootView)
                Log.d(TAG, "[DETACH] Overlay window detached")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[DETACH] Error removing overlay view", e)
        } finally {
            rootView          = null
            pillContainer     = null
            leftWingView      = null
            centerSpacerView  = null
            rightWingView     = null
            compactIconView   = null
            iconView          = null
            countTextView     = null
            statusDot         = null
            animationController = null
            isWindowAttached  = false
        }
    }

    // ── Layout params ─────────────────────────────────────────────────────

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val windowType = when {
            context is AccessibilityService -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            context is Activity             -> WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE         or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL       or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN      or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS      or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val config  = ScrollyApp.instance.notchSettingsRepository.configFlow.value
        val cutout  = config.detectedCutout
        val (posX, posY) = resolveOverlayPosition(config.placementMode, cutout.notchShape,
                                                    config.offsetX, config.offsetY, cutout)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = posX
            y = posY
        }

        // Allow overlay to draw inside the cutout area (needed for punch-hole wrap-around)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Disable automatic inset fitting so we control the Y position precisely
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.fitInsetsTypes = 0
            params.fitInsetsSides = 0
        }

        Log.d(TAG, "[PARAMS] type=$windowType  x=$posX  y=$posY  " +
              "mode=${config.placementMode}  shape=${cutout.notchShape}")
        return params
    }

    /**
     * Resolves the overlay's (x, y) pixel position based on notch shape and placement mode.
     *
     * For WRAP_AROUND_NOTCH (punch-hole center):
     *   y = cutout top in px, so the pill's vertical center aligns with the camera
     * For BELOW_NOTCH:
     *   y = safeInsetTop in px + small gap, so text is never under the camera
     */
    private fun resolveOverlayPosition(
        mode: IslandPlacementMode,
        shape: NotchShape,
        offsetXDp: Int,
        offsetYDp: Int,
        cutout: HardwareCutoutInfo
    ): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density

        val x: Int = dpToPx(offsetXDp)

        val y: Int = when (mode) {
            IslandPlacementMode.WRAP_AROUND_NOTCH -> {
                // Align the pill with the notch top edge in screen-pixel coordinates
                // The pill is centered on the notch's vertical midpoint
                val notchTopPx = (cutout.top * density).toInt()
                notchTopPx.coerceAtLeast(0)
            }
            IslandPlacementMode.BELOW_NOTCH -> {
                if (offsetYDp > 0) {
                    dpToPx(offsetYDp)
                } else {
                    // Derive from safeInsetTop: guaranteed below camera + status bar
                    val safePx = (cutout.safeInsetTop * density).toInt()
                    (safePx + dpToPx(4)).coerceAtLeast(dpToPx(28))
                }
            }
        }

        return x to y
    }

    // ── View creation ─────────────────────────────────────────────────────

    private fun createNotchView() {
        val ctx  = context
        val root = FrameLayout(ctx)
        rootView = root

        // Re-query cutout whenever the window attaches (catches first-time calibration)
        // OnWindowAttachListener is API 18+; rootWindowInsets is API 23+; displayCutout is API 28+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            root.viewTreeObserver.addOnWindowAttachListener(object : ViewTreeObserver.OnWindowAttachListener {
                override fun onWindowAttached() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
                    val insets = root.rootWindowInsets ?: return
                    val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        insets.displayCutout
                    } else null
                    cutout ?: return
                    val detected = DisplayCutoutDetectionService.parseDisplayCutout(cutout, ctx)
                    val repo     = ScrollyApp.instance.notchSettingsRepository
                    if (!repo.configFlow.value.autoAdjusted) {
                        Log.d(TAG, "[CUTOUT] Auto-calibrating from window insets: $detected")
                        repo.autoCalibrateWithCutout(detected)
                    }
                }
                override fun onWindowDetached() {}
            })
        }

        // ── Pill container (the "Dynamic Island" capsule) ─────────────────
        val pill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))

            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(20).toFloat()
                setColor(Color.BLACK)                              // Pure Apple obsidian
                setStroke(dpToPx(1), Color.parseColor("#2A2A2A")) // Subtle rim light
            }
            elevation  = dpToPx(12).toFloat()
            visibility = View.GONE
            alpha      = 0f
        }
        pillContainer = pill

        // 1. Compact idle icon (hidden in expanded state)
        val compactIcon = TextView(ctx).apply {
            text      = "⚡"
            textSize  = 12f
            gravity   = Gravity.CENTER
            visibility = View.GONE
        }
        compactIconView = compactIcon
        pill.addView(compactIcon)

        // 2. LEFT WING: App emoji icon
        val leftWing = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        leftWingView = leftWing

        val icon = TextView(ctx).apply {
            text     = "🔥"
            textSize = 13f
            gravity  = Gravity.CENTER
            setPadding(0, 0, dpToPx(4), 0)
        }
        iconView = icon
        leftWing.addView(icon)
        pill.addView(leftWing)

        // 3. CENTER SPACER — width driven by cutout width in WRAP_AROUND_NOTCH mode
        //    Hidden (width=0) in BELOW_NOTCH mode so the pill stays compact
        val centerSpacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(20))
            visibility   = View.GONE
        }
        centerSpacerView = centerSpacer
        pill.addView(centerSpacer)

        // 4. RIGHT WING: scroll count + status dot
        val rightWing = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dpToPx(6) }
        }
        rightWingView = rightWing

        val countText = TextView(ctx).apply {
            text     = "0"
            textSize = 13f
            setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD), Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity  = Gravity.CENTER
        }
        countTextView = countText
        rightWing.addView(countText)

        val dot = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(7), dpToPx(7)).apply {
                leftMargin = dpToPx(5)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4CAF50"))
            }
        }
        statusDot = dot
        rightWing.addView(dot)
        pill.addView(rightWing)

        // Add pill into full-screen root frame (centered by default; x/y set in layout params)
        root.addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        // Initialize animation controller
        animationController = DynamicIslandAnimationController(
            pillContainer    = pill,
            leftWingView     = leftWing,
            centerSpacerView = centerSpacer,
            rightWingView    = rightWing,
            compactIconView  = compactIcon,
            dpToPx           = { dpToPx(it) },
            onLayoutRequested = {
                try { rootView?.requestLayout() } catch (_: Exception) {}
            }
        )
    }

    // ── UI updates ────────────────────────────────────────────────────────

    private fun updateUI(count: Int, appName: String, dailyGoal: Int) {
        iconView?.text      = "🔥"
        countTextView?.text = count.toString()

        val isNearLimit  = count >= (dailyGoal * 0.8f).toInt()
        val isOverLimit  = count >= dailyGoal

        val dotColor = when {
            isOverLimit  -> Color.parseColor("#FF5252") // Alert red
            isNearLimit  -> Color.parseColor("#FFAB00") // Warning amber
            else         -> Color.parseColor("#4CAF50") // Safe green
        }
        (statusDot?.background as? GradientDrawable)?.setColor(dotColor)
    }

    /**
     * Applies a configuration update — repositions the window and adjusts the center spacer
     * to correctly clear the hardware notch in WRAP_AROUND_NOTCH mode.
     */
    private fun applyConfigUpdate(
        offsetX: Int,
        offsetY: Int,
        cutoutGapWidth: Int,
        placementMode: IslandPlacementMode,
        notchShape: NotchShape
    ) {
        val config = ScrollyApp.instance.notchSettingsRepository.configFlow.value
        val cutout = config.detectedCutout

        // ── Center spacer width ───────────────────────────────────────────
        centerSpacerView?.let { spacer ->
            val lp = spacer.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(0, dpToPx(20))

            if (placementMode == IslandPlacementMode.WRAP_AROUND_NOTCH && cutoutGapWidth > 0) {
                // Convert dp gap → px for the spacer so text clears the camera lens
                lp.width   = dpToPx(cutoutGapWidth)
                spacer.visibility = View.VISIBLE
                Log.d(TAG, "[SPACER] gap=${cutoutGapWidth}dp  px=${lp.width}  shape=$notchShape")
            } else {
                lp.width   = 0
                spacer.visibility = View.GONE
            }
            spacer.layoutParams = lp
        }

        // ── WindowManager position ────────────────────────────────────────
        if (isWindowAttached && rootView?.isAttachedToWindow == true) {
            try {
                val params = rootView?.layoutParams as? WindowManager.LayoutParams
                    ?: createLayoutParams()

                val (newX, newY) = resolveOverlayPosition(
                    placementMode, notchShape, offsetX, offsetY, cutout
                )
                params.x = newX
                params.y = newY
                windowManager.updateViewLayout(rootView, params)
                Log.d(TAG, "[REPOSITION] x=$newX  y=$newY  mode=$placementMode  shape=$notchShape")
            } catch (e: Exception) {
                Log.w(TAG, "[REPOSITION] Error updating window layout", e)
            }
        }
    }

    // ── Util ──────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
