package com.example

import com.example.tracking.detector.YouTubeScrollSignals
import com.example.tracking.detector.YouTubeShortsRules
import com.example.tracking.detector.YouTubeSwipeVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the YouTube Shorts swipe rules.
 *
 * These are deliberately framework-free: [YouTubeShortsRules] takes primitives, so the exact
 * device situation that used to break counting (renamed view ids, missing view ids, no source
 * node, a swipe reported as a negative delta) can be reproduced without a device or a node tree.
 */
class YouTubeShortsRulesTest {

    private fun signals(
        viewId: String = "",
        className: String = "androidx.recyclerview.widget.RecyclerView",
        scrollable: Boolean = true,
        widthFraction: Float = 1f,
        heightFraction: Float = 1f,
        deltaX: Int = 0,
        deltaY: Int = 1080,
        hasDeltas: Boolean = true,
        surface: Boolean = false,
        session: Boolean = false
    ) = YouTubeScrollSignals(
        sourceViewId = viewId,
        sourceClassName = className,
        sourceIsScrollable = scrollable,
        sourceWidthFraction = widthFraction,
        sourceHeightFraction = heightFraction,
        deltaX = deltaX,
        deltaY = deltaY,
        hasDirectionalDeltas = hasDeltas,
        fullScreenVideoSurface = surface,
        shortsSessionConfirmed = session
    )

    @Test
    fun `known Shorts pager ids count in both swipe directions and without deltas`() {
        listOf("reel_recycler", "reel_player_page_container", "reel_watch_fragment_root").forEach { id ->
            assertEquals(
                "$id should count a downward swipe",
                YouTubeSwipeVerdict.COUNT,
                YouTubeShortsRules.classify(signals(viewId = "com.google.android.youtube:id/$id"), SCREEN_H)
            )
            assertEquals(
                "$id should count an upward swipe (negative delta)",
                YouTubeSwipeVerdict.COUNT,
                YouTubeShortsRules.classify(
                    signals(viewId = "com.google.android.youtube:id/$id", deltaY = -1080),
                    SCREEN_H
                )
            )
            assertEquals(
                "$id should count when the app reports no scroll deltas at all",
                YouTubeSwipeVerdict.COUNT,
                YouTubeShortsRules.classify(
                    signals(
                        viewId = "com.google.android.youtube:id/$id",
                        deltaX = YouTubeScrollSignals.NO_DELTA,
                        deltaY = YouTubeScrollSignals.NO_DELTA,
                        hasDeltas = false
                    ),
                    SCREEN_H
                )
            )
        }
    }

    @Test
    fun `a settled snap that reports no delta still counts on a marker id`() {
        // Some YouTube versions dispatch TYPE_VIEW_SCROLLED after the pager has settled, with the
        // delta left at zero. The Shorts pager only moves between reels, so that is a swipe.
        assertEquals(
            YouTubeSwipeVerdict.COUNT,
            YouTubeShortsRules.classify(
                signals(viewId = "com.google.android.youtube:id/reel_recycler", deltaY = 0),
                SCREEN_H
            )
        )

        assertEquals(
            "without a marker, a zero movement is a jitter, not a swipe",
            YouTubeSwipeVerdict.MOVEMENT_TOO_SMALL,
            YouTubeShortsRules.classify(signals(viewId = "", deltaY = 0, session = true), SCREEN_H)
        )
    }

    @Test
    fun `unknown id still counts inside a confirmed Shorts session`() {
        val verdict = YouTubeShortsRules.classify(
            signals(viewId = "com.google.android.youtube:id/some_new_pager_id", deltaY = 700),
            SCREEN_H
        )
        assertEquals(
            "an unrecognised id on a full-screen vertical pager must not be counted without a session",
            YouTubeSwipeVerdict.NEEDS_SHORTS_CONFIRMATION,
            verdict
        )

        assertEquals(
            YouTubeSwipeVerdict.COUNT,
            YouTubeShortsRules.classify(
                signals(viewId = "com.google.android.youtube:id/some_new_pager_id", deltaY = 700, session = true),
                SCREEN_H
            )
        )
    }

    @Test
    fun `missing view ids fall back to class and session or video surface`() {
        // No view ids reported (FLAG_REPORT_VIEW_IDS off, or a view without an id).
        assertEquals(
            YouTubeSwipeVerdict.COUNT,
            YouTubeShortsRules.classify(
                signals(
                    viewId = "",
                    className = "androidx.viewpager2.widget.ViewPager2",
                    deltaY = -1080,
                    session = true
                ),
                SCREEN_H
            )
        )

        assertEquals(
            "without a session, only a full-page snap onto a full-screen video may open one",
            YouTubeSwipeVerdict.COUNT,
            YouTubeShortsRules.classify(
                signals(viewId = "", className = "androidx.recyclerview.widget.RecyclerView", deltaY = 2200, surface = true),
                SCREEN_H
            )
        )
    }

