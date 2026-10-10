package com.example.tracking.overlay

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup

/**
 * DynamicIslandAnimationController
 *
 * Optimised controller for the floating dynamic bar overlay.
 * Maintains a solid, stable, static-sized compact capsule [ 🔥  <count>  • ]
 * without unwanted expanding animations, keeping the count away from the notch camera.
 *
 * The only animation applied is a smooth 120ms alpha fade-out on exit so the overlay
 * dismisses gracefully rather than snapping to invisible.
 */
class DynamicIslandAnimationController(
    private val pillContainer:    View,
    private val leftWingView:     View,
    private val centerSpacerView: View,
    private val rightWingView:    View,
    private val compactIconView:  View,
    private val dpToPx:           (Int) -> Int,
    private val onLayoutRequested: (() -> Unit)? = null
) {
    enum class IslandState { HIDDEN, VISIBLE }

    private val mainHandler  = Handler(Looper.getMainLooper())
    private var currentState = IslandState.HIDDEN

    fun getState(): IslandState = currentState

    // ── Visible / static compact pill ─────────────────────────────────────

    /**
     * Enforces the solid, sleek compact pill shape without expanding across the screen.
     * The center spacer width is managed externally by [NotchFloatingBarManager.applyConfigUpdate].
     */
    fun enforceStaticCompactPill() {
        compactIconView.visibility = View.GONE

        leftWingView.visibility  = View.VISIBLE
        leftWingView.alpha       = 1f
        rightWingView.visibility = View.VISIBLE
        rightWingView.alpha      = 1f

        pillContainer.visibility  = View.VISIBLE
        pillContainer.alpha       = 1f
        pillContainer.scaleX      = 1f
        pillContainer.scaleY      = 1f
        pillContainer.translationY = 0f

        val lp = pillContainer.layoutParams
        if (lp != null) {
            lp.width  = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            pillContainer.layoutParams = lp
        }
        currentState = IslandState.VISIBLE
        onLayoutRequested?.invoke()
    }

    // ── Backwards-compatible aliases ───────────────────────────────────────

    /** No-op alias kept for call-site compatibility. */
    fun expandToDynamicIsland(cutoutWidthDp: Int = 0, onComplete: (() -> Unit)? = null) {
        enforceStaticCompactPill()
        onComplete?.invoke()
    }

    /** No-op alias kept for call-site compatibility. */
    fun transitionToCompactPill(onComplete: (() -> Unit)? = null) {
        enforceStaticCompactPill()
        onComplete?.invoke()
    }

    /** No-op alias — no bounce animation to avoid number shifting near notch. */
    fun triggerScrollBounce(cutoutWidthDp: Int = 0) {
        if (currentState != IslandState.VISIBLE) enforceStaticCompactPill()
    }

    /** Shows the bar instantly in clean compact form. */
    fun animateEntrance(cutoutWidthDp: Int = 0) {
        enforceStaticCompactPill()
    }

    // ── Exit animation ─────────────────────────────────────────────────────

    /**
     * Smooth 120ms alpha fade-out before signalling completion.
     * Graceful dismissal without a jarring snap-to-gone.
     */
    fun animateExit(onEnd: () -> Unit) {
        currentState = IslandState.HIDDEN
        pillContainer.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                pillContainer.visibility = View.GONE
                pillContainer.alpha      = 1f // reset for next show()
                onEnd()
            }
            .start()
    }

    fun resetIdleTimer() {
        // Bar remains steady while reels are open — no idle collapse
    }

    fun onDestroy() {
        pillContainer.animate().cancel()
    }
}
