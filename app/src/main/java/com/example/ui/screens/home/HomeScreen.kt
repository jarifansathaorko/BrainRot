package com.example.ui.screens.home

import android.app.Activity
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NotchBarPreview
import com.example.ui.components.NotchDynamicIslandCustomizer
import com.example.ui.components.ScrollyCharacter
import com.example.ui.components.ScrollyMascotState
import com.example.ui.mascot.BrainMascotState
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
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val todayStats by viewModel.todayStats.collectAsStateWithLifecycle()
    val liveTotalScrolls by viewModel.liveTotalScrolls.collectAsStateWithLifecycle()
    val appStats by viewModel.todayAppStats.collectAsStateWithLifecycle()
    val isServiceActive by viewModel.isServiceActive.collectAsStateWithLifecycle()
    val isOverlayPermissionGranted by viewModel.isOverlayPermissionGranted.collectAsStateWithLifecycle()
    val isBatteryOptimizationIgnored by viewModel.isBatteryOptimizationIgnored.collectAsStateWithLifecycle()
    val showBatteryDialog by viewModel.showBatteryOptimizationDialog.collectAsStateWithLifecycle()
    val showOverlayDialog by viewModel.showOverlayPermissionDialog.collectAsStateWithLifecycle()

    val isNotchPreviewVisible by viewModel.isNotchBarPreviewVisible.collectAsStateWithLifecycle()
    val notchConfig by viewModel.notchConfig.collectAsStateWithLifecycle()
    val isNotchCustomizerOpen by viewModel.isNotchCustomizerOpen.collectAsStateWithLifecycle()
    val brainMascotState by viewModel.brainMascotState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    // Auto-refresh permissions on app resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val totalScrolls = if (liveTotalScrolls > 0) liveTotalScrolls else (todayStats?.totalScrolls ?: 0)
    val dailyLimit = todayStats?.goal ?: 100

    // Animated number counter
    val animatedCount by animateIntAsState(targetValue = totalScrolls, label = "scroll_counter")

    var selectedAppForSim by remember { mutableStateOf("com.instagram.android") }

    val supportedList = remember {
        listOf(
            Pair("com.instagram.android", "Instagram"),
            Pair("com.google.android.youtube", "Shorts"),
            Pair("com.zhiliaoapp.musically", "TikTok"),
            Pair("com.spotify.music", "Spotify"),
            Pair("com.facebook.katana", "Facebook")
        )
    }

    // --- BATTERY OPTIMIZATION POPUP DIALOG ---
    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissBatteryOptimizationDialog() },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SleekGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Battery Optimization",
                        tint = SleekGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Enable Background Usage",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = SleekTextPrimary
                    ),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Android aggressively pauses background services to save battery, which can cause the floating counter to not show when switching between apps.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = SleekTextSecondary)
                    )
                    Text(
                        text = "⚡ Scrolly's detection engine is built to draw less than 1% battery charge per day while staying active.\n\nDisabling battery optimization ensures the Dynamic Island counter always appears instantly.",
                        style = MaterialTheme.typography.bodySmall.copy(color = SleekTextPrimary, fontWeight = FontWeight.Medium)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.requestIgnoreBatteryOptimization() },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Turn Off Optimization", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBatteryOptimizationDialog() }) {
                    Text("Maybe Later", color = SleekTextMuted)
                }
            },
            containerColor = SleekCardSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // --- OVERLAY PERMISSION POPUP DIALOG ---
    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissOverlayPermissionDialog() },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SleekPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Overlay Permission",
                        tint = SleekPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Display Over Other Apps",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = SleekTextPrimary
                    ),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "To float the Dynamic Island counter above Facebook, Instagram, YouTube, and TikTok, Android requires the 'Display over other apps' permission.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = SleekTextSecondary)
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.requestOverlayPermission() },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open Settings", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissOverlayPermissionDialog() }) {
                    Text("Cancel", color = SleekTextMuted)
                }
            },
            containerColor = SleekCardSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SleekBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TOP BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Scrolly",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = SleekTextPrimary
                        )
                    )
                    Text(
                        text = "Take back your attention",
                        style = MaterialTheme.typography.labelSmall.copy(color = SleekTextMuted)
                    )
                }

                // Streak Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SleekPillBg)
                        .border(1.dp, SleekBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "7",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = SleekHeroText,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "🔥 DAY STREAK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SleekPillText,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- HERO CARD ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, SleekBorder, RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = SleekHeroCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Label
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TODAY'S SCROLLS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SleekHeroText
                            )
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SleekCardHighlight)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "LIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SleekHeroText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Giant Scroll Count
                    Text(
                        text = "$animatedCount",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 68.sp,
                            fontWeight = FontWeight.Normal,
                            color = SleekHeroText
                        )
                    )

                    Text(
                        text = "Shorts & Reels consumed today",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = SleekHeroText.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Animated Mascot (Interactive: tap to check in)
                    ScrollyCharacter(
                        mascotState = brainMascotState,
                        size = 135.dp,
                        onClick = { viewModel.onMascotTapped() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mascot Reaction Speech & Intensity Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SleekCardSurface)
                            .border(1.dp, SleekBorder, RoundedCornerShape(20.dp))
                            .clickable { viewModel.onMascotTapped() }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Intensity Badge Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(brainMascotState.dailyScrollIntensity.color.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${brainMascotState.dailyScrollIntensity.label.uppercase()} INTENSITY",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = brainMascotState.dailyScrollIntensity.color,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }

                                Text(
                                    text = brainMascotState.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = SleekPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = brainMascotState.userInteractionReaction ?: "\"${brainMascotState.quote}\"",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SleekTextPrimary,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (brainMascotState.userInteractionReaction != null) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "💡 ${brainMascotState.advice}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SleekTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Progress Bar toward daily limit
                    val progressFraction = (totalScrolls.toFloat() / dailyLimit.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val isNearLimit = totalScrolls >= (dailyLimit * 0.8f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$totalScrolls / $dailyLimit limit",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = SleekHeroText,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = "${((1f - progressFraction) * dailyLimit).toInt().coerceAtLeast(0)} left",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isNearLimit) SleekRed else SleekHeroText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = if (isNearLimit) SleekRed else SleekPrimary,
                        trackColor = SleekCardHighlight,
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- APP DISTRIBUTION SUMMARY ROW ---
            Text(
                text = "Active Apps",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = SleekTextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                supportedList.forEach { (pkg, name) ->
                    val appStat = appStats.find { it.packageName == pkg }
                    val count = appStat?.scrollCount ?: 0
                    val isSelected = selectedAppForSim == pkg

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                1.5.dp,
                                if (isSelected) SleekPrimary else SleekBorder,
                                RoundedCornerShape(18.dp)
                            )
                            .clickable { selectedAppForSim = pkg },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) SleekPillBg else SleekCardSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppIconBadge(packageName = pkg, size = 26.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) SleekHeroText else SleekTextPrimary
                                )
                            )
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) SleekPrimary else SleekTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- HARDWARE NOTCH CUSTOMIZER ---
            NotchDynamicIslandCustomizer(
                config = notchConfig,
                onSelectNotchType = { viewModel.selectNotchType(it) },
                onSetPlacementMode = { viewModel.setPlacementMode(it) },
                onSetCutoutGapWidth = { viewModel.setCutoutGapWidth(it) },
                onTriggerAutoDetect = { viewModel.triggerAutoDetectCutout(activity) },
                onAdjustPosition = { dx, dy -> viewModel.adjustNotchPosition(dx, dy) },
                onSetPosition = { x, y -> viewModel.setNotchPosition(x, y) },
                onSetDynamicIslandMode = { viewModel.setDynamicIslandMode(it) },
                onSetShowGuide = { viewModel.setShowCutoutGuide(it) },
                onResetDefaults = { viewModel.resetNotchDefaults() },
                onTestScroll = { viewModel.simulateScroll(selectedAppForSim, 1) },
                isExpanded = isNotchCustomizerOpen,
                onToggleExpanded = { viewModel.toggleNotchCustomizer() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick bar preview toggler row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleNotchBarPreview() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNotchPreviewVisible) SleekCardSurfaceElevated else SleekPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (isNotchPreviewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle Notch Bar",
                        tint = if (isNotchPreviewVisible) SleekPillText else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isNotchPreviewVisible) "Hide Live Island Overlay" else "Show Live Island Overlay",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isNotchPreviewVisible) SleekPillText else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- LIVE SCROLL TESTER ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, SleekBorder, RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = SleekCardSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Test",
                            tint = SleekPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live Scroll Tester (Dev & Emulator)",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = SleekPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap to simulate scroll events on selected app and verify real-time counter & floating island:",
                        style = MaterialTheme.typography.bodySmall.copy(color = SleekTextSecondary)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.simulateScroll(selectedAppForSim, 1) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("+1 Scroll", style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = { viewModel.simulateScroll(selectedAppForSim, 5) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SleekCardSurfaceElevated),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("+5", style = MaterialTheme.typography.labelMedium.copy(color = SleekPillText, fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = { viewModel.simulateScroll(selectedAppForSim, 20) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SleekCardSurfaceElevated),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("+20", style = MaterialTheme.typography.labelMedium.copy(color = SleekPillText, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- RELIABILITY & PERMISSIONS CONTROL CENTER ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, SleekBorder, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = SleekCardSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "System Setup & Reliability",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = SleekTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // 1. Accessibility Service
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isServiceActive) SleekGreen.copy(alpha = 0.15f) else SleekCardHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isServiceActive) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = "Accessibility",
                                tint = if (isServiceActive) SleekGreen else SleekPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Accessibility Service",
                                style = MaterialTheme.typography.labelMedium.copy(color = SleekTextPrimary, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isServiceActive) "Active • Counting Reels & Shorts" else "Required to detect Reels swipes",
                                style = MaterialTheme.typography.labelSmall.copy(color = SleekTextSecondary)
                            )
                        }

                        if (!isServiceActive) {
                            OutlinedButton(
                                onClick = { viewModel.openAccessibilitySettings() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekPrimary)
                            ) {
                                Text("Enable", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        } else {
                            Text("Active", style = MaterialTheme.typography.labelSmall.copy(color = SleekGreen, fontWeight = FontWeight.Bold))
                        }
                    }

                    // 2. Display Over Other Apps (Overlay)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isOverlayPermissionGranted) SleekGreen.copy(alpha = 0.15f) else SleekCardHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isOverlayPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Layers,
                                contentDescription = "Overlay",
                                tint = if (isOverlayPermissionGranted) SleekGreen else SleekPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Display Over Other Apps",
                                style = MaterialTheme.typography.labelMedium.copy(color = SleekTextPrimary, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isOverlayPermissionGranted) "Granted • Floats island over apps" else "Needed to show counter over apps",
                                style = MaterialTheme.typography.labelSmall.copy(color = SleekTextSecondary)
                            )
                        }

                        if (!isOverlayPermissionGranted) {
                            OutlinedButton(
                                onClick = { viewModel.promptOverlayPermissionDialog() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekPrimary)
                            ) {
                                Text("Grant", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        } else {
                            Text("Granted", style = MaterialTheme.typography.labelSmall.copy(color = SleekGreen, fontWeight = FontWeight.Bold))
                        }
                    }

                    // 3. Battery Optimization (Background Usage)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isBatteryOptimizationIgnored) SleekGreen.copy(alpha = 0.15f) else SleekRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isBatteryOptimizationIgnored) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = "Battery Optimization",
                                tint = if (isBatteryOptimizationIgnored) SleekGreen else SleekRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background Battery Usage",
                                style = MaterialTheme.typography.labelMedium.copy(color = SleekTextPrimary, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isBatteryOptimizationIgnored) "Unrestricted • Zero freeze" else "Android may pause background counter",
                                style = MaterialTheme.typography.labelSmall.copy(color = if (isBatteryOptimizationIgnored) SleekTextSecondary else SleekRed)
                            )
                        }

                        if (!isBatteryOptimizationIgnored) {
                            Button(
                                onClick = { viewModel.promptBatteryOptimizationDialog() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                            ) {
                                Text("Fix", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                            }
                        } else {
                            Text("Unrestricted", style = MaterialTheme.typography.labelSmall.copy(color = SleekGreen, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
