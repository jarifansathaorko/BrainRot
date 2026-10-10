package com.example.tracking.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.ScrollyApp
import com.example.tracking.detector.AppRecognitionEngine
import com.example.tracking.detector.ScrollDetectionEngine
import com.example.tracking.overlay.NotchFloatingBarManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ScrollyAccessibilityService
 *
 * Central AccessibilityService that:
 *  1. Tracks the real-time foreground package via a [foregroundPackageFlow] StateFlow.
 *  2. Immediately force-hides the overlay whenever a non-social app moves to the foreground,
 *     preventing the counter from persisting on the home screen or other apps.
 *  3. Delegates scroll/reel detection to [ScrollDetectionEngine].
 *  4. Manages [NotchFloatingBarManager] lifecycle and visibility.
 *  5. Emits structured Logcat entries on every window transition for debugging.
 */
class ScrollyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ScrollyAccessService"

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive = _isServiceActive.asStateFlow()

        /** Exposes the foreground package name in real-time for observers (e.g. debug UI). */
        private val _foregroundPackageFlow = MutableStateFlow("")
        val foregroundPackageFlow = _foregroundPackageFlow.asStateFlow()

        /** Current window title / activity class for real-time debugging. */
        private val _windowTitleFlow = MutableStateFlow("")
        val windowTitleFlow = _windowTitleFlow.asStateFlow()

        var activeServiceInstance: ScrollyAccessibilityService? = null
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler   = Handler(Looper.getMainLooper())
    private var notchFloatingBarManager: NotchFloatingBarManager? = null
    private var scrollDetectionEngine: ScrollDetectionEngine? = null

    fun getFloatingBarManager(): NotchFloatingBarManager? = notchFloatingBarManager

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        _isServiceActive.value = true
        activeServiceInstance  = this
        Log.i(TAG, "[SERVICE] onCreate — Scrolly AccessibilityService starting")

        try {
            notchFloatingBarManager = NotchFloatingBarManager(this)
        } catch (e: Exception) {
            Log.e(TAG, "[SERVICE] Failed to initialize NotchFloatingBarManager", e)
        }

        scrollDetectionEngine = ScrollDetectionEngine(
            service = this,
            onScrollDetected = { pkg, appName ->
                incrementCount(pkg, appName)
            },
            onReelVisibilityChanged = { pkg, appName, isWatchingReels ->
                mainHandler.post {
                    if (isWatchingReels) {
                        val count = ScrollyApp.instance.trackingRepository.todayTotalScrolls.value
                        notchFloatingBarManager?.attachWindowIfNeeded()
                        notchFloatingBarManager?.show(appName, count)
                        Log.d(TAG, "[OVERLAY] show — pkg=$pkg  app=$appName  count=$count")
                    } else {
                        Log.d(TAG, "[OVERLAY] hide — pkg=$pkg  app=$appName")
                        notchFloatingBarManager?.forceHide()
                    }
                }
            }
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "[SERVICE] onServiceConnected — configuring event types")
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes =
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED   or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED        or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED          or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags =
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                notificationTimeout = 100
            }
            this.serviceInfo = info
        } catch (e: Exception) {
            Log.w(TAG, "[SERVICE] Could not configure serviceInfo", e)
        }
        _isServiceActive.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // ── Real-time window tracking + immediate overlay guard ────────────
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg        = event.packageName?.toString() ?: ""
            val className  = event.className?.toString() ?: ""
            val title      = event.text.firstOrNull()?.toString() ?: ""

            _foregroundPackageFlow.value = pkg
            _windowTitleFlow.value       = "$pkg / $className"

            Log.d(
                TAG,
                "[WINDOW] pkg=$pkg  class=$className  title=\"$title\"  " +
                "supported=${AppRecognitionEngine.isSupported(pkg)}"
            )

            // If the foreground app is NOT a tracked social app, immediately force-hide
            if (!AppRecognitionEngine.isSupported(pkg) &&
                pkg.isNotEmpty() &&
                !isIgnoredUiPackage(pkg)
            ) {
                mainHandler.post {
                    if (notchFloatingBarManager?.isReelActive == true) {
                        Log.d(TAG, "[OVERLAY] force-hide — non-social foreground: $pkg")
                        notchFloatingBarManager?.forceHide()
                    }
                }
            }
        }

        // Delegate all event processing to the detection engine
        scrollDetectionEngine?.processAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        Log.w(TAG, "[SERVICE] onInterrupt — forcing overlay hide")
        mainHandler.post { notchFloatingBarManager?.forceHide() }
        scrollDetectionEngine?.resetState()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "[SERVICE] onDestroy — tearing down Scrolly service")
        _isServiceActive.value = false
        activeServiceInstance  = null
        scrollDetectionEngine?.resetState()
        scrollDetectionEngine  = null
        notchFloatingBarManager?.onDestroy()
        notchFloatingBarManager = null
        serviceScope.cancel()
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun incrementCount(pkg: String, appName: String) {
        val optimisticCount = ScrollyApp.instance.trackingRepository.todayTotalScrolls.value + 1
        mainHandler.post {
            notchFloatingBarManager?.updateCount(optimisticCount, appName)
        }
        serviceScope.launch {
            try {
                ScrollyApp.instance.trackingRepository.recordScroll(pkg, appName, 1)
            } catch (e: Exception) {
                Log.e(TAG, "[SERVICE] Error recording scroll event", e)
            }
        }
    }

    /**
     * Returns true for system UI packages that appear in the window event stream but
     * should NOT trigger an overlay hide (e.g. status bar, IME, permission dialogs).
     */
    private fun isIgnoredUiPackage(pkg: String): Boolean {
        return pkg == "android" ||
               pkg.startsWith("com.android.systemui") ||
               pkg.contains("inputmethod") ||
               pkg.contains("keyboard") ||
               pkg == "com.google.android.gms" ||
               pkg == "com.google.android.webview" ||
               pkg == "com.android.vending" ||
               pkg == packageName // our own package
    }
}
