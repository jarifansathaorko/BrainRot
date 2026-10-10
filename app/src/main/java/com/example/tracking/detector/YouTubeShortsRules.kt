package com.example.tracking.detector

import kotlin.math.abs

/**
 * Outcome of [YouTubeShortsRules.classify] for one YouTube `TYPE_VIEW_SCROLLED` event.
 */
enum class YouTubeSwipeVerdict {
    /** Count this event as exactly one Shorts swipe. */
    COUNT,

    /** Trailing event of a swipe burst that was already counted — swallow it. */
    DUPLICATE_BURST_EVENT,

    /** A swipe was counted too recently for this event to be a new one — swallow it. */
    COOLDOWN,

    /** Sideways movement (tab bar, carousel, comment sheet) — never a Shorts swipe. */
    HORIZONTAL,

    /** The event does not look like it came from the Shorts pager. */
    NOT_SHORTS_PAGER,

    /** The pager moved, but not far enough to be a completed swipe to the next short. */
    MOVEMENT_TOO_SMALL,

    /**
     * Ambiguous: a large vertical scroll with no Shorts evidence yet. The caller should run a
     * tree check ([ScrollDetectionEngine.checkNodeTreeStrictlyForReels]) before counting.
     */
    NEEDS_SHORTS_CONFIRMATION
}

/**
 * Everything the [ScrollDetectionEngine] knows about a single YouTube scroll event, reduced to
 * primitives so that the decision can be unit tested without a device or a node tree.
 *
 * Geometry fractions are [YouTubeScrollSignals.UNKNOWN_GEOMETRY] when the screen size or the
 * source bounds could not be read, and [sourceViewId] is empty when the app did not report view
 * ids (missing `FLAG_REPORT_VIEW_IDS`, or a view with no id at all).
 */
data class YouTubeScrollSignals(
    val sourceViewId: String = "",
    val sourceClassName: String = "",
    val sourceIsScrollable: Boolean = false,
    val sourceWidthFraction: Float = UNKNOWN_GEOMETRY,
    val sourceHeightFraction: Float = UNKNOWN_GEOMETRY,
    val deltaX: Int = NO_DELTA,
    val deltaY: Int = NO_DELTA,
    val hasDirectionalDeltas: Boolean = false,
    val fullScreenVideoSurface: Boolean = false,
    val shortsSessionConfirmed: Boolean = false,
    val msSinceLastCountedSwipe: Long = Long.MAX_VALUE,
    val msSinceLastPagerEvent: Long = Long.MAX_VALUE
) {
    companion object {
        const val UNKNOWN_GEOMETRY = -1f
        const val NO_DELTA = -1
    }
}

/**
 * YouTubeShortsRules
 *
 * Single source of truth for "is this YouTube scroll event one swipe to the next short?".
 *
 * Why this exists: YouTube is the app whose detection has broken most often, because every rule
 * that relied on one exact view id (`reel_recycler`) stops working whenever YouTube renames a
 * view, reports no view id at all, or renders the Shorts pager asynchronously so that a window
 * scan finds nothing. The rules below therefore never require a single id:
 *
 *  1. **Marker ids** (`isShortsPagerMarker`) — a list of every Shorts pager / container id seen
 *     in the wild, matched as substrings. A hit is decisive.
 *  2. **Geometry** ([YouTubeScrollSignals.sourceHeightFraction]) — the Shorts pager fills the
 *     whole screen, while YouTube's home feed, watch page, and comment sheet never combine a
 *     full-screen scrollable container with a full-screen video surface.
 *  3. **A confirmed Shorts session** — the sticky flag in [ScrollDetectionEngine]. Once Shorts
 *     has been entered (tab tap, marker id, window/tree evidence), every later vertical
 *     full-height scroll counts, even if the view ids are unavailable.
 *
 * The same leniency is deliberately *not* applied when nothing has confirmed Shorts yet: there a
 * swipe only counts if it moves roughly one screen height **and** a full-screen video surface is
 * on screen, so the home feed and the watch page stay uncounted.
 */
object YouTubeShortsRules {

    /**
     * View-id fragments owned by the Shorts pager or its container. YouTube has shipped several
     * of these across versions, and the accessibility event source is not always the pager
     * itself, so all of them are treated as decisive evidence.
     */
    val SHORTS_PAGER_ID_MARKERS = listOf(
        "reel_recycler",
        "reel_watch_fragment_root",
        "reel_watch_fragment",
        "reel_player_page_container",
        "reel_player_container",
        "reel_watch_recycler",
        "shorts_recycler",
        "shorts_recyclerview",
        "shorts_viewer",
        "shorts_player",
        "shorts_watch_container",
        "immersionless"
    )

