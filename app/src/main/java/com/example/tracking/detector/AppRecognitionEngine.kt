package com.example.tracking.detector

data class SupportedAppConfig(
    val packageName: String,
    val appName: String,
    val feedKeywords: List<String>,
    val isEnabled: Boolean = true
)

object AppRecognitionEngine {
    val SUPPORTED_APPS = mapOf(
        "com.instagram.android" to SupportedAppConfig(
            packageName = "com.instagram.android",
            appName = "Instagram Reels",
            feedKeywords = listOf("reel", "clips", "feed", "viewer", "video", "media")
        ),
        "com.instagram.lite" to SupportedAppConfig(
            packageName = "com.instagram.lite",
            appName = "Instagram Reels",
            feedKeywords = listOf("reel", "clips", "feed", "viewer", "video", "media")
        ),
        "com.google.android.youtube" to SupportedAppConfig(
            packageName = "com.google.android.youtube",
            appName = "YouTube Shorts",
            feedKeywords = listOf("shorts", "reel", "player", "watch", "vertical")
        ),
        "com.zhiliaoapp.musically" to SupportedAppConfig(
            packageName = "com.zhiliaoapp.musically",
            appName = "TikTok",
            feedKeywords = listOf("feed", "video", "foryou", "player", "main", "vertical")
        ),
        "com.zhiliaoapp.musically.go" to SupportedAppConfig(
            packageName = "com.zhiliaoapp.musically.go",
            appName = "TikTok",
            feedKeywords = listOf("feed", "video", "foryou", "player", "main", "vertical")
        ),
        "com.ss.android.ugc.trill" to SupportedAppConfig(
            packageName = "com.ss.android.ugc.trill",
            appName = "TikTok",
            feedKeywords = listOf("feed", "video", "foryou", "player", "main", "vertical")
        ),
        "com.ss.android.ugc.aweme" to SupportedAppConfig(
            packageName = "com.ss.android.ugc.aweme",
            appName = "TikTok",
            feedKeywords = listOf("feed", "video", "foryou", "player", "main", "vertical")
        ),
        "com.snapchat.android" to SupportedAppConfig(
            packageName = "com.snapchat.android",
            appName = "Snapchat Spotlight",
            feedKeywords = listOf("spotlight", "discover", "story", "stories", "feed")
        ),
        "com.facebook.katana" to SupportedAppConfig(
            packageName = "com.facebook.katana",
            appName = "Facebook Reels",
            feedKeywords = listOf("reel", "watch", "video", "feed", "story")
        ),
        "com.facebook.lite" to SupportedAppConfig(
            packageName = "com.facebook.lite",
            appName = "Facebook Reels",
            feedKeywords = listOf("reel", "watch", "video", "feed", "story")
        ),
        "com.spotify.music" to SupportedAppConfig(
            packageName = "com.spotify.music",
            appName = "Spotify Video",
            feedKeywords = listOf("canvas", "video", "clips", "discover", "npv")
        ),
        "com.spotify.lite" to SupportedAppConfig(
            packageName = "com.spotify.lite",
            appName = "Spotify Video",
            feedKeywords = listOf("canvas", "video", "clips", "discover", "npv")
        )
    )

    fun getAppName(packageName: String): String {
        return SUPPORTED_APPS[packageName]?.appName ?: "Unknown App"
    }

    fun isSupported(packageName: String?): Boolean {
        if (packageName == null) return false
        return SUPPORTED_APPS.containsKey(packageName)
    }
}
