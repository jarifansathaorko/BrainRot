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
        private const val INSTAGRAM_COMMENT_SHEET_SUPPRESSION_MS = 1200L
        private const val MAX_COMMENT_CONTAINER_ANCESTOR_DEPTH = 15
    }

    private val powerManager by lazy {
        service?.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private var lastScrollTimestamp: Long = 0L
    private var lastEvalTimestamp: Long = 0L
    private var instagramCommentSheetOpenedAt: Long = 0L

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
                if (isCurrentlyWatchingReels) {
                    Log.d(TAG, "[SCREEN_OFF] Hiding overlay — screen is off")
                    setReelInactive(currentPackage ?: "", "")
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
                if (isCurrentlyWatchingReels || currentPackage != eventPkg) {
                    Log.d(TAG, "[WINDOW] Non-social foreground → force hide overlay")
                    currentPackage = eventPkg
                    setReelInactive(eventPkg, "")
                } else {
                    currentPackage = eventPkg
                }
                return
            }

            val appName = AppRecognitionEngine.getAppName(eventPkg)
            currentPackage = eventPkg
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
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun setReelInactive(pkg: String, appName: String) {
        isCurrentlyWatchingReels = false
        cachedIsReelContext = false
        onReelVisibilityChanged?.invoke(pkg, appName, false)
        Log.d(TAG, "[REEL] pkg=$pkg  active=false")
    }

    private fun scheduleEvaluation(activePkg: String, appName: String) {
        pendingEvalRunnable?.let { handler.removeCallbacks(it) }
        pendingEvalRunnable = Runnable {
            // Re-evaluate reel context using the live node tree — no dummy AccessibilityEvent needed
            val isReel = checkNodeTreeStrictlyForReels(activePkg)
            if (isReel != isCurrentlyWatchingReels) {
                if (isReel) {
                    isCurrentlyWatchingReels = true
                    cachedIsReelContext = true
                    onReelVisibilityChanged?.invoke(activePkg, appName, true)
                    Log.d(TAG, "[REEL] pkg=$activePkg  active=true  (via scheduled eval)")
                } else {
                    setReelInactive(activePkg, appName)
                }
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
            if (!isCurrentlyWatchingReels) {
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
            cachedIsReelContext = false
            if (isCurrentlyWatchingReels) {
                setReelInactive(packageName, appName)
            }
        }
    }

    private fun handleScroll(packageName: String, appName: String, event: AccessibilityEvent) {
        val now = System.currentTimeMillis()

        // Reject comment-sheet events before debounce, context evaluation, or state
        // changes. The reel pager remains in the tree behind this sheet, so those
        // later checks alone cannot distinguish a comment RecyclerView from a reel.
        if (packageName.contains("instagram")) {
            if (now - instagramCommentSheetOpenedAt < INSTAGRAM_COMMENT_SHEET_SUPPRESSION_MS) {
                Log.d(TAG, "[SCROLL] Rejected — Instagram comment sheet is animating")
                return
            }

            if (isNodeInsideCommentContainer(event.source)) {
                Log.d(TAG, "[SCROLL] Rejected — inside Instagram comment sheet")
                return
            }
        }

        if (now - lastScrollTimestamp < MIN_SCROLL_INTERVAL_MS) return

        // Reject horizontal swiping
        if (!isStrictlyVerticalScroll(event)) {
            Log.d(TAG, "[SCROLL] Rejected horizontal scroll in $packageName")
            return
        }

        // Quick negative check
        val className = event.className?.toString() ?: ""
        if (isUnambiguousNegativeScreen(packageName, className, event)) {
            cachedIsReelContext = false
            if (isCurrentlyWatchingReels) {
                setReelInactive(packageName, appName)
            }
            return
        }

        // Confirm reel context
        val isReel = if (isTikTok(packageName)) {
            !isUnambiguousNegativeScreen(packageName, className, event)
        } else {
            cachedIsReelContext || isPositiveReelScreen(packageName, className, event)
        }

        if (!isReel) return

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

    private fun isStrictlyVerticalScroll(event: AccessibilityEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val deltaX = event.scrollDeltaX
            val deltaY = event.scrollDeltaY

            if (deltaX != -1 && deltaY != -1) {
                if (deltaX != 0 && deltaY == 0) return false
                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 2) return false
                if (deltaY > 0) return true
                if (deltaY < 0) return false
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
            cachedIsReelContext = false
            if (isCurrentlyWatchingReels) {
                setReelInactive(packageName, appName)
            }
            return
        }

        // 2. Strict Positive Check
        val isReel = isPositiveReelScreen(packageName, className, event)
        cachedIsReelContext = isReel

        if (isReel) {
            if (!isCurrentlyWatchingReels) {
                isCurrentlyWatchingReels = true
                onReelVisibilityChanged?.invoke(packageName, appName, true)
                Log.d(TAG, "[REEL] pkg=$packageName  active=true  (via evaluation)")
            }
        } else {
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
     * YouTube Shorts detection:
     * 1. View ID reel_player_page_container.
     * 2. Selected Shorts tab.
     */
    private fun isYouTubeShortsActive(root: AccessibilityNodeInfo, packageName: String): Boolean {
        if (hasVisibleViewId(root, "$packageName:id/reel_player_page_container")) return true

        try {
            val tabNodes = root.findAccessibilityNodeInfosByText("Shorts")
            if (tabNodes.isNotEmpty() && tabNodes.any { it.isSelected && isNodeVisibleToTheUser(it) }) {
                return true
            }
        } catch (_: Exception) {}

        return false
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
