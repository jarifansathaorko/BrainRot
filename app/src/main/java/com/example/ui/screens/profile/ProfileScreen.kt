package com.example.ui.screens.profile

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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ScrollyCharacter
import com.example.ui.theme.SleekBg
import com.example.ui.theme.SleekBorder
import com.example.ui.theme.SleekCardHighlight
import com.example.ui.theme.SleekCardSurface
import com.example.ui.theme.SleekCardSurfaceElevated
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
fun ProfileScreen(
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    
    var showEditUsernameDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    val context = LocalContext.current

    if (showEditUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showEditUsernameDialog = false },
            title = { Text("Edit Username") },
            text = {
                OutlinedTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    label = { Text("Username") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateUsername(newUsername)
                    showEditUsernameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditUsernameDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SleekBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- PROFILE HEADER CARD (Matching rounded-[28px]) ---
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
                // Avatar with Mascot
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(SleekCardHighlight)
                        .border(2.dp, SleekPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ScrollyCharacter(scrollCount = 15, size = 68.dp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { 
                            newUsername = profile.username
                            showEditUsernameDialog = true
                        }
                        .padding(4.dp)
                ) {
                    Text(
                        text = "@${profile.username}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SleekTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Username",
                        tint = SleekTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = "Level ${profile.currentLevel} Focus Scholar",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = SleekPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // XP Progress Bar
                val xpFraction = (profile.currentXp.toFloat() / profile.xpForNextLevel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "XP Progress",
                        style = MaterialTheme.typography.labelSmall.copy(color = SleekTextMuted)
                    )
                    Text(
                        text = "${profile.currentXp} / ${profile.xpForNextLevel} XP",
                        style = MaterialTheme.typography.labelSmall.copy(color = SleekTextPrimary, fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { xpFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = SleekPrimary,
                    trackColor = SleekCardHighlight,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- LIFETIME STATS SUMMARY ROW ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, SleekBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SleekCardSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "14,832",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = SleekTextPrimary
                        )
                    )
                    Text(
                        text = "Total Scrolls",
                        style = MaterialTheme.typography.labelSmall.copy(color = SleekTextMuted),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, SleekBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SleekCardSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "11 Days",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = SleekPrimary
                        )
                    )
                    Text(
                        text = "Best Streak",
                        style = MaterialTheme.typography.labelSmall.copy(color = SleekTextMuted),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, SleekBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SleekCardSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "23W / 17L",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = SleekGreen
                        )
                    )
                    Text(
                        text = "Battles",
                        style = MaterialTheme.typography.labelSmall.copy(color = SleekTextMuted),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- ACHIEVEMENTS GRID ---
        Text(
            text = "Achievements & Badges",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SleekTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        achievements.chunked(2).forEach { rowAchievements ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowAchievements.forEach { ach ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                1.dp,
                                if (ach.isUnlocked) SleekPrimary.copy(alpha = 0.4f) else SleekBorder,
                                RoundedCornerShape(20.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ach.isUnlocked) SleekCardSurface else SleekCardSurfaceSecondary
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (ach.isUnlocked) SleekPillBg else SleekBorder.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (ach.isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (ach.isUnlocked) SleekHeroText else SleekTextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (ach.isUnlocked) {
                                    Text(
                                        text = "UNLOCKED",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = SleekGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = ach.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (ach.isUnlocked) SleekTextPrimary else SleekTextMuted
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = ach.description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = SleekTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            )
                        }
                    }
                }
                if (rowAchievements.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- PRIVACY-FIRST COMMITMENT ---
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
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = "Privacy",
                        tint = SleekGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Privacy-First Architecture",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SleekTextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Scrolly tracks scrolling behavior, not what you are watching. We never record video titles, messages, passwords, or personal screens. All statistics remain exclusively on your device (unless you explicitly connect with friends).",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SleekTextSecondary,
                        lineHeight = 18.sp
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // --- FRIENDS & INVITE SECTION ---
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
                    .padding(24.dp)
            ) {
                Text(
                    text = "Friends",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = SleekTextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                if (friends.isEmpty()) {
                    Text(
                        text = "You haven't connected with any friends yet.",
                        style = MaterialTheme.typography.bodySmall.copy(color = SleekTextMuted)
                    )
                } else {
                    friends.forEach { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "@${friend.username}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = SleekTextPrimary
                                )
                            )
                            Text(
                                text = "${friend.todayScrolls} scrolls today",
                                style = MaterialTheme.typography.bodySmall.copy(color = SleekPrimary)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        val uid = viewModel.getMyUid()
                        if (uid != null) {
                            val inviteLink = "scrolly://invite?uid=$uid"
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Connect with me on Scrolly and let's battle our screen time! $inviteLink")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Invite Friend"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Invite a Friend", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
