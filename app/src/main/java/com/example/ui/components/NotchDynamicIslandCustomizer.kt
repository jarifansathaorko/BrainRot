package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.IslandPlacementMode
import com.example.data.repository.NotchConfiguration
import com.example.data.repository.NotchType
import com.example.ui.theme.SleekBorder
import com.example.ui.theme.SleekCardHighlight
import com.example.ui.theme.SleekCardSurface
import com.example.ui.theme.SleekCardSurfaceElevated
import com.example.ui.theme.SleekCardSurfaceSecondary
import com.example.ui.theme.SleekGreen
import com.example.ui.theme.SleekPrimary
import com.example.ui.theme.SleekTextMuted
import com.example.ui.theme.SleekTextPrimary
import com.example.ui.theme.SleekTextSecondary

/**
 * Device Notch Finder & Auto-Adjuster
 * Automatically detects the phone's hardware camera cutout / notch area
 * and calibrates the Dynamic Bar directly over/beneath the notch area.
 * Keeps the bar in its solid, compact first-shown form so punch-holes never obscure the count.
 */
@Composable
fun NotchDynamicIslandCustomizer(
    config: NotchConfiguration,
    onSelectNotchType: (NotchType) -> Unit,
    onSetPlacementMode: (IslandPlacementMode) -> Unit,
    onSetCutoutGapWidth: (Int) -> Unit,
    onTriggerAutoDetect: () -> Unit,
    onAdjustPosition: (deltaX: Int, deltaY: Int) -> Unit,
    onSetPosition: (x: Int, y: Int) -> Unit,
    onSetDynamicIslandMode: (Boolean) -> Unit,
    onSetShowGuide: (Boolean) -> Unit,
    onResetDefaults: () -> Unit,
    onTestScroll: () -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, SleekBorder, RoundedCornerShape(28.dp)),
        colors = CardDefaults.cardColors(containerColor = SleekCardSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Notch Area Finder
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SleekPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Notch Finder",
                            tint = SleekPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Device Notch Finder",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SleekTextPrimary
                            )
                        )
                        Text(
                            text = if (config.autoAdjusted)
                                "✓ Automatically Adjusted Over Notch Area"
                            else
                                "Tap to detect device notch & calibrate",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (config.autoAdjusted) SleekGreen else SleekTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand Notch Settings",
                        tint = SleekTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // PRIMARY NOTCH FINDER ACTION BUTTON
            Button(
                onClick = onTriggerAutoDetect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_find_device_notch"),
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Auto Detect Notch",
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Find Device Notch & Auto-Adjust",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // HARDWARE NOTCH STATUS CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141A22))
                    .border(1.dp, SleekBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Status",
                                tint = SleekGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (config.detectedCutout.hasCutout)
                                    "Hardware Cutout Found"
                                else
                                    "Status Bar Safe Zone Found",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = SleekTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Text(
                            text = "Safe Y: ${config.offsetY}dp",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SleekPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (config.detectedCutout.hasCutout)
                            "Cutout: ${config.detectedCutout.width}×${config.detectedCutout.height}dp (Center: ${config.detectedCutout.centerX}dp). Dynamic bar is anchored directly over the notch area."
                        else
                            "Top Safe Inset: ${config.detectedCutout.safeInsetTop}dp. Dynamic bar is positioned safely without covering screen content.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SleekTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    )
                }
            }

            // --- EXPANDED DETAILS & MICRO-ADJUSTMENT ---
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    // VISUAL NOTCH AREA DIAGRAM
                    Text(
                        text = "NOTCH AREA ALIGNMENT PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SleekTextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0D1117))
                            .border(1.dp, SleekBorder, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Phone top border line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color(0xFF30363D))
                        )

                        // Hardware Camera Cutout (Punch-Hole)
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .offset(x = (config.offsetX).dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1F242C))
                                .border(1.dp, Color(0xFF484F58), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF090D12))
                            )
                        }

                        // Compact Solid Dynamic Bar Capsule (Positioned directly over/below notch)
                        Box(
                            modifier = Modifier
                                .padding(top = 34.dp)
                                .offset(x = (config.offsetX).dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black)
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🔥", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "48",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                            }
                        }

                        // Status caption at bottom of diagram
                        Text(
                            text = "✓ Solid Compact Bar • Numbers 100% visible outside punch-hole",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SleekGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // FINE-TUNING MICRO NUDGE (±2 DP)
                    Text(
                        text = "MICRO-NUDGE OVER NOTCH (±2 DP)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SleekTextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Up
                            IconButton(
                                onClick = { onAdjustPosition(0, -2) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SleekCardSurfaceSecondary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Nudge Up",
                                    tint = SleekTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Left, Center Auto-Fit, Right
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onAdjustPosition(-2, 0) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SleekCardSurfaceSecondary)
                                ) {
                                    Text("◄", color = SleekTextPrimary, fontSize = 12.sp)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SleekPrimary.copy(alpha = 0.2f))
                                        .border(1.dp, SleekPrimary, RoundedCornerShape(12.dp))
                                        .clickable { onTriggerAutoDetect() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🎯", fontSize = 14.sp)
                                }

                                IconButton(
                                    onClick = { onAdjustPosition(2, 0) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SleekCardSurfaceSecondary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Nudge Right",
                                        tint = SleekTextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Down
                            IconButton(
                                onClick = { onAdjustPosition(0, 2) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SleekCardSurfaceSecondary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Nudge Down",
                                    tint = SleekTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // POSITION SLIDERS
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Vertical Offset (Y)",
                                style = MaterialTheme.typography.labelSmall.copy(color = SleekTextSecondary)
                            )
                            Text(
                                text = "${config.offsetY} dp",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SleekTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Slider(
                            value = config.offsetY.toFloat(),
                            onValueChange = { onSetPosition(config.offsetX, it.toInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = SleekPrimary,
                                activeTrackColor = SleekPrimary,
                                inactiveTrackColor = SleekBorder
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Horizontal Offset (X)",
                                style = MaterialTheme.typography.labelSmall.copy(color = SleekTextSecondary)
                            )
                            Text(
                                text = "${if (config.offsetX > 0) "+${config.offsetX}" else config.offsetX} dp",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SleekTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Slider(
                            value = config.offsetX.toFloat(),
                            onValueChange = { onSetPosition(it.toInt(), config.offsetY) },
                            valueRange = -60f..60f,
                            colors = SliderDefaults.colors(
                                thumbColor = SleekPrimary,
                                activeTrackColor = SleekPrimary,
                                inactiveTrackColor = SleekBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // RESET BUTTON
                    OutlinedButton(
                        onClick = onResetDefaults,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = SleekTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset to Default Notch Position",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = SleekTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}
