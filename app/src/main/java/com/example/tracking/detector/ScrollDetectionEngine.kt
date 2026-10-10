package com.example.tracking.detector

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.abs

/**
 * ScrollDetectionEngine
 *
 * Highly precise, battery-optimized reel and short-form video swipe detection engine.
 *
 * Key behaviours:
 *  - TYPE_WINDOW_STATE_CHANGED on a **non-supported** package → immediately signals hide (no delay).
 *  - TYPE_WINDOW_STATE_CHANGED on a supported package → evaluates reel context synchronously.
 *  - TYPE_VIEW_SCROLLED → counts vertical swipes with 400ms debounce; rejects horizontal carousels.
 *  - TYPE_VIEW_CLICKED  → handles tab navigation in/out of Reels/Shorts tabs.
 *  - Structured Logcat tags for every window transition:
 *      [WINDOW]  pkg=...  class=...  title=...  → supported=true/false
 *      [REEL]    pkg=...  → active=true/false
 *      [SCROLL]  pkg=...  → counted
 */
class ScrollDetectionEngine(
    private val service: AccessibilityService? = null,
    private val onScrollDetected: (packageName: String, appName: String) -> Unit,
    private val onReelVisibilityChanged: ((packageName: String, appName: String, isWatchingReels: Boolean) -> Unit)? = null
) {
    companion object {
        private const val TAG = "ScrollDetectionEngine"
        private const val MIN_SCROLL_INTERVAL_MS = 400L
        private const val THROTTLE_EVAL_MS = 250L
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        // Instagram emits several accessibility scroll events for one Reel snap. These
        // values deliberately apply only to Instagram; the existing detectors for the
        // other supported apps retain their current behaviour.
        private const val INSTAGRAM_MIN_VERTICAL_DISTANCE_PX = 240
        private const val INSTAGRAM_MIN_VELOCITY_PX_PER_MS = 0.35f
        private const val INSTAGRAM_SWIPE_COOLDOWN_MS = 900L
        private const val INSTAGRAM_COMMENT_SHEET_SUPPRESSION_MS = 1200L
        private const val MAX_COMMENT_CONTAINER_ANCESTOR_DEPTH = 15
        // YouTube Shorts: the full-screen player is a vertical RecyclerView whose view id is
        // `reel_recycler` in some versions and something else (or nothing) in others. One swipe
        // emits a burst of scroll events while the pager moves and snaps; they are collapsed into
        // one count. All thresholds live in `YouTubeShortsRules`; see that file for the reasoning.
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val YOUTUBE_SWIPE_BURST_GAP_MS = YouTubeShortsRules.BURST_GAP_MS
        // YouTube renders the Shorts pager asynchronously, so a window scan can come back empty
        // right after a transition (documented upstream as "works, then stops working until the
        // app is reopened"). Retry a few times before the pill is dismissed.
        private const val YOUTUBE_MAX_TREE_MISSES = 3
        private const val YOUTUBE_TREE_RETRY_DELAY_MS = 400L
        private const val MAX_YOUTUBE_SURFACE_SCAN_NODES = 60
        private const val MAX_YOUTUBE_TREE_SCAN_NODES = 300
    }

    private val powerManager by lazy {
        service?.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private var lastScrollTimestamp: Long = 0L
    private var lastEvalTimestamp: Long = 0L
    private var instagramCommentSheetOpenedAt: Long = 0L
    private var lastInstagramEventTimestamp: Long = 0L
    private var lastYouTubePagerEventTimestamp: Long = 0L
    private var youtubeShortsSessionActive: Boolean = false
    private var youtubeTreeMissRetries: Int = 0
    private var pendingYouTubeRecoveryRunnable: Runnable? = null

    private var currentPackage: String? = null
    private var isCurrentlyWatchingReels: Boolean = false
    private var cachedIsReelContext: Boolean = false

    private data class DetectionNode(
        val nodeId: Int,
        val parentNodeId: Int? = null,
        val className: String? = null,
        val screenWidthFraction: Float = 0f,
        val screenHeightFraction: Float = 0f,
        val isScrollable: Boolean = false,
        val isLongClickable: Boolean = false
    )

    private fun isIgnoredSystemPackage(packageName: String): Boolean {
        if (packageName == service?.packageName) return true
        if (packageName == "com.example") return true
        if (packageName == "android") return true
        if (packageName.startsWith("com.android.systemui")) return true
        if (packageName.contains("inputmethod") || packageName.contains("keyboard")) return true
        if (packageName == "com.google.android.gms") return true
        if (packageName == "com.google.android.webview") return true
        if (packageName == "com.android.vending") return true
        return false
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingEvalRunnable: Runnable? = null

    // ── Public API ────────────────────────────────────────────────────────

    fun processAccessibilityEvent(event: AccessibilityEvent) {
        // Screen off: immediately stop reel tracking
        powerManager?.let { pm ->
            if (!pm.isInteractive) {
                if (isCurrentlyWatchingReels || youtubeShortsSessionActive) {
                    Log.d(TAG, "[SCREEN_OFF] Hiding overlay — screen is off")
                    endReelSession(currentPackage ?: "", "", "screen off")
                }
                return
            }
        }

        val eventPkg = event.packageName?.toString() ?: return

        // Filter IME / system packages
        if (isIgnoredSystemPackage(eventPkg)) return

        val isEventPkgSupported = AppRecognitionEngine.isSupported(eventPkg)

        // ── Window / App switch ───────────────────────────────────────────
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            val windowTitle = event.text.firstOrNull()?.toString() ?: ""

            Log.d(
                TAG,
                "[WINDOW] pkg=$eventPkg  class=$className  title=\"$windowTitle\"  " +
                "supported=$isEventPkgSupported  wasWatching=$isCurrentlyWatchingReels"
            )

            if (!isEventPkgSupported) {
                // Foreground changed to a non-social app — immediately hide overlay
                if (isCurrentlyWatchingReels || youtubeShortsSessionActive || currentPackage != eventPkg) {
                    Log.d(TAG, "[WINDOW] Non-social foreground → force hide overlay")
                    currentPackage = eventPkg
                    endReelSession(eventPkg, "", "non-social foreground")
                } else {
                    currentPackage = eventPkg
                }
                return
            }

            val appName = AppRecognitionEngine.getAppName(eventPkg)
            currentPackage = eventPkg
            // A Shorts-shaped window class is enough to re-open a session that YouTube's
            // asynchronous rendering made the tree scan miss.
            if (eventPkg == YOUTUBE_PACKAGE &&
                className.lowercase().contains("shorts")
            ) {
                confirmYouTubeShortsSession("window class $className")
            }
            evaluateScreenContext(eventPkg, appName, event, force = true)
            return
        }

        // ── Events below only matter if package is supported ──────────────
        if (!isEventPkgSupported) return

        val appName = AppRecognitionEngine.getAppName(eventPkg)
        currentPackage = eventPkg

        // Instant click detection
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            handleViewClicked(eventPkg, appName, event)
            // Only schedule re-evaluation if we're in a reel context already
            if (isCurrentlyWatchingReels) {
                scheduleEvaluation(eventPkg, appName)
            }
            return
        }

        // Scroll event
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            handleScroll(eventPkg, appName, event)
            return
        }

        // Content updates — throttled
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val now = System.currentTimeMillis()
            if (now - lastEvalTimestamp >= THROTTLE_EVAL_MS) {
                evaluateScreenContext(eventPkg, appName, event, force = false)
            } else {
                scheduleEvaluation(eventPkg, appName)
            }
        }
    }

    /**
     * Resets all tracking state — call this on service interrupt or destroy.
     */
    fun resetState() {
        pendingEvalRunnable?.let { handler.removeCallbacks(it) }
        pendingEvalRunnable = null
        cancelYouTubeTreeRecovery()
        if (isCurrentlyWatchingReels) {
            val pkg = currentPackage ?: ""
            Log.d(TAG, "[RESET] Forcing reel-inactive due to resetState() for pkg=$pkg")
            isCurrentlyWatchingReels = false
            cachedIsReelContext = false
            onReelVisibilityChanged?.invoke(pkg, "", false)
        }
        isCurrentlyWatchingReels = false
        cachedIsReelContext = false
        currentPackage = null
        lastScrollTimestamp = 0L
        lastEvalTimestamp = 0L
        instagramCommentSheetOpenedAt = 0L
        lastInstagramEventTimestamp = 0L
        lastYouTubePagerEventTimestamp = 0L
        youtubeShortsSessionActive = false
        youtubeTreeMissRetries = 0
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun setReelInactive(pkg: String, appName: String) {
        isCurrentlyWatchingReels = false
        cachedIsReelContext = false
        onReelVisibilityChanged?.invoke(pkg, appName, false)
        Log.d(TAG, "[REEL] pkg=$pkg  active=false")
    }

    /**
     * Ends the reel session for [pkg]: hides the pill and clears every per-app sticky state, so
     * the next swipe has to re-confirm the viewer. Used by all explicit exits (leaving the app,
     * screen off, exit tap, a confirmed non-reel screen).
     */
    private fun endReelSession(pkg: String, appName: String, reason: String) {
        val wasActive = isCurrentlyWatchingReels || youtubeShortsSessionActive
        cancelYouTubeTreeRecovery()
        youtubeShortsSessionActive = false
        youtubeTreeMissRetries = 0
        cachedIsReelContext = false
        if (wasActive) {
            Log.d(TAG, "[SESSION] Ending reel session for $pkg — $reason")
            setReelInactive(pkg, appName)
        }
    }

    /**
     * Opens (or refreshes) the YouTube Shorts session and makes sure the pill is on screen.
     * Every counting path funnels through here, which is what keeps counting alive while YouTube
     * is mid-transition and its view tree is temporarily empty.
     */
    private fun confirmYouTubeShortsSession(reason: String) {
        if (!youtubeShortsSessionActive) {
            Log.d(TAG, "[YT] Shorts session opened — $reason")
        }
        youtubeShortsSessionActive = true
        youtubeTreeMissRetries = 0
        cachedIsReelContext = true
        if (!isCurrentlyWatchingReels) {
            isCurrentlyWatchingReels = true
            val appName = AppRecognitionEngine.getAppName(YOUTUBE_PACKAGE)
            onReelVisibilityChanged?.invoke(YOUTUBE_PACKAGE, appName, true)
            Log.d(TAG, "[REEL] pkg=$YOUTUBE_PACKAGE  active=true  (via $reason)")
        }
    }

    private fun cancelYouTubeTreeRecovery() {
        pendingYouTubeRecoveryRunnable?.let { handler.removeCallbacks(it) }
        pendingYouTubeRecoveryRunnable = null
    }

    /**
     * YouTube renders the Shorts pager asynchronously. A negative tree scan while a session is
     * open is therefore usually a transient miss, not an exit. It is only treated as an exit on
     * a window change (a real navigation inside the app), where it gets a small retry budget
     * first. Content-update scans never end the session.
     *
     * Returns true when the counter must stay on screen.
     */
    private fun recoverYouTubeTreeMiss(packageName: String, appName: String, fromWindowChange: Boolean): Boolean {
        if (packageName != YOUTUBE_PACKAGE || !youtubeShortsSessionActive) return false

        if (!fromWindowChange) {
            cachedIsReelContext = true
            Log.d(TAG, "[YT] Shorts session kept through a content-update tree miss")
            return true
        }

        if (youtubeTreeMissRetries >= YOUTUBE_MAX_TREE_MISSES) {
            Log.d(TAG, "[YT] Tree scan negative $youtubeTreeMissRetries times — Shorts really exited")
            endReelSession(packageName, appName, "tree negative $YOUTUBE_MAX_TREE_MISSES times in a row")
            return false
        }

        youtubeTreeMissRetries++
        Log.d(TAG, "[YT] Shorts tree miss after a window change — re-check " +
                "$youtubeTreeMissRetries/$YOUTUBE_MAX_TREE_MISSES")
        cancelYouTubeTreeRecovery()
        val attempt = youtubeTreeMissRetries
        pendingYouTubeRecoveryRunnable = Runnable {
            pendingYouTubeRecoveryRunnable = null
            if (youtubeShortsSessionActive) {
                if (checkNodeTreeStrictlyForReels(YOUTUBE_PACKAGE)) {
                    confirmYouTubeShortsSession("tree re-check #$attempt")
                } else {
                    recoverYouTubeTreeMiss(YOUTUBE_PACKAGE, appName, fromWindowChange = true)
                }
            }
        }
        handler.postDelayed(pendingYouTubeRecoveryRunnable!!, YOUTUBE_TREE_RETRY_DELAY_MS)
        return true
    }

    /**
     * Instagram can temporarily omit its Reels viewer nodes during UI updates. Once a
     * Reels tab/click has confirmed the context, an inconclusive tree check must not
     * dismiss the counter; explicit exit clicks and negative-screen checks still do.
     * YouTube uses the same idea through [recoverYouTubeTreeMiss], with a retry budget that is
     * only spent on window changes.
     */
    private fun retainReelContextOnTransientMiss(
        packageName: String,
        appName: String,
        fromWindowChange: Boolean
    ): Boolean {
        if (!isCurrentlyWatchingReels) return false

        if (packageName.contains("instagram")) {
            cachedIsReelContext = true
            Log.d(TAG, "[REEL] pkg=$packageName  retaining active state during transient tree miss")
            return true
        }

        if (packageName == YOUTUBE_PACKAGE) {
            return recoverYouTubeTreeMiss(packageName, appName, fromWindowChange)
        }

        return false
    }

    private fun scheduleEvaluation(activePkg: String, appName: String) {
        pendingEvalRunnable?.let { handler.removeCallbacks(it) }
        pendingEvalRunnable = Runnable {
            // Re-evaluate reel context using the live node tree — no dummy AccessibilityEvent needed
            val isReel = checkNodeTreeStrictlyForReels(activePkg)
            if (isReel) {
                if (activePkg == YOUTUBE_PACKAGE) {
                    confirmYouTubeShortsSession("scheduled tree check")
                } else if (!isCurrentlyWatchingReels) {
                    isCurrentlyWatchingReels = true
                    cachedIsReelContext = true
                    onReelVisibilityChanged?.invoke(activePkg, appName, true)
                    Log.d(TAG, "[REEL] pkg=$activePkg  active=true  (via scheduled eval)")
                }
            } else if (isCurrentlyWatchingReels &&
                !retainReelContextOnTransientMiss(activePkg, appName, fromWindowChange = false)
            ) {
                setReelInactive(activePkg, appName)
            }
        }
        handler.postDelayed(pendingEvalRunnable!!, 300L)
    }

    private fun handleViewClicked(packageName: String, appName: String, event: AccessibilityEvent) {
        val textList    = event.text.map { it.toString().lowercase() }
        val contentDesc = event.contentDescription?.toString()?.lowercase() ?: ""
        val clickedText = (textList + listOf(contentDesc)).joinToString(" ")
        // Read and immediately recycle the source node to prevent AccessibilityNodeInfo leaks
        val resId = event.source?.let { src ->
            val id = src.viewIdResourceName?.lowercase() ?: ""
            src.recycle()
            id
        } ?: ""

        // Instagram emits a programmatic TYPE_VIEW_SCROLLED while its comment sheet
        // animates in. Mark the click before any reel-navigation classification so that
        // those animation events cannot be mistaken for reel swipes.
        val isInstagramCommentClick = packageName.contains("instagram") &&
                (clickedText.contains("comment") || resId.contains("comment"))
        if (isInstagramCommentClick) {
            instagramCommentSheetOpenedAt = System.currentTimeMillis()
            Log.d(TAG, "[CLICK] Instagram comment sheet tap detected; suppressing scrolls briefly")
        }

        // Clicked into Reels or Shorts tab
        val isReelClick = clickedText.contains("reels") ||
                clickedText.contains("shorts") ||
                clickedText.contains("spotlight") ||
                resId.contains("reels") ||
                resId.contains("clips") ||
                resId.contains("shorts")

        if (isReelClick) {
            Log.d(TAG, "[CLICK] Reel/Shorts tap detected in $packageName")
            cachedIsReelContext = true
            if (packageName == YOUTUBE_PACKAGE) {
                confirmYouTubeShortsSession("Shorts tab tap")
            } else if (!isCurrentlyWatchingReels) {
                isCurrentlyWatchingReels = true
                onReelVisibilityChanged?.invoke(packageName, appName, true)
                Log.d(TAG, "[REEL] pkg=$packageName  active=true  (via click)")
            }
            return
        }

        // Clicked away from Reels (Home, Search, Profile, DMs, etc.)
        val isExitClick = clickedText.contains("home") ||
                clickedText.contains("search") ||
                clickedText.contains("explore") ||
                clickedText.contains("friends") ||
                clickedText.contains("notifications") ||
                clickedText.contains("profile") ||
                clickedText.contains("direct") ||
                clickedText.contains("messages") ||
                clickedText.contains("subscriptions") ||
                clickedText.contains("library") ||
                clickedText.contains("you") ||
                clickedText.contains("close") ||
                clickedText.contains("back") ||
                resId.contains("home_tab") ||
                resId.contains("search_tab") ||
                resId.contains("profile_tab") ||
                resId.contains("action_bar_button_back")

        if (isExitClick) {
            Log.d(TAG, "[CLICK] Exit-reel tap detected in $packageName  resId=$resId")
            endReelSession(packageName, appName, "exit tap (resId=$resId)")
        }
    }

    private fun handleScroll(packageName: String, appName: String, event: AccessibilityEvent) {
        val now = System.currentTimeMillis()

        if (packageName == YOUTUBE_PACKAGE) {
            if (handleYouTubeShortsScroll(appName, event, now)) return
            // Sibling scroll event emitted during the same Shorts swipe: never count it twice.
            if (now - lastYouTubePagerEventTimestamp < YOUTUBE_SWIPE_BURST_GAP_MS) return
        }

        // Reject comment-sheet events before debounce, context evaluation, or state
        // changes. The reel pager remains in the tree behind this sheet, so those
        // later checks alone cannot distinguish a comment RecyclerView from a reel.
        if (packageName == INSTAGRAM_PACKAGE) {
            if (now - instagramCommentSheetOpenedAt < INSTAGRAM_COMMENT_SHEET_SUPPRESSION_MS) {
                Log.d(TAG, "[SCROLL] Rejected — Instagram comment sheet is animating")
                return
            }

            if (isNodeInsideCommentContainer(event.source)) {
                Log.d(TAG, "[SCROLL] Rejected — inside Instagram comment sheet")
                return
            }

            // Never infer Reels merely from a generic Instagram scroll. The user must
            // already have entered the Reels viewer via its tab/node context.
            if (!isCurrentlyWatchingReels || !cachedIsReelContext) {
                Log.d(TAG, "[SCROLL] Rejected — Instagram is not in confirmed Reels view")
                return
            }

            if (!isCompletedInstagramForwardSwipe(event, now)) return
            if (now - lastScrollTimestamp < INSTAGRAM_SWIPE_COOLDOWN_MS) {
                Log.d(TAG, "[SCROLL] Rejected — Instagram Reel snap is in cooldown")
                return
            }
        } else if (now - lastScrollTimestamp < MIN_SCROLL_INTERVAL_MS) {
            return
        }

        // Reject horizontal swiping. YouTube Shorts can report the upward swipe to the next
        // short as a negative scroll delta, which the per-app check below accepts.
        if (!isStrictlyVerticalScroll(event, packageName)) {
            Log.d(TAG, "[SCROLL] Rejected horizontal scroll in $packageName")
            return
        }

        // Quick negative check
        val className = event.className?.toString() ?: ""
        if (isUnambiguousNegativeScreen(packageName, className, event)) {
            endReelSession(packageName, appName, "negative screen on swipe")
            return
        }

        // Confirm reel context
        val treeSaysReel = if (isTikTok(packageName)) {
            true
        } else {
            cachedIsReelContext || isPositiveReelScreen(packageName, className, event)
        }

        if (!treeSaysReel) {
            if (packageName == YOUTUBE_PACKAGE) {
                Log.d(TAG, "[SCROLL] YouTube swipe not counted — no Shorts evidence yet " +
                        "(session=$youtubeShortsSessionActive, cached=$cachedIsReelContext)")
            }
            return
        }

        if (packageName == YOUTUBE_PACKAGE && !youtubeShortsSessionActive) {
            confirmYouTubeShortsSession("generic swipe + tree check")
        }

        lastScrollTimestamp = now
        if (!isCurrentlyWatchingReels) {
            isCurrentlyWatchingReels = true
            onReelVisibilityChanged?.invoke(packageName, appName, true)
            Log.d(TAG, "[REEL] pkg=$packageName  active=true  (via scroll)")
        }

        Log.d(TAG, "[SCROLL] Reel swipe counted for $appName ($packageName)")
        onScrollDetected(packageName, appName)
    }

    /**
     * YouTube Shorts swipe detection.
     *
     * Three independent evidence sources are combined (see [YouTubeShortsRules]): the view id of
     * the scroll source, the source's geometry, and the sticky Shorts session. Any one of them is
     * enough once it is corroborated by vertical movement, so counting keeps working when YouTube
     * renames a view, ships views without ids, or returns no source node at all — the three ways
     * this detector has failed on real devices.
     *
     * Events for a single swipe arrive as a burst and are collapsed by the burst gap plus a
     * per-swipe cooldown.
     *
     * @return true when the event was decided here (counted, or swallowed as a duplicate, a
     *         jitter, a sideways move, or a comment-sheet scroll); false to let the generic path
     *         run the window tree check.
     */
    private fun handleYouTubeShortsScroll(appName: String, event: AccessibilityEvent, now: Long): Boolean {
        val (screenWidth, screenHeight) = screenMetrics()
        val source = try { event.source } catch (_: Exception) { null }

        var viewId = ""
        var className = event.className?.toString()?.lowercase() ?: ""
        var sourceScrollable = false
        var widthFraction = YouTubeScrollSignals.UNKNOWN_GEOMETRY
        var heightFraction = YouTubeScrollSignals.UNKNOWN_GEOMETRY
        var fullScreenVideoSurface = false

        if (source != null) {
            try {
                viewId = try { source.viewIdResourceName?.lowercase() ?: "" } catch (_: Exception) { "" }
                val sourceClass = try { source.className?.toString()?.lowercase() ?: "" } catch (_: Exception) { "" }
                if (sourceClass.isNotEmpty()) className = sourceClass
                sourceScrollable = try { source.isScrollable } catch (_: Exception) { false }

                if (screenWidth > 0 && screenHeight > 0) {
                    val rect = Rect()
                    try { source.getBoundsInScreen(rect) } catch (_: Exception) {}
                    if (rect.width() > 0 && rect.height() > 0) {
                        widthFraction = rect.width().toFloat() / screenWidth.toFloat()
                        heightFraction = rect.height().toFloat() / screenHeight.toFloat()
                    }
                }

                // The surface scan walks the subtree, so it only runs when it can actually be the
                // tie-breaker: no decisive id and no session open yet.
                if (!YouTubeShortsRules.isShortsPagerMarker(viewId) && !youtubeShortsSessionActive) {
                    fullScreenVideoSurface = containsFullScreenVideoSurface(source, screenHeight)
                }
            } catch (e: Exception) {
                Log.w(TAG, "[YT] Could not read the scroll source node", e)
            } finally {
                try { source.recycle() } catch (_: Exception) {}
            }
        }

        // A scroll owned by a comment sheet, chip bar, or panel is never a Shorts swipe, and the
        // pager is still alive in the tree behind it — so the generic path must not reconsider it.
        if (YouTubeShortsRules.isNonPagerViewId(viewId)) {
            Log.d(TAG, "[YT-SCROLL] Swallowed — scroll owned by a YouTube panel/sheet: src=$viewId")
            return true
        }

        val hasDeltas = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val deltaX = if (hasDeltas) event.scrollDeltaX else YouTubeScrollSignals.NO_DELTA
        val deltaY = if (hasDeltas) event.scrollDeltaY else YouTubeScrollSignals.NO_DELTA

        val signals = YouTubeScrollSignals(
            sourceViewId = viewId,
            sourceClassName = className,
            sourceIsScrollable = sourceScrollable,
            sourceWidthFraction = widthFraction,
            sourceHeightFraction = heightFraction,
            deltaX = deltaX,
            deltaY = deltaY,
            hasDirectionalDeltas = hasDeltas &&
                    !(deltaX == YouTubeScrollSignals.NO_DELTA && deltaY == YouTubeScrollSignals.NO_DELTA),
            fullScreenVideoSurface = fullScreenVideoSurface,
            shortsSessionConfirmed = youtubeShortsSessionActive,
            msSinceLastCountedSwipe = if (lastScrollTimestamp == 0L) Long.MAX_VALUE else now - lastScrollTimestamp,
            msSinceLastPagerEvent = if (lastYouTubePagerEventTimestamp == 0L) Long.MAX_VALUE else now - lastYouTubePagerEventTimestamp
        )

        val verdict = YouTubeShortsRules.classify(signals, screenHeight)

        return when (verdict) {
            YouTubeSwipeVerdict.COUNT -> {
                lastYouTubePagerEventTimestamp = now
                lastScrollTimestamp = now
                confirmYouTubeShortsSession("Shorts swipe")
                Log.d(TAG, "[SCROLL] YouTube Shorts swipe counted")
                onScrollDetected(YOUTUBE_PACKAGE, appName)
                true
            }

            YouTubeSwipeVerdict.DUPLICATE_BURST_EVENT,
            YouTubeSwipeVerdict.COOLDOWN -> {
                // Keep extending the burst window so a long settle cannot leak a second count.
                lastYouTubePagerEventTimestamp = now
                true
            }

            YouTubeSwipeVerdict.HORIZONTAL,
            YouTubeSwipeVerdict.MOVEMENT_TOO_SMALL -> {
                Log.d(TAG, "[YT-SCROLL] Swallowed — $verdict dx=$deltaX dy=$deltaY")
                true
            }

            YouTubeSwipeVerdict.NOT_SHORTS_PAGER,
            YouTubeSwipeVerdict.NEEDS_SHORTS_CONFIRMATION -> {
                val heightLog = if (heightFraction < 0f) "unknown" else "${(heightFraction * 100).toInt()}%"
                Log.d(TAG, "[YT-SCROLL] Inconclusive — $verdict src=${viewId.ifEmpty { "none" }} " +
                        "class=$className scrollable=$sourceScrollable height=$heightLog dy=$deltaY " +
                        "surface=$fullScreenVideoSurface session=$youtubeShortsSessionActive")
                if (!youtubeShortsSessionActive) {
                    // Let the async re-check open the session so the next swipe counts even if
                    // this window scan is empty right now.
                    scheduleEvaluation(YOUTUBE_PACKAGE, appName)
                }
                false
            }
        }
    }

    /**
     * Bounded scan of the scroll source's subtree for a video surface that fills the screen.
     * That is what separates the Shorts pager from YouTube's portrait watch page, where the
     * player occupies only the top part of the screen.
     */
    private fun containsFullScreenVideoSurface(node: AccessibilityNodeInfo, screenHeightPx: Int): Boolean {
        if (screenHeightPx <= 0) return false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_YOUTUBE_SURFACE_SCAN_NODES) {
            val current = queue.removeFirst()
            visited++

            val className = try { current.className?.toString()?.lowercase() ?: "" } catch (_: Exception) { "" }
            val viewId = try { current.viewIdResourceName?.lowercase() ?: "" } catch (_: Exception) { "" }
            val looksLikeVideoSurface =
                YouTubeShortsRules.VIDEO_SURFACE_MARKERS.any { className.contains(it) } ||
                        YouTubeShortsRules.VIDEO_SURFACE_MARKERS.any { viewId.contains(it) }

            if (looksLikeVideoSurface) {
                val rect = Rect()
                try { current.getBoundsInScreen(rect) } catch (_: Exception) {}
                if (rect.height() >= screenHeightPx * YouTubeShortsRules.MIN_SURFACE_HEIGHT_FRACTION &&
                    rect.width() > 0
                ) {
                    return true
                }
            }

            val childCount = try { current.childCount } catch (_: Exception) { 0 }
            for (i in 0 until childCount) {
                try { current.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
            }
        }
        return false
    }

    /** Screen size in px, used to turn node bounds into fractions. `0` when unknown. */
    private fun screenMetrics(): Pair<Int, Int> {
        return try {
            val metrics = service?.resources?.displayMetrics
                ?: android.content.res.Resources.getSystem().displayMetrics
            (metrics?.widthPixels ?: 0) to (metrics?.heightPixels ?: 0)
        } catch (e: Exception) {
            Log.w(TAG, "[SCREEN] Could not read display metrics", e)
            0 to 0
        }
    }

    /**
     * Returns true when a scroll event source belongs to Instagram's comment-sheet
     * hierarchy. This walks from the source toward the root rather than searching the
     * entire active window, which keeps the hot scroll path both fast and reliable.
     *
     * This method takes ownership of [sourceNode] and every parent it obtains; all are
     * recycled before it returns.
     */
    private fun isNodeInsideCommentContainer(sourceNode: AccessibilityNodeInfo?): Boolean {
        var node: AccessibilityNodeInfo? = sourceNode

        try {
            repeat(MAX_COMMENT_CONTAINER_ANCESTOR_DEPTH) {
                val current = node ?: return false
                val viewId = current.viewIdResourceName?.lowercase() ?: ""
                val className = current.className?.toString()?.lowercase() ?: ""
                val contentDescription = current.contentDescription?.toString()?.lowercase() ?: ""

                val isCommentContainer = viewId.contains("comment_bottom_sheet") ||
                        viewId.contains("comments_recycler_view") ||
                        viewId.contains("igds_bottom_sheet_root") ||
                        viewId.contains("comments_fragment") ||
                        viewId.contains("comment_text_input") ||
                        className.contains("comment") ||
                        className.contains("bottomsheet") ||
                        contentDescription.contains("comments")

                if (isCommentContainer) return true

                val parent = try {
                    current.parent
                } catch (_: Exception) {
                    null
                }
                try {
                    current.recycle()
                } catch (_: Exception) {
                }
                node = parent
                if (node == null) return false
            }
            return false
        } finally {
            try {
                node?.recycle()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Instagram-only completed-snap filter. A positive delta is the pager direction used
     * by Instagram when advancing to the next Reel. Negative deltas (previous Reel), tiny
     * content shifts/taps, and slow sheet movement are ignored.
     *
     * Accessibility events do not expose MotionEvent velocity. The velocity below is the
     * passive equivalent: pager distance divided by time since the previous emitted scroll
     * frame. No touch listener is installed and no gesture is intercepted.
     */
    private fun isCompletedInstagramForwardSwipe(event: AccessibilityEvent, now: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.d(TAG, "[SCROLL] Rejected — Instagram event has no directional scroll delta")
            return false
        }

        val deltaX = event.scrollDeltaX
        val deltaY = event.scrollDeltaY
        val eventTimestamp = event.eventTime.takeIf { it > 0L } ?: now
        val frameDuration = if (lastInstagramEventTimestamp > 0L) {
            (eventTimestamp - lastInstagramEventTimestamp).coerceIn(1L, 250L)
        } else {
            // The first accessibility frame is already a delta, so use one display frame.
            16L
        }
        lastInstagramEventTimestamp = eventTimestamp

        val velocity = deltaY.toFloat() / frameDuration.toFloat()
        val accepted = deltaY >= INSTAGRAM_MIN_VERTICAL_DISTANCE_PX &&
                abs(deltaX) < deltaY &&
                velocity >= INSTAGRAM_MIN_VELOCITY_PX_PER_MS

        if (!accepted) {
            Log.d(TAG, "[SCROLL] Rejected Instagram motion — dx=$deltaX dy=$deltaY velocity=$velocity")
        }
        return accepted
    }

    private fun isStrictlyVerticalScroll(event: AccessibilityEvent, packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val deltaX = event.scrollDeltaX
            val deltaY = event.scrollDeltaY

            if (deltaX != -1 && deltaY != -1) {
                if (deltaX != 0 && deltaY == 0) return false
                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 2) return false
                if (deltaY > 0) return true
                // YouTube Shorts advances to the next short with an upward swipe, which
                // its view pager reports as a negative delta. Keep all other apps' rule.
                if (deltaY < 0) return packageName == "com.google.android.youtube"
            }
        }

        val className = event.className?.toString()?.lowercase() ?: ""
        if (className.contains("horizontal") ||
            className.contains("tablayout") ||
            className.contains("tabbar") ||
            className.contains("carousel") ||
            className.contains("storytray")
        ) {
            return false
        }

        return true
    }

    private fun evaluateScreenContext(
        packageName: String,
        appName: String,
        event: AccessibilityEvent,
        force: Boolean
    ) {
        val now = System.currentTimeMillis()
        lastEvalTimestamp = now

        val className = event.className?.toString() ?: ""

        // 1. Strict Negative Check
        if (isUnambiguousNegativeScreen(packageName, className, event)) {
            if (packageName == YOUTUBE_PACKAGE && youtubeShortsSessionActive && !force) {
                // Content updates fire constantly inside Shorts and can carry the bottom-tab
                // labels; only a real window change may end the session from here.
                Log.d(TAG, "[YT] Negative keywords during a content update — session kept")
                return
            }
            Log.d(TAG, "[EVAL] Negative screen for $packageName  class=$className")
            endReelSession(packageName, appName, "negative screen")
            return
        }

        // 2. Strict Positive Check
        val isReel = isPositiveReelScreen(packageName, className, event)

        if (isReel) {
            if (packageName == YOUTUBE_PACKAGE) {
                confirmYouTubeShortsSession(if (force) "window scan" else "content scan")
            } else {
                cachedIsReelContext = true
                if (!isCurrentlyWatchingReels) {
                    isCurrentlyWatchingReels = true
                    onReelVisibilityChanged?.invoke(packageName, appName, true)
                    Log.d(TAG, "[REEL] pkg=$packageName  active=true  (via evaluation)")
                }
            }
        } else if (!retainReelContextOnTransientMiss(packageName, appName, fromWindowChange = force)) {
            cachedIsReelContext = false
            if (isCurrentlyWatchingReels) {
                setReelInactive(packageName, appName)
            }
        }
    }

    private fun isUnambiguousNegativeScreen(packageName: String, className: String, event: AccessibilityEvent): Boolean {
        val lowerClass = className.lowercase()
        val textList   = event.text.map { it.toString().lowercase() }
        val allText    = (textList + listOfNotNull(event.contentDescription?.toString()?.lowercase())).joinToString(" ")

        when {
            packageName.contains("facebook") -> {
                if (allText.contains("what's on your mind") ||
                    allText.contains("write a comment") ||
                    allText.contains("news feed") ||
                    allText.contains("marketplace") ||
                    allText.contains("groups") ||
                    allText.contains("friends, tab") ||
                    allText.contains("notifications") ||
                    allText.contains("menu") ||
                    lowerClass.contains("newsfeed") ||
                    lowerClass.contains("feedunit") ||
                    lowerClass.contains("storytray") ||
                    lowerClass.contains("marketplace") ||
                    lowerClass.contains("settingsactivity")
                ) {
                    return true
                }
            }

            packageName.contains("instagram") -> {
                if (allText.contains("your story") ||
                    allText.contains("add a comment") ||
                    allText.contains("direct messages") ||
                    allText.contains("search and explore") ||
                    // Comment sheet indicators
                    allText.contains("comments") ||
                    allText.contains("reply") ||
                    lowerClass.contains("directinbox") ||
                    lowerClass.contains("directthread") ||
                    lowerClass.contains("feedfragment") ||
                    lowerClass.contains("profilefragment") ||
                    lowerClass.contains("editprofileactivity") ||
                    lowerClass.contains("comment") ||
                    lowerClass.contains("bottomsheet")
                ) {
                    return true
                }
            }

            packageName.contains("youtube") -> {
                if ((allText.contains("home") && allText.contains("subscriptions") && !allText.contains("shorts")) ||
                    allText.contains("add a comment") ||
                    lowerClass.contains("homefragment") ||
                    lowerClass.contains("browsefragment") ||
                    lowerClass.contains("settingsactivity") ||
                    lowerClass.contains("searchactivity")
                ) {
                    return true
                }
            }

            isTikTok(packageName) -> {
                if (lowerClass.contains("chatroom") ||
                    lowerClass.contains("imactivity") ||
                    lowerClass.contains("userprofile") ||
                    lowerClass.contains("profileactivity") ||
                    allText.contains("edit profile") ||
                    allText.contains("direct messages")
                ) {
                    return true
                }
            }

            packageName.contains("spotify") -> {
                if (allText.contains("your library") ||
                    allText.contains("playlists") ||
                    allText.contains("artists") ||
                    allText.contains("album") ||
                    lowerClass.contains("tracklist") ||
                    lowerClass.contains("playlistfragment") ||
                    lowerClass.contains("libraryfragment")
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun isPositiveReelScreen(packageName: String, className: String, event: AccessibilityEvent): Boolean {
        return when {
            packageName.contains("facebook")  -> checkNodeTreeStrictlyForReels(packageName)
            packageName.contains("instagram") -> checkNodeTreeStrictlyForReels(packageName)
            packageName.contains("youtube")   -> checkNodeTreeStrictlyForReels(packageName)
            isTikTok(packageName)             -> checkNodeTreeStrictlyForReels(packageName)
            packageName.contains("spotify")   -> checkNodeTreeStrictlyForReels(packageName)
            else -> false
        }
    }

    private fun isTikTok(pkg: String): Boolean {
        return pkg.contains("musically") || pkg.contains("ugc.trill") || pkg.contains("ugc.aweme")
    }

    /**
     * Inspects active accessibility node hierarchy using the Scrolless detection model.
     */
    fun checkNodeTreeStrictlyForReels(packageName: String): Boolean {
        val serviceInstance = service ?: return false
        val root = getActiveAppRootNode(serviceInstance) ?: return false

        return try {
            val rootBounds = Rect().also(root::getBoundsInScreen)
            when {
                packageName.contains("facebook.katana") ->
                    isFacebookKatanaReelsActive(root, rootBounds)

                packageName.contains("facebook.lite") ->
                    hasVisibleViewId(root, "com.facebook.lite:id/video_view")

                packageName.contains("instagram") ->
                    isInstagramReelsActive(root, rootBounds)

                packageName.contains("youtube") ->
                    isYouTubeShortsActive(root, packageName)

                isTikTok(packageName) ->
                    isTikTokActive(root, packageName)

                packageName.contains("snapchat") ->
                    hasVisibleViewId(root, "com.snapchat.android:id/spotlight_container")

                else -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "[NODE_TREE] Error checking node tree for $packageName", e)
            false
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
    }

    private fun getActiveAppRootNode(service: AccessibilityService): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val windows = service.windows
                if (!windows.isNullOrEmpty()) {
                    val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    val foreground = appWindows.firstOrNull { it.isFocused } ?: appWindows.firstOrNull { it.isActive }
                    val root = foreground?.root
                    if (root != null) return root
                }
            } catch (_: Exception) {}
        }
        return try { service.rootInActiveWindow } catch (_: Exception) { null }
    }

    private fun isNodeVisibleToTheUser(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return node.isVisibleToUser && rect.width() > 0 && rect.height() > 0
    }

    private fun hasVisibleViewId(root: AccessibilityNodeInfo, fullyQualifiedId: String): Boolean {
        return try {
            val nodes = root.findAccessibilityNodeInfosByViewId(fullyQualifiedId)
            nodes.isNotEmpty() && nodes.any(::isNodeVisibleToTheUser)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Facebook Katana detection:
     * 1. Rejects if "What's on your mind?" is visible (News Feed).
     * 2. Rejects if Home or Friends tab is selected.
     * 3. Checks composer specs (FbShortsComposerAttachmentComponentSpec_STICKER).
     * 4. Checks selected Reels navigation tab.
     * 5. Checks top header text "Reels".
     * 6. Fallback structural: RecyclerView (scrollable) → Button (long-clickable) → SurfaceView.
     */
    private fun isFacebookKatanaReelsActive(root: AccessibilityNodeInfo, rootBounds: Rect): Boolean {
        // Fast negative: News Feed status input
        try {
            val newsFeedNodes = root.findAccessibilityNodeInfosByText("What's on your mind?")
            if (newsFeedNodes.isNotEmpty() && newsFeedNodes.any(::isNodeVisibleToTheUser)) return false
        } catch (_: Exception) {}

        // Fast positive: Composer stickers
        try {
            val stickerNodes = root.findAccessibilityNodeInfosByText("FbShortsComposerAttachmentComponentSpec_STICKER")
            if (stickerNodes.isNotEmpty() && stickerNodes.any(::isNodeVisibleToTheUser)) return true
            val gifNodes = root.findAccessibilityNodeInfosByText("FbShortsComposerAttachmentComponentSpec_GIF")
            if (gifNodes.isNotEmpty() && gifNodes.any(::isNodeVisibleToTheUser)) return true
        } catch (_: Exception) {}

        // BFS traversal for tabs, headers, and node structure
        var isHomeTabSelected    = false
        var isFriendsTabSelected = false
        var isReelsTabSelected   = false
        var hasReelsHeader       = false

        val structuralNodes = mutableListOf<DetectionNode>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int?>>()
        queue.add(root to null)
        var visited = 0
        var nextId = 0
        val screenWidth  = rootBounds.width().coerceAtLeast(1)
        val screenHeight = rootBounds.height().coerceAtLeast(1)

        while (queue.isNotEmpty() && visited < 400) {
            val (node, parentId) = queue.removeFirst()
            visited++
            val isVisible = isNodeVisibleToTheUser(node)
            var currentStructuralId: Int? = null

            if (isVisible) {
                val desc       = node.contentDescription?.toString()?.lowercase() ?: ""
                val text       = node.text?.toString()?.lowercase() ?: ""
                val isSelected = node.isSelected
                val nodeRect   = Rect().also(node::getBoundsInScreen)

                if (isSelected) {
                    if (desc.contains("home") || desc.contains("news feed") ||
                        text.contains("home") || text.contains("news feed")) {
                        isHomeTabSelected = true
                    }
                    if (desc.contains("friends") || desc.contains("menu") || desc.contains("notifications") ||
                        text.contains("friends") || text.contains("menu") || text.contains("notifications")) {
                        isFriendsTabSelected = true
                    }
                }

                if (isSelected && (
                    desc.contains("reels") || desc.contains("video") || desc.contains("watch") ||
                    text.contains("reels") || text.contains("video") || text.contains("watch")
                )) {
                    isReelsTabSelected = true
                }

                if (text.equals("reels", ignoreCase = true) && nodeRect.top < screenHeight * 0.25f) {
                    hasReelsHeader = true
                }

                val className = node.className?.toString() ?: ""
                if (className == "androidx.recyclerview.widget.RecyclerView" ||
                    className == "android.widget.Button" ||
                    className == "android.view.SurfaceView"
                ) {
                    val id = nextId++
                    currentStructuralId = id
                    structuralNodes.add(
                        DetectionNode(
                            nodeId = id,
                            parentNodeId = parentId,
                            className = className,
                            screenWidthFraction  = (nodeRect.width().toFloat()  / screenWidth).coerceIn(0f, 1f),
                            screenHeightFraction = (nodeRect.height().toFloat() / screenHeight).coerceIn(0f, 1f),
                            isScrollable      = node.isScrollable,
                            isLongClickable   = node.isLongClickable
                        )
                    )
                }
            }

            val childParentId = currentStructuralId ?: parentId
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it to childParentId) }
            }
        }

        if (isHomeTabSelected || isFriendsTabSelected) return false
        if (isReelsTabSelected || hasReelsHeader) return true

        // Scrolless fallback structural check
        val childrenByParent = structuralNodes.groupBy { it.parentNodeId }
        return structuralNodes.any { node ->
            node.className == "androidx.recyclerview.widget.RecyclerView" &&
            node.screenWidthFraction  >= 0.9f &&
            node.screenHeightFraction >= 0.75f &&
            node.isScrollable &&
            hasMatchingButtonAndSurfaceView(node.nodeId, childrenByParent)
        }
    }

    private fun hasMatchingButtonAndSurfaceView(parentId: Int, childrenByParent: Map<Int?, List<DetectionNode>>): Boolean {
        val children = childrenByParent[parentId].orEmpty()
        return children.any { child ->
            (child.className == "android.widget.Button" &&
             child.screenWidthFraction  >= 0.9f &&
             child.screenHeightFraction >= 0.75f &&
             child.isLongClickable &&
             hasMatchingSurfaceView(child.nodeId, childrenByParent)) ||
            hasMatchingButtonAndSurfaceView(child.nodeId, childrenByParent)
        }
    }

    private fun hasMatchingSurfaceView(parentId: Int, childrenByParent: Map<Int?, List<DetectionNode>>): Boolean {
        val children = childrenByParent[parentId].orEmpty()
        return children.any { child ->
            (child.className == "android.view.SurfaceView" &&
             child.screenWidthFraction  >= 0.9f &&
             child.screenHeightFraction >= 0.75f) ||
            hasMatchingSurfaceView(child.nodeId, childrenByParent)
        }
    }

    /**
     * Instagram Reels detection:
     * 1. Fast-negative: comment bottom sheet is open — never count in this state.
     * 2. View ID clips_viewer_view_pager (Scrolless ViewId rule).
     * 3. Selected Reels / Clips navigation tab.
     * 4. Top header text "Reels".
     * 5. Rejects if Home / Feed tab is selected.
     */
    private fun isInstagramReelsActive(root: AccessibilityNodeInfo, rootBounds: Rect): Boolean {
        // Fast-negative #1: comment sheet is open — the reel ViewPager is still
        // underneath so clips_viewer_view_pager would give a false positive without this.
        if (isInstagramCommentSheetOpen(root)) return false

        if (hasVisibleViewId(root, "com.instagram.android:id/clips_viewer_view_pager")) return true

        var isHomeTabSelected  = false
        var isReelsTabSelected = false
        var hasReelsHeader     = false
        val screenHeight       = rootBounds.height().coerceAtLeast(1)

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < 350) {
            val node = queue.removeFirst()
            visited++
            if (isNodeVisibleToTheUser(node)) {
                val desc       = node.contentDescription?.toString()?.lowercase() ?: ""
                val text       = node.text?.toString()?.lowercase() ?: ""
                val isSelected = node.isSelected
                val nodeRect   = Rect().also(node::getBoundsInScreen)

                if (isSelected) {
                    if (desc.contains("home") || desc.contains("feed") ||
                        text.contains("home") || text.contains("feed")) {
                        isHomeTabSelected = true
                    }
                    if (desc.contains("reels") || desc.contains("clips") ||
                        text.contains("reels") || text.contains("clips")) {
                        isReelsTabSelected = true
                    }
                }

                if (text.equals("reels", ignoreCase = true) && nodeRect.top < screenHeight * 0.25f) {
                    hasReelsHeader = true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (isHomeTabSelected) return false
        return isReelsTabSelected || hasReelsHeader
    }

    /**
     * Returns true when the Instagram comment bottom sheet is currently shown.
     *
     * Signals checked (most specific first for performance):
     *  1. View IDs: `comment_bottom_sheet`, `comments_fragment`, `igds_bottom_sheet_root`,
     *     `comments_recycler_view`, `comment_row`, `comment_text_input_field`
     *  2. Class names containing "comment" or "bottomsheet"
     *  3. Presence of visible text "Comments" in a top-position header
     *
     * The root-node overload performs a bounded BFS (max 200 nodes) for context
     * evaluation. Scroll-event handling instead uses [isNodeInsideCommentContainer]
     * to inspect the source node's ancestors.
     */
    private fun isInstagramCommentSheetOpen(root: AccessibilityNodeInfo): Boolean {
        // Fast ID checks first (O(1) hash lookup in framework)
        val commentIds = listOf(
            "com.instagram.android:id/comment_bottom_sheet",
            "com.instagram.android:id/comments_fragment_root",
            "com.instagram.android:id/igds_bottom_sheet_root",
            "com.instagram.android:id/comments_recycler_view",
            "com.instagram.android:id/comment_row_message_container",
            "com.instagram.android:id/comment_text_input_field"
        )
        for (id in commentIds) {
            if (hasVisibleViewId(root, id)) {
                Log.d(TAG, "[INSTAGRAM] Comment sheet detected via viewId: $id")
                return true
            }
        }

        // BFS-based class + text check (capped at 200 nodes)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 200) {
            val node = queue.removeFirst()
            visited++
            val clazz = node.className?.toString()?.lowercase() ?: ""
            val text  = node.text?.toString()?.lowercase() ?: ""
            val desc  = node.contentDescription?.toString()?.lowercase() ?: ""
            val resId = node.viewIdResourceName?.lowercase() ?: ""

            if (clazz.contains("comment") ||
                clazz.contains("bottomsheet") ||
                resId.contains("comment") ||
                (text == "comments" && isNodeVisibleToTheUser(node)) ||
                (desc == "comments" && isNodeVisibleToTheUser(node))
            ) {
                Log.d(TAG, "[INSTAGRAM] Comment sheet detected via BFS: class=$clazz resId=$resId")
                return true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    /**
     * Like [hasVisibleViewId], but tolerant of nodes Android reports as not visible to the user:
     * a SurfaceView behind a transparent overlay is regularly flagged that way, which is enough on
     * its own to lose the whole Shorts session. To keep this from matching a YouTube fragment that
     * is attached but off screen, such a node only counts when it is laid out over at least half
     * the screen.
     */
    private fun hasViewIdInTree(root: AccessibilityNodeInfo, fullyQualifiedId: String): Boolean {
        return try {
            val nodes = root.findAccessibilityNodeInfosByViewId(fullyQualifiedId)
            if (nodes.isEmpty()) return false
            if (nodes.any(::isNodeVisibleToTheUser)) return true

            val screenHeight = screenMetrics().second
            nodes.any { node ->
                val rect = Rect()
                try { node.getBoundsInScreen(rect) } catch (_: Exception) {}
                rect.width() > 0 &&
                        (screenHeight <= 0 || rect.height() * 2 >= screenHeight)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * YouTube Shorts detection, ordered from the most decisive evidence to the structural
     * fallback, because YouTube renames views between versions and often reports no id at all:
     *  1. Any known Shorts pager/container view id (`reel_recycler`, `reel_player_page_container`,
     *     `reel_watch_fragment_root`, …).
     *  2. A node labelled "Shorts" that is selected — the bottom tab while Shorts is open.
     *  3. Structural: a full-screen scrollable container that holds a full-screen video surface.
     *     That is the shape of the Shorts pager; the home feed, the watch page, and search never
     *     combine both halves of it.
     */
    private fun isYouTubeShortsActive(root: AccessibilityNodeInfo, packageName: String): Boolean {
        for (idName in YouTubeShortsRules.SHORTS_CONTAINER_VIEW_IDS) {
            if (hasViewIdInTree(root, "$packageName:id/$idName")) {
                Log.d(TAG, "[YT] Shorts confirmed by view id: $idName")
                return true
            }
        }

        val (screenWidth, screenHeight) = screenMetrics()
        var isSelectedShortsTab = false
        var hasFullScreenPager = false
        var hasFullScreenSurface = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_YOUTUBE_TREE_SCAN_NODES) {
            val node = queue.removeFirst()
            visited++

            val viewId = try { node.viewIdResourceName?.lowercase() ?: "" } catch (_: Exception) { "" }
            val className = try { node.className?.toString()?.lowercase() ?: "" } catch (_: Exception) { "" }

            if (viewId.isNotEmpty() && YouTubeShortsRules.isShortsPagerMarker(viewId)) {
                Log.d(TAG, "[YT] Shorts confirmed by pager marker: $viewId")
                return true
            }

            val label = try {
                "${node.text ?: ""} ${node.contentDescription ?: ""}".lowercase()
            } catch (_: Exception) {
                ""
            }
            if (!isSelectedShortsTab && label.contains("shorts")) {
                val selected = try { node.isSelected || node.isFocused } catch (_: Exception) { false }
                if (selected && isNodeVisibleToTheUser(node)) isSelectedShortsTab = true
            }

            if (screenWidth > 0 && screenHeight > 0 && (!hasFullScreenPager || !hasFullScreenSurface)) {
                val rect = Rect()
                try { node.getBoundsInScreen(rect) } catch (_: Exception) {}
                if (rect.width() > 0 && rect.height() > 0) {
                    val widthFraction = rect.width().toFloat() / screenWidth.toFloat()
                    val heightFraction = rect.height().toFloat() / screenHeight.toFloat()
                    if (!hasFullScreenPager &&
                        isNodeVisibleToTheUser(node) &&
                        YouTubeShortsRules.isFullScreenPager(
                            sourceWidthFraction = widthFraction,
                            sourceHeightFraction = heightFraction,
                            isScrollable = try { node.isScrollable } catch (_: Exception) { false },
                            classNameLowercase = className
                        )
                    ) {
                        hasFullScreenPager = true
                    }
                    if (!hasFullScreenSurface &&
                        heightFraction >= YouTubeShortsRules.MIN_SURFACE_HEIGHT_FRACTION &&
                        (YouTubeShortsRules.VIDEO_SURFACE_MARKERS.any { className.contains(it) } ||
                                YouTubeShortsRules.VIDEO_SURFACE_MARKERS.any { viewId.contains(it) })
                    ) {
                        hasFullScreenSurface = true
                    }
                }
            }

            val childCount = try { node.childCount } catch (_: Exception) { 0 }
            for (i in 0 until childCount) {
                try { node.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
            }
        }

        if (isSelectedShortsTab) {
            Log.d(TAG, "[YT] Shorts confirmed by the selected Shorts tab")
            return true
        }
        val structural = hasFullScreenPager && hasFullScreenSurface
        if (structural) Log.d(TAG, "[YT] Shorts confirmed structurally (full-screen pager + video)")
        return structural
    }

    /**
     * TikTok detection: player view or main feed vertical pager.
     */
    private fun isTikTokActive(root: AccessibilityNodeInfo, packageName: String): Boolean {
        if (hasVisibleViewId(root, "$packageName:id/player_view") ||
            hasVisibleViewId(root, "$packageName:id/simplayer_api_player_view")
        ) {
            return true
        }
        return true // TikTok's main feed is always vertical video
    }
}
