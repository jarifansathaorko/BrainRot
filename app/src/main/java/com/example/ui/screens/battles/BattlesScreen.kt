package com.example.ui.screens.battles

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
fun BattlesScreen(
    viewModel: BattlesViewModel,
    modifier: Modifier = Modifier
) {
    val battles by viewModel.battles.collectAsStateWithLifecycle()
    var showChallengeDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "lightning_glow")
    val lightningAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

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
            text = "Battle your\nFriends",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SleekTextPrimary,
                lineHeight = 40.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
        )

        Text(
            text = "Lower scroll count wins! Control your feed together.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = SleekTextSecondary,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // --- FEATURED MATCHUP CARD (Inspired by Sleek rounded-[28px] card) ---
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SleekPillBg)
                        .border(1.dp, SleekBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "LIVE BATTLE • TODAY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SleekPillText,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // The Versus Duel Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // --- YOU SIDE ---
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ScrollyCharacter(
                            scrollCount = 36,
                            size = 80.dp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Scroll Count Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(SleekHeroCard)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "36",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SleekHeroText
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "YOU",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SleekTextPrimary
                            )
                        )

                        Text(
                            text = "WINNING 👑",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SleekGreen,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // --- LIGHTNING BOLT CENTER ---
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Versus",
                            tint = SleekPrimary.copy(alpha = lightningAlpha),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "VS",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = SleekTextPrimary
                            )
                        )
                    }

                    // --- FRIEND SIDE ---
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ScrollyCharacter(
                            scrollCount = 93,
                            size = 80.dp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Friend Scroll Count Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(SleekCardHighlight)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "93",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SleekHeroText
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "ALEX",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SleekTextPrimary
                            )
                        )

                        Text(
                            text = "COOKING 🍳",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SleekRed,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Alex has scrolled 57 more reels than you today. Keep going strong!",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SleekTextSecondary,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- ACTIVE CHALLENGES & LEADERBOARD ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Friend Leaderboard",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = SleekTextPrimary
                )
            )

            Button(
                onClick = { showChallengeDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Challenge",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "New Battle",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Battles list
        battles.forEach { battle ->
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
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SleekCardSurfaceElevated)
                            .border(1.dp, SleekBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = SleekPillText
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = battle.friendName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SleekTextPrimary
                            )
                        )
                        Text(
                            text = battle.friendUsername,
                            style = MaterialTheme.typography.labelSmall.copy(color = SleekTextMuted)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${battle.userScrolls} vs ${battle.friendScrolls}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SleekTextPrimary
                            )
                        )
                        val isWinning = battle.userScrolls < battle.friendScrolls
                        Text(
                            text = if (isWinning) "You're Winning" else "Friend Winning",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isWinning) SleekGreen else SleekRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showChallengeDialog) {
        var friendInput by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showChallengeDialog = false }) {
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Challenge a Friend",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = SleekTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enter your friend's @username to launch a 24-hour Scroll Battle!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SleekTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = friendInput,
                        onValueChange = { friendInput = it },
                        placeholder = { Text("@username", color = SleekTextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = SleekBorder,
                            focusedTextColor = SleekTextPrimary,
                            unfocusedTextColor = SleekTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showChallengeDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SleekCardSurfaceElevated),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = SleekPillText)
                        }

                        Button(
                            onClick = {
                                if (friendInput.isNotBlank()) {
                                    viewModel.addFriendChallenge(friendInput, friendInput.removePrefix("@").replaceFirstChar { it.uppercase() })
                                    showChallengeDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Start Battle", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
