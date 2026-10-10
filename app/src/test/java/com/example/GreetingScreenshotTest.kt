package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.components.NotchBarPreview
import com.example.ui.theme.ScrollyTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun notch_bar_screenshot() {
    composeTestRule.setContent {
      ScrollyTheme {
        NotchBarPreview(scrollCount = 42, appName = "Reels", dailyGoal = 100)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/notch_bar.png")
  }
}
