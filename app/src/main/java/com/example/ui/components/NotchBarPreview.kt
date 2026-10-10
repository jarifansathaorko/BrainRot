package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.repository.IslandPlacementMode
import com.example.data.repository.NotchConfiguration
import com.example.ui.theme.SleekBorder
import com.example.ui.theme.SleekCardHighlight
import com.example.ui.theme.SleekGreen
import com.example.ui.theme.SleekPillBg
import com.example.ui.theme.SleekPrimary
import com.example.ui.theme.SleekRed

/**
 * iPhone-Style Dynamic Island Floating Counter.
 * - Sits precisely at hardware cutout coordinates.
 * - In "WRAP_AROUND_NOTCH" mode, houses the camera in the center gap so text/counts are never hidden.
 * - In "BELOW_NOTCH" mode, floats safely beneath the camera cutout.
 * - Interactive: tap to expand into full Dynamic Island card with goal progress.
 */
@Composable
fun NotchBarPreview(
    scrollCount: Int,
    appName: String = "Reels",
    dailyGoal: Int = 100,
    visible: Boolean = true,
    config: NotchConfiguration = NotchConfiguration(),
    onDismiss: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    val scaleAnim = remember { Animatable(1.0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_pulse"
    )

    // Bounce on scroll increment
    LaunchedEffect(scrollCount) {
        if (scrollCount > 0) {
            scaleAnim.animateTo(
                targetValue = 1.15f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            scaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(9999f)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val statusColor = when {
                scrollCount >= dailyGoal -> SleekRed
                scrollCount >= (dailyGoal * 0.75f).toInt() -> Color(0xFFFFA000)
                else -> SleekGreen
            }

            val shortApp = when {
                appName.contains("YouTube", ignoreCase = true) || appName.contains("Shorts", ignoreCase = true) -> "Shorts"
                appName.contains("TikTok", ignoreCase = true) -> "TikTok"
                appName.contains("Spotify", ignoreCase = true) -> "Spotify"
                appName.contains("Snapchat", ignoreCase = true) || appName.contains("Spotlight", ignoreCase = true) -> "Spotlight"
                appName.contains("Facebook", ignoreCase = true) -> "FB Reels"
                else -> "Reels"
            }

            val appEmoji = "🔥"

            val progressFraction = (scrollCount.toFloat() / dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f)

            // Dynamic Island Capsule
            Box(
                modifier = Modifier
                    .offset(x = config.offsetX.dp, y = config.offsetY.dp)
                    .scale(scaleAnim.value)
                    .shadow(
                        elevation = if (isExpanded) 24.dp else 12.dp,
                        shape = RoundedCornerShape(if (isExpanded) 28.dp else 22.dp)
                    )
                    .clip(RoundedCornerShape(if (isExpanded) 28.dp else 22.dp))
                    .background(Color(0xFF000000)) // Pure Apple Dynamic Island pitch black
                    .border(
                        1.dp,
                        if (isExpanded) SleekPrimary.copy(alpha = 0.6f) else Color(0xFF333333),
                        RoundedCornerShape(if (isExpanded) 28.dp else 22.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isExpanded = !isExpanded
                    }
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                    .testTag("dynamic_island_pill")
            ) {
                if (isExpanded) {
                    // EXPANDED DYNAMIC ISLAND CARD
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1C24)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = appEmoji, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "$shortApp Session",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Dynamic Island Active",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = SleekGreen,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Collapse Island",
                                    tint = Color(0xFFAAAAAA),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "Scrolls Today",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA))
                                )
                                Text(
                                    text = "$scrollCount",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Black
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Daily Limit: $dailyGoal",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA))
                                )
                                Text(
                                    text = "${((1f - progressFraction) * dailyGoal).toInt().coerceAtLeast(0)} left",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = statusColor,
                            trackColor = Color(0xFF222222),
                            strokeCap = StrokeCap.Round
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { isExpanded = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24222E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Minimize", style = MaterialTheme.typography.labelMedium.copy(color = Color.White))
                            }
                        }
                    }
                } else {
                    // COMPACT DYNAMIC ISLAND PILL
                    if (config.placementMode == IslandPlacementMode.WRAP_AROUND_NOTCH) {
                        // TRUE IPHONE ISLAND: WINGS ON EITHER SIDE OF CAMERA CUTOUT
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            // LEFT WING: App / Flame Emoji
                            Text(
                                text = appEmoji,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(end = 6.dp)
                            )

                            // CENTER CUTOUT SPACER: Exactly covers the physical notch / punch-hole!
                            // Nothing is rendered here so the hardware camera never covers content.
                            Box(
                                modifier = Modifier
                                    .width(config.cutoutGapWidth.dp)
                                    .height(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (config.showCutoutGuide) {
                                    // Visual alignment guide outline
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(SleekPrimary.copy(alpha = 0.35f))
                                            .border(1.dp, SleekPrimary, CircleShape)
                                    )
                                }
                            }

                            // RIGHT WING: Counter Number + Live Dot
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "$scrollCount",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusColor.copy(alpha = dotAlpha))
                                )
                            }
                        }
                    } else {
                        // BELOW NOTCH (COMPACT UNIFIED FLOATING PILL)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = appEmoji,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$scrollCount",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = dotAlpha))
                            )
                        }
                    }
                }
            }
        }
    }
}
