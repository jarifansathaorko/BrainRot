package com.example

import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.example.tracking.detector.ScrollDetectionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Engine-level tests for the YouTube Shorts counting path.
 *
 * `service = null` makes every window tree scan come back empty, which is exactly the state
 * YouTube puts the detector into while the Shorts pager is still rendering, and on installs where
 * the app exposes no usable view ids. Counting must therefore rest on the events themselves plus
 * the sticky Shorts session, and an inconclusive scan must not dismiss the pill.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YouTubeShortsDetectionTest {

    private class Harness {
        var scrolls = 0
        var reelVisible = false
        val visibilityChanges = mutableListOf<Pair<String, Boolean>>()

        val engine: ScrollDetectionEngine by lazy {
            ScrollDetectionEngine(
                service = null,
                onScrollDetected = { _, _ -> scrolls++ },
                onReelVisibilityChanged = { pkg, _, visible ->
                    reelVisible = visible
                    visibilityChanges += pkg to visible
                }
            )
        }
    }

    private fun shortClick() =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = YOUTUBE
            contentDescription = "Shorts"
        }

    private fun swipe(
        deltaY: Int,
        pkg: String = YOUTUBE,
        className: String = "androidx.viewpager2.widget.ViewPager2"
    ) = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {
        packageName = pkg
        this.className = className
        scrollDeltaX = 0
        scrollDeltaY = deltaY
    }

    private fun windowChange(pkg: String, className: String) =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = pkg
            this.className = className
        }

    private fun contentChange(pkg: String = YOUTUBE) =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED).apply {
            packageName = pkg
            className = "android.widget.FrameLayout"
        }

    @Test
    fun `shorts swipes count without view ids and survive an empty tree scan`() {
        val h = Harness()

        h.engine.processAccessibilityEvent(shortClick())
        assertTrue("Tapping the Shorts tab must show the counter", h.reelVisible)

        // The user swipes to the next short. YouTube exposes no usable view id here, so only the
        // session, the class name, and the delta are available — and the swipe to the next short
        // is reported as a negative delta.
        h.engine.processAccessibilityEvent(swipe(-1080))
        assertEquals("An upward Shorts swipe must count", 1, h.scrolls)

        // YouTube then fires content updates while the pager settles and the tree scan is empty.
        // That used to hide the counter, which stopped every later swipe from being counted.
        h.engine.processAccessibilityEvent(contentChange())
        shadowOf(Looper.getMainLooper()).idleFor(1500, TimeUnit.MILLISECONDS)

        assertTrue("An inconclusive tree scan must not hide the Shorts counter", h.reelVisible)
        assertEquals(
            "The counter must never be hidden while the user stays in Shorts",
            emptyList<Pair<String, Boolean>>(),
            h.visibilityChanges.filter { !it.second }
        )

        // And a swipe is still accepted afterwards.
        h.engine.processAccessibilityEvent(swipe(-1080))
        assertTrue("Counting must continue after a transient miss", h.scrolls >= 1)
    }

    @Test
    fun `one swipe never counts twice`() {
        val h = Harness()
        h.engine.processAccessibilityEvent(shortClick())

        // A settled snap and the trailing events of the same gesture arrive together.
        h.engine.processAccessibilityEvent(swipe(1080))
        h.engine.processAccessibilityEvent(swipe(120))
        h.engine.processAccessibilityEvent(swipe(60))

        assertEquals("Burst events of one swipe must collapse into one count", 1, h.scrolls)
    }

    @Test
    fun `ordinary YouTube browsing does not count as Shorts`() {
        val h = Harness()

        // Long flings through the home feed and the watch page, with no Shorts session open.
        h.engine.processAccessibilityEvent(
            swipe(3000, className = "androidx.recyclerview.widget.RecyclerView")
        )
        h.engine.processAccessibilityEvent(
            swipe(1600, className = "androidx.recyclerview.widget.RecyclerView")
        )

        assertEquals("The home feed must not be counted as Shorts", 0, h.scrolls)
        assertFalse("The counter must not appear outside a reel viewer", h.reelVisible)
    }

    @Test
    fun `a Shorts-shaped window opens the session when the tree scan misses`() {
        val h = Harness()

        h.engine.processAccessibilityEvent(
            windowChange(YOUTUBE, "com.google.android.youtube.shorts.ShortsPlayerFragment")
        )
        assertTrue("A Shorts window is enough to show the counter", h.reelVisible)

        h.engine.processAccessibilityEvent(swipe(-1080))
        assertEquals(1, h.scrolls)
    }

    @Test
    fun `leaving YouTube ends the Shorts session`() {
        val h = Harness()
        h.engine.processAccessibilityEvent(shortClick())
        h.engine.processAccessibilityEvent(swipe(1080))
        assertEquals(1, h.scrolls)

        h.engine.processAccessibilityEvent(
            windowChange(
                "com.google.android.apps.nexuslauncher",
                "com.google.android.apps.nexuslauncher.NexusLauncherActivity"
            )
        )
        assertFalse("The counter must hide when another app is in the foreground", h.reelVisible)
        assertEquals(
            "The counter is shown once and hidden once",
            listOf(true, false),
            h.visibilityChanges.map { it.second }
        )

        // Back inside YouTube the swipe has to re-confirm Shorts, and the tree scan is empty.
        h.engine.processAccessibilityEvent(swipe(1080))
        assertEquals("A swipe without a re-confirmed Shorts viewer must not count", 1, h.scrolls)
    }

    private companion object {
        const val YOUTUBE = "com.google.android.youtube"
    }
}