    @Test
    fun `ordinary YouTube browsing is not counted as Shorts`() {
        // A long fling through the home feed: full-screen list, no Shorts evidence.
        assertEquals(
            YouTubeSwipeVerdict.NEEDS_SHORTS_CONFIRMATION,
            YouTubeShortsRules.classify(signals(viewId = "", deltaY = 3000), SCREEN_H)
        )

        // Small moves on the watch page never reach the entry threshold.
        assertEquals(
            YouTubeSwipeVerdict.NEEDS_SHORTS_CONFIRMATION,
            YouTubeShortsRules.classify(
                signals(viewId = "com.google.android.youtube:id/nested_scrollable_container", deltaY = 320),
                SCREEN_H
            )
        )

        // The comment sheet owns its scrolls and must be swallowed, not re-considered.
        assertEquals(
            YouTubeSwipeVerdict.NOT_SHORTS_PAGER,
            YouTubeShortsRules.classify(
                signals(viewId = "com.google.android.youtube:id/comments_recycler_view", deltaY = 200),
                SCREEN_H
            )
        )

        // A non-scrolling node is never the pager.
        assertEquals(
            YouTubeSwipeVerdict.NOT_SHORTS_PAGER,
            YouTubeShortsRules.classify(
                signals(scrollable = false, heightFraction = 0.2f, deltaY = 900),
                SCREEN_H
            )
        )
    }

    @Test
    fun `horizontal moves jitters bursts and cooldowns are handled`() {
        assertEquals(
            YouTubeSwipeVerdict.HORIZONTAL,
            YouTubeShortsRules.classify(
                signals(viewId = "reel_recycler", deltaX = 900, deltaY = 12),
                SCREEN_H
            )
        )

        assertEquals(
            "a tap jitter is not a swipe",
            YouTubeSwipeVerdict.MOVEMENT_TOO_SMALL,
            YouTubeShortsRules.classify(
                signals(viewId = "some_id", deltaY = 6, session = true),
                SCREEN_H
            )
        )

        assertEquals(
            "trailing events of the same swipe are duplicates",
            YouTubeSwipeVerdict.DUPLICATE_BURST_EVENT,
            YouTubeShortsRules.classify(
                signals(viewId = "reel_recycler").copy(msSinceLastPagerEvent = 80L),
                SCREEN_H
            )
        )

        assertEquals(
            "a second swipe inside the cooldown is ignored",
            YouTubeSwipeVerdict.COOLDOWN,
            YouTubeShortsRules.classify(
                signals(viewId = "reel_recycler").copy(
                    msSinceLastPagerEvent = YouTubeShortsRules.BURST_GAP_MS + 50L,
                    msSinceLastCountedSwipe = 300L
                ),
                SCREEN_H
            )
        )
    }

    @Test
    fun `entry threshold scales with the screen but keeps a floor`() {
        assertTrue(
            YouTubeShortsRules.entryMinimumSwipePx(2400) >= (2400 * YouTubeShortsRules.ENTRY_MIN_SWIPE_FRACTION).toInt()
        )
        assertEquals(
            YouTubeShortsRules.IN_SESSION_MIN_SWIPE_PX,
            YouTubeShortsRules.inSessionMinimumSwipePx(0)
        )
        assertEquals(
            YouTubeShortsRules.ENTRY_MIN_SWIPE_PX,
            YouTubeShortsRules.entryMinimumSwipePx(0)
        )
        assertFalse(
            "unknown geometry must not be mistaken for a full-screen pager",
            YouTubeShortsRules.isFullScreenPager(
                sourceWidthFraction = YouTubeScrollSignals.UNKNOWN_GEOMETRY,
                sourceHeightFraction = YouTubeScrollSignals.UNKNOWN_GEOMETRY,
                isScrollable = true,
                classNameLowercase = "androidx.recyclerview.widget.RecyclerView"
            )
        )
        assertTrue(
            YouTubeShortsRules.isFullScreenPager(1f, 1f, true, "androidx.recyclerview.widget.RecyclerView")
        )
        assertFalse(
            "a half-height watch-page player is not a Shorts pager",
            YouTubeShortsRules.isFullScreenPager(1f, 0.5f, true, "androidx.recyclerview.widget.RecyclerView")
        )
    }

    private companion object {
        const val SCREEN_H = 2400
    }
}
