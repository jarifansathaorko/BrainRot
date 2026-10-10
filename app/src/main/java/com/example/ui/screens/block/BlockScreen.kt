package com.example.ui.screens.block

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppIconBadge
import com.example.ui.components.ScrollyCharacter
import com.example.ui.theme.SleekBg
import com.example.ui.theme.SleekBorder
import com.example.ui.theme.SleekCardHighlight
import com.example.ui.theme.SleekCardSurface
import com.example.ui.theme.SleekCardSurfaceElevated
import com.example.ui.theme.SleekGreen
import com.example.ui.theme.SleekHeroCard
import com.example.ui.theme.SleekHeroText
import com.example.ui.theme.SleekPillBg
import com.example.ui.theme.SleekPillText
import com.example.ui.theme.SleekPrimary
import com.example.ui.theme.SleekRed
import com.example.ui.theme.SleekTextMuted
import com.example.ui.theme.SleekTextPrimary
import com.example.ui.theme.SleekTextSecondary

@Composable
fun BlockScreen(
    viewModel: BlockViewModel,
    modifier: Modifier = Modifier
) {
    val limits by viewModel.appLimits.collectAsStateWithLifecycle()
    var previewInterventionApp by remember { mutableStateOf<String?>(null) }
    var challengeEarnedNotice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SleekBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- SCREEN TITLE ---
        Text(
            text = "Block Reels",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SleekTextPrimary
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        Text(
            text = "Set strict caps before doomscrolling takes over your day.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = SleekTextSecondary,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // --- SLEEK COACH MASCOT CARD (Matching rounded-[28px]) ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, SleekBorder, RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = SleekCardSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScrollyCharacter(
                    scrollCount = 75,
                    size = 75.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Scrolly Interceptor",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SleekTextPrimary
                        )
                    )
                    Text(
                        text = "When your daily limit is hit, Scrolly puts up a sleek full-screen intervention to break the dopamine loop.",
                        style = MaterialTheme.typography.bodySmall.copy(color = SleekTextSecondary)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { previewInterventionApp = "Instagram" },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Preview Intervention", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- APP LIMITS CONFIGURATION LIST ---
        Text(
            text = "Daily App Limits",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SleekTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        limits.forEach { limit ->
            var sliderValue by remember(limit.dailyLimit) { mutableFloatStateOf(limit.dailyLimit.toFloat()) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, SleekBorder, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = SleekCardSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIconBadge(packageName = limit.packageName, size = 36.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = limit.appName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = SleekTextPrimary
                                    )
                                )
                                Text(
                                    text = "${sliderValue.toInt()} reels per day",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = SleekPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }

                        Switch(
                            checked = limit.isEnabled,
                            onCheckedChange = { isChecked ->
                                viewModel.updateLimit(
                                    packageName = limit.packageName,
                                    newLimit = sliderValue.toInt(),
                                    warningThreshold = (sliderValue * 0.8f).toInt(),
                                    isEnabled = isChecked
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SleekPrimary,
                                uncheckedThumbColor = SleekTextMuted,
                                uncheckedTrackColor = SleekCardSurfaceElevated
                            )
                        )
                    }

                    if (limit.isEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                viewModel.updateLimit(
                                    packageName = limit.packageName,
                                    newLimit = sliderValue.toInt(),
                                    warningThreshold = (sliderValue * 0.8f).toInt(),
                                    isEnabled = limit.isEnabled
                                )
                            },
                            valueRange = 10f..200f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = SleekPrimary,
                                activeTrackColor = SleekPrimary,
                                inactiveTrackColor = SleekCardSurfaceElevated
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- EARN MORE SCROLLS ---
        Text(
            text = "Earn Extra Scrolls",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SleekTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        )

        Text(
            text = "Complete healthy, intentional resets to unlock small scroll allowances.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = SleekTextSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        viewModel.challenges.forEach { challenge ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, SleekBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SleekCardSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (challenge.iconName) {
                        "walk" -> Icons.Default.DirectionsWalk
                        "phone" -> Icons.Default.PhoneAndroid
                        "wind" -> Icons.Default.SelfImprovement
                        else -> Icons.Default.Timer
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SleekCardHighlight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = SleekHeroText)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = challenge.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SleekTextPrimary
                            )
                        )
                        Text(
                            text = challenge.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = SleekTextSecondary,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+${challenge.rewardScrolls} scrolls  •  +${challenge.rewardXp} XP",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SleekPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            viewModel.completeChallengeAndEarn("com.instagram.android", challenge.rewardScrolls)
                            challengeEarnedNotice = "+${challenge.rewardScrolls} bonus scrolls unlocked!"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPillBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Claim", style = MaterialTheme.typography.labelSmall.copy(color = SleekPillText, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        challengeEarnedNotice?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SleekGreen.copy(alpha = 0.15f))
                    .border(1.dp, SleekGreen, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = msg, style = MaterialTheme.typography.labelMedium.copy(color = SleekGreen, fontWeight = FontWeight.Bold))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // --- SLEEK FULL SCREEN SCROLL BLOCK INTERVENTION DIALOG ---
    previewInterventionApp?.let { appName ->
        Dialog(
            onDismissRequest = { previewInterventionApp = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SleekBg.copy(alpha = 0.98f))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SleekRed.copy(alpha = 0.12f))
                            .border(2.dp, SleekRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Blocked",
                            tint = SleekRed,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "That's enough scrolling.",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = SleekTextPrimary,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You've reached your daily $appName limit of 100 scrolls. Your brain needs a breather.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = SleekTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    ScrollyCharacter(
                        scrollCount = 250, // Cooked state
                        size = 140.dp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { previewInterventionApp = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Take a Break", style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.completeChallengeAndEarn("com.instagram.android", 10)
                            previewInterventionApp = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekTextPrimary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Earn +10 Scrolls with Focus Reset", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Emergency Override (5 mins)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SleekTextMuted,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