    /**
     * View-id fragments that are never the Shorts pager. YouTube keeps the pager alive in the
     * tree behind these surfaces, so an open comment sheet or panel must veto a count.
     */
    val NON_PAGER_ID_MARKERS = listOf(
        "comment",
        "engagement_panel",
        "chip_bar",
        "toolbar",
        "horizontal",
        "search"
    )

    /** Container classes that can own a vertical Shorts pager. */
    val PAGER_CLASS_MARKERS = listOf(
        "recyclerview",
        "viewpager",
        "scrollview",
        "listview",
        "nestedscroll"
    )

    /** Classes that scroll sideways, or that can never be a feed. */
    val NON_PAGER_CLASS_MARKERS = listOf(
        "horizontal",
        "tablayout",
        "tabbar",
        "carousel",
        "storytray",
        "edittext",
        "textview",
        "imageview",
        "button"
    )

    /** Container ids whose presence means the Shorts viewer is on screen. */
    val SHORTS_CONTAINER_VIEW_IDS = listOf(
        "reel_player_page_container",
        "reel_recycler",
        "reel_watch_fragment_root",
        "shorts_recycler_view",
        "shorts_recyclerview",
        "shorts_viewer",
        "immersionless_player"
    )

    /** Views/classes that YouTube uses for the video surface inside a Shorts page. */
    val VIDEO_SURFACE_MARKERS = listOf("surfaceview", "textureview", "player_view", "videoview")

    /** The Shorts pager fills the screen; a feed row or a sheet does not. */
    const val MIN_PAGER_WIDTH_FRACTION = 0.9f
    const val MIN_PAGER_HEIGHT_FRACTION = 0.7f

    /** A full-screen video is what separates Shorts from the portrait watch page. */
    const val MIN_SURFACE_HEIGHT_FRACTION = 0.75f

    /** Below this the "scroll" is a tap jitter or a content refresh, not a swipe. */
    const val MIN_SWIPE_JITTER_PX = 24

    /** While Shorts is confirmed, any real swipe on the full-height pager counts. */
    const val IN_SESSION_MIN_SWIPE_PX = 150
    const val IN_SESSION_MIN_SWIPE_FRACTION = 0.25f

    /** Without a confirmed session only a near full-page snap may open one. */
    const val ENTRY_MIN_SWIPE_FRACTION = 0.55f
    const val ENTRY_MIN_SWIPE_PX = 900

    /** Events closer than this belong to the same swipe and never count twice. */
    const val BURST_GAP_MS = 250L

    /** One extra guard against double counting a slow, multi-event snap. */
    const val SWIPE_COOLDOWN_MS = 600L

    fun isShortsPagerMarker(viewIdLowercase: String): Boolean {
        if (viewIdLowercase.isEmpty()) return false
        return SHORTS_PAGER_ID_MARKERS.any { viewIdLowercase.contains(it) }
    }

    fun isNonPagerMarkerViewId(viewIdLowercase: String): Boolean {
        if (viewIdLowercase.isEmpty()) return false
        return NON_PAGER_ID_MARKERS.any { viewIdLowercase.contains(it) }
    }

    /** True when the id says "sheet/panel" and no Shorts pager marker says otherwise. */
    fun isNonPagerViewId(viewIdLowercase: String): Boolean {
        return isNonPagerMarkerViewId(viewIdLowercase) && !isShortsPagerMarker(viewIdLowercase)
    }

    fun isNonPagerView(viewIdLowercase: String, classNameLowercase: String): Boolean {
        if (isNonPagerMarkerViewId(viewIdLowercase)) {
            return true
        }
        return classNameLowercase.isNotEmpty() &&
                NON_PAGER_CLASS_MARKERS.any { classNameLowercase.contains(it) }
    }

    /** True when the scroll moved sideways rather than up/down. */
    fun isHorizontalMovement(deltaX: Int, deltaY: Int, hasDirectionalDeltas: Boolean): Boolean {
        if (!hasDirectionalDeltas) return false
        if (deltaX != 0 && deltaY == 0) return true
        return abs(deltaX) > abs(deltaY) && abs(deltaX) > 2
    }

    /**
     * True when the event's source is a container big enough to be a full-screen feed pager.
     * Unknown geometry returns false — the caller then falls back to ids and session state.
     */
    fun isFullScreenPager(
        sourceWidthFraction: Float,
        sourceHeightFraction: Float,
        isScrollable: Boolean,
        classNameLowercase: String
    ): Boolean {
        if (sourceWidthFraction < 0f || sourceHeightFraction < 0f) return false
        if (isNonPagerView("", classNameLowercase)) return false
        return isScrollable &&
                sourceWidthFraction >= MIN_PAGER_WIDTH_FRACTION &&
                sourceHeightFraction >= MIN_PAGER_HEIGHT_FRACTION
    }

