package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.NotchSettingsRepository
import com.example.data.repository.NotchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Scrolly", appName)
  }

  @Test
  fun `test notch settings selection and arrow adjustments`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NotchSettingsRepository(context)

    // Select Wide Notch
    repo.selectNotchType(NotchType.WIDE_NOTCH)
    assertEquals(NotchType.WIDE_NOTCH, repo.configFlow.value.notchType)
    assertEquals(48, repo.configFlow.value.offsetY)

    // Arrow down key adjustment (+10dp downwards to clear notch)
    repo.adjustPosition(deltaX = 0, deltaY = 10)
    assertEquals(58, repo.configFlow.value.offsetY)

    // Arrow right key adjustment (+6dp)
    repo.adjustPosition(deltaX = 6, deltaY = 0)
    assertEquals(6, repo.configFlow.value.offsetX)

    // Select Dynamic Island
    repo.selectNotchType(NotchType.DYNAMIC_ISLAND)
    assertEquals(NotchType.DYNAMIC_ISLAND, repo.configFlow.value.notchType)
    assertEquals(38, repo.configFlow.value.offsetY)
    assertTrue(repo.configFlow.value.isDynamicIslandMode)
  }

  @Test
  fun `test stats view model starts at zero and supports Day Week Month Year`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.screens.stats.StatsViewModel(application)

    // Verify timeframes are available
    com.example.ui.screens.stats.StatsTimeframe.values().forEach { tf ->
      vm.selectTimeframe(tf)
      assertEquals(tf, vm.selectedTimeframe.value)
    }

    // Default timeframe is WEEK
    vm.selectTimeframe(com.example.ui.screens.stats.StatsTimeframe.WEEK)
    assertEquals(0, vm.periodOffset.value)

    // Offset navigation
    vm.navigatePrevious()
    assertEquals(-1, vm.periodOffset.value)

    vm.navigateNext()
    assertEquals(0, vm.periodOffset.value)
  }

  @Test
  fun `test scroll detection engine activates on reels scroll and counts`() {
    var detectedScrolls = 0
    var isReelVisible = false
    var detectedApp = ""

    val engine = com.example.tracking.detector.ScrollDetectionEngine(
      service = null,
      onScrollDetected = { pkg, app ->
        detectedScrolls++
        detectedApp = app
      },
      onReelVisibilityChanged = { pkg, app, visible ->
        isReelVisible = visible
      }
    )

    // 1. Scrolling on TikTok triggers reel visibility and counts
    val tiktokEvent = android.view.accessibility.AccessibilityEvent.obtain(
      android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED
    ).apply {
      packageName = "com.zhiliaoapp.musically"
      className = "androidx.viewpager2.widget.ViewPager2"
    }

    engine.processAccessibilityEvent(tiktokEvent)
    assertTrue("Reel notch should become visible on scroll", isReelVisible)
    assertEquals(1, detectedScrolls)
    assertEquals("TikTok", detectedApp)

    // 2. System UI, our own app overlay, or Google Play Services events must NOT hide the notch
    val overlayEvent = android.view.accessibility.AccessibilityEvent.obtain(
      android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    ).apply {
      packageName = "com.example"
    }
    engine.processAccessibilityEvent(overlayEvent)
    assertTrue("Our own overlay event must never dismiss the notch", isReelVisible)

    val gmsEvent = android.view.accessibility.AccessibilityEvent.obtain(
      android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    ).apply {
      packageName = "com.google.android.gms"
    }
    engine.processAccessibilityEvent(gmsEvent)
    assertTrue("Google Play Services event must not dismiss the notch", isReelVisible)

    val systemUiEvent = android.view.accessibility.AccessibilityEvent.obtain(
      android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    ).apply {
      packageName = "com.android.systemui"
    }
    engine.processAccessibilityEvent(systemUiEvent)
    assertTrue("System UI event must not dismiss the notch", isReelVisible)

    // 3. User navigating to Home launcher hides the notch
    val launcherEvent = android.view.accessibility.AccessibilityEvent.obtain(
      android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    ).apply {
      packageName = "com.google.android.apps.nexuslauncher"
    }
    engine.processAccessibilityEvent(launcherEvent)
    org.junit.Assert.assertFalse("Notch must hide when leaving to launcher", isReelVisible)
  }
}
