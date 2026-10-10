package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.tracking.detector.NotchDetectionHelper
import com.example.ui.navigation.ScrollyAppScaffold
import com.example.ui.theme.ScrollyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.decorView.post {
            try {
                val detected = NotchDetectionHelper.detectFromWindow(window, this)
                val repo = ScrollyApp.instance.notchSettingsRepository
                // If not manually calibrated yet, automatically adopt the detected hardware cutout
                if (!repo.configFlow.value.autoAdjusted || detected.hasCutout) {
                    repo.autoCalibrateWithCutout(detected)
                }
            } catch (_: Exception) {}
        }
        
        handleIntent(intent)

        setContent {
            ScrollyTheme {
                ScrollyAppScaffold()
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "scrolly" && data.host == "invite") {
            val friendUid = data.getQueryParameter("uid")
            if (friendUid != null) {
                lifecycleScope.launch {
                    val success = ScrollyApp.instance.socialRepository.connectFriend(friendUid)
                    val msg = if (success) "Connected with friend!" else "Failed to connect or already connected."
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