    /** Smallest movement that counts as one swipe while Shorts is already confirmed. */
    fun inSessionMinimumSwipePx(screenHeightPx: Int): Int {
        val fromFraction = if (screenHeightPx > 0) {
            (screenHeightPx * IN_SESSION_MIN_SWIPE_FRACTION).toInt()
        } else {
            0
        }
        return maxOf(IN_SESSION_MIN_SWIPE_PX, fromFraction)
    }

    /** Smallest movement that may open a Shorts session without any other evidence. */
    fun entryMinimumSwipePx(screenHeightPx: Int): Int {
        if (screenHeightPx <= 0) return ENTRY_MIN_SWIPE_PX
        return maxOf((screenHeightPx * ENTRY_MIN_SWIPE_FRACTION).toInt(), IN_SESSION_MIN_SWIPE_PX)
    }

    /**
     * The decision. See the class doc for the three evidence sources it combines.
     */
    fun classify(signals: YouTubeScrollSignals, screenHeightPx: Int): YouTubeSwipeVerdict {
        val viewId = signals.sourceViewId.lowercase()
        val className = signals.sourceClassName.lowercase()

        if (isHorizontalMovement(signals.deltaX, signals.deltaY, signals.hasDirectionalDeltas)) {
            return YouTubeSwipeVerdict.HORIZONTAL
        }

        val markerHit = isShortsPagerMarker(viewId)
        // A non-pager id (comment sheet, chip bar, panel) vetoes the source unless a Shorts
        // marker is present as well.
        val vetoedById = isNonPagerViewId(viewId)

        val geometryPager = isFullScreenPager(
            sourceWidthFraction = signals.sourceWidthFraction,
            sourceHeightFraction = signals.sourceHeightFraction,
            isScrollable = signals.sourceIsScrollable,
            classNameLowercase = className
        )

        // View ids are frequently absent (flag off, or YouTube views built without ids). In that
        // case a pager-looking class plus either a confirmed session or a full-screen video is
        // what identifies the Shorts pager.
        val idsUnavailable = viewId.isEmpty()
        val classLooksLikePager = className.isNotEmpty() &&
                PAGER_CLASS_MARKERS.any { className.contains(it) }
        val lenientPager = idsUnavailable && classLooksLikePager &&
                (signals.shortsSessionConfirmed || signals.fullScreenVideoSurface)

        val isPager = !vetoedById && (markerHit || geometryPager || lenientPager)
        if (!isPager) return YouTubeSwipeVerdict.NOT_SHORTS_PAGER

        if (signals.msSinceLastPagerEvent in 0 until BURST_GAP_MS) {
            return YouTubeSwipeVerdict.DUPLICATE_BURST_EVENT
        }
        if (signals.msSinceLastCountedSwipe in 0 until SWIPE_COOLDOWN_MS) {
            return YouTubeSwipeVerdict.COOLDOWN
        }

        val movement = if (signals.hasDirectionalDeltas) abs(signals.deltaY) else -1

        if (movement < 0) {
            // No directional deltas (Android 8 and below, or an app that omits them). A marker
            // hit or a confirmed session is enough evidence; anything else needs a tree check.
            return if (markerHit || signals.shortsSessionConfirmed) {
                YouTubeSwipeVerdict.COUNT
            } else {
                YouTubeSwipeVerdict.NEEDS_SHORTS_CONFIRMATION
            }
        }

        // The Shorts pager only ever moves between reels, so a marker hit is a swipe on its own:
        // some YouTube versions dispatch the event after the snap has settled and report the delta
        // as zero, which must not lose the count. The burst gap and cooldown still apply.
        if (markerHit) return YouTubeSwipeVerdict.COUNT

        if (movement < MIN_SWIPE_JITTER_PX) return YouTubeSwipeVerdict.MOVEMENT_TOO_SMALL

        if (signals.shortsSessionConfirmed) {
            return if (movement >= inSessionMinimumSwipePx(screenHeightPx)) {
                YouTubeSwipeVerdict.COUNT
            } else {
                YouTubeSwipeVerdict.MOVEMENT_TOO_SMALL
            }
        }

        // Opening a session from scratch: a full-screen scrollable container is not enough — the
        // home feed and the watch page both fill the screen. Only a near full-page snap that lands
        // on a full-screen video surface counts; anything else asks for a tree check first.
        val bigEnoughSnap = movement >= entryMinimumSwipePx(screenHeightPx)
        return if (bigEnoughSnap && signals.fullScreenVideoSurface) {
            YouTubeSwipeVerdict.COUNT
        } else {
            YouTubeSwipeVerdict.NEEDS_SHORTS_CONFIRMATION
        }
    }
}
