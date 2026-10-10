package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrandFacebook
import com.example.ui.theme.BrandInstagram
import com.example.ui.theme.BrandSnapchat
import com.example.ui.theme.BrandYouTube
import com.example.ui.theme.ScrollyDarkBg

@Composable
fun AppIconBadge(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val (bgBrush, iconColor, iconVector) = when (packageName) {
        "com.instagram.android" -> Triple(
            Brush.linearGradient(listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCAF45))),
            Color.White,
            Icons.Default.CameraAlt
        )
        "com.google.android.youtube" -> Triple(
            Brush.linearGradient(listOf(BrandYouTube, Color(0xFFCC0000))),
            Color.White,
            Icons.Default.PlayArrow
        )
        "com.facebook.katana" -> Triple(
            Brush.linearGradient(listOf(BrandFacebook, Color(0xFF1565C0))),
            Color.White,
            Icons.Default.ThumbUp
        )
        "com.snapchat.android" -> Triple(
            Brush.linearGradient(listOf(BrandSnapchat, Color(0xFFFFE600))),
            ScrollyDarkBg,
            Icons.Default.Videocam
        )
        "com.spotify.music", "com.spotify.lite" -> Triple(
            Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF121212))),
            Color.White,
            Icons.Default.PlayArrow
        )
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme" -> Triple(
            Brush.linearGradient(listOf(Color(0xFF00F2FE), Color(0xFFFE2C55), Color(0xFF010101))),
            Color.White,
            Icons.Default.PlayArrow
        )
        else -> Triple(
            Brush.linearGradient(listOf(Color(0xFF555555), Color(0xFF333333))),
            Color.White,
            Icons.Default.Videocam
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgBrush)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = packageName,
            tint = iconColor,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
