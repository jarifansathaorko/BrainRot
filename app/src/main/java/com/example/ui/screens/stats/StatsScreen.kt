package com.example.ui.screens.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppIconBadge
import com.example.ui.components.ScrollyCharacter
import com.example.ui.theme.SleekBg
import com.example.ui.theme.SleekBorder
import com.example.ui.theme.SleekCardSurface
import com.example.ui.theme.SleekCardSurfaceSecondary
import com.example.ui.theme.SleekGreen
import com.example.ui.theme.SleekHeroCard
import com.example.ui.theme.SleekHeroText
import com.example.ui.theme.SleekPillBg
import com.example.ui.theme.SleekPillText
import com.example.ui.theme.SleekPrimary
import com.example.ui.theme.SleekTextMuted
import com.example.ui.theme.SleekTextPrimary
import com.example.ui.theme.SleekTextSecondary

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsStateWithLifecycle()

    val maxCount = (uiState.chartBars.maxOfOrNull { it.count } ?: 1).coerceAtLeast(10).toFloat()

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
            text = "Progress & Analytics",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SleekTextPrimary
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )

        // --- TIMEFRAME SELECTOR TABS (Day, Week, Month, Year) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SleekCardSurface)
                .border(1.dp, SleekBorder, RoundedCornerShape(24.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatsTimeframe.values().forEach { tf ->
                val isSelected = (selectedTimeframe == tf)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) SleekPrimary else Color.Transparent)
                        .clickable { viewModel.selectTimeframe(tf) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tf.label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else SleekTextSecondary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- PERIOD NAVIGATOR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigatePrevious() }) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous period",
                    tint = SleekTextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SleekPillBg)
                    .border(1.dp, SleekBorder, RoundedCornerShape(20.dp))
                    .clickable { viewModel.resetToCurrent() }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = uiState.periodLabel.ifEmpty { "Current Period" },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = SleekPillText
                    )
                )
            }

            IconButton(
                onClick = { viewModel.navigateNext() },
                enabled = uiState.canGoNext
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next period",
                    tint = if (uiState.canGoNext) SleekTextSecondary else SleekTextMuted.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- DYNAMIC BAR CHART CONTAINER ---
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
                    .padding(vertical = 20.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.chartBars.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No history recorded yet.\nData starts from 0 on first install.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = SleekTextMuted,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        uiState.chartBars.forEachIndexed { index, bar ->
                            val heightFraction = if (bar.count == 0) 0.08f else (bar.count / maxCount).coerceIn(0.12f, 1f)
                            val animatedHeight by animateFloatAsState(
                                targetValue = heightFraction,
                                animationSpec = tween(durationMillis = 600),
                                label = "bar_height_$index"
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                // Count label on top of bar
                                Text(
                                    text = "${bar.count}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (bar.count > 0) SleekTextSecondary else SleekTextMuted.copy(alpha = 0.6f),
                                        fontSize = if (uiState.chartBars.size > 8) 9.sp else 11.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Vertical Pill Bar
                                Box(
                                    modifier = Modifier
                                        .width(if (uiState.chartBars.size > 8) 12.dp else 16.dp)
                                        .height((130 * animatedHeight).dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (bar.count == 0) SleekCardSurfaceSecondary
                                            else if (bar.count > 100) SleekPrimary
                                            else SleekHeroCard
                                        )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Mini Mascot indicator (shows calm zen state when at 0)
                                ScrollyCharacter(
                                    scrollCount = bar.count,
                                    size = if (uiState.chartBars.size > 8) 22.dp else 26.dp,
                                    animated = false
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Primary Label
                                Text(
                                    text = bar.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (bar.isCurrent) SleekPrimary else SleekTextMuted,
                                        fontWeight = if (bar.isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = if (uiState.chartBars.size > 8) 9.sp else 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SIGNATURE HERO STATS CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, SleekBorder, RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = SleekHeroCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Stat: Total Reels
                Column {
                    Text(
                        text = "${uiState.totalReels}",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Normal,
                            color = SleekHeroText,
                            fontSize = 38.sp
                        )
                    )
                    Text(
                        text = "Total Reels",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = SleekHeroText
                        )
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .height(50.dp)
                        .width(1.5.dp)
                        .background(SleekHeroText.copy(alpha = 0.25f))
                )

                // Right Stat: Average or Goal
                Column {
                    Text(
                        text = "${uiState.secondaryStat}",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Normal,
                            color = SleekHeroText,
                            fontSize = 38.sp
                        )
                    )
                    Text(
                        text = uiState.secondaryStatLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = SleekHeroText
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- ENCOURAGING INSIGHT CHIP ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SleekCardSurface)
                .border(1.dp, SleekBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = "Trend",
                tint = SleekGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = uiState.insightMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = SleekTextPrimary,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- APP BREAKDOWN SECTION ---
        Text(
            text = "App Breakdown (${selectedTimeframe.label})",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SleekTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        uiState.appBreakdown.forEach { item ->
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
                    AppIconBadge(packageName = item.packageName, size = 36.dp)

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.appName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SleekTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { item.fractionOfTotal },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = SleekPrimary,
                            trackColor = SleekCardSurfaceSecondary
                        )
                    }

                    Text(
                        text = "${item.count}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (item.count > 0) SleekTextPrimary else SleekTextMuted
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
