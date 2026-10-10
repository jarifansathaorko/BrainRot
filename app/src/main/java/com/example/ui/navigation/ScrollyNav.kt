package com.example.ui.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.battles.BattlesScreen
import com.example.ui.screens.battles.BattlesViewModel
import com.example.ui.screens.block.BlockScreen
import com.example.ui.screens.block.BlockViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.profile.ProfileViewModel
import com.example.ui.screens.stats.StatsScreen
import com.example.ui.screens.stats.StatsViewModel
import com.example.ui.theme.SleekBg
import com.example.ui.theme.SleekBorder
import com.example.ui.theme.SleekCardSurface
import com.example.ui.theme.SleekPillBg
import com.example.ui.theme.SleekPillText
import com.example.ui.theme.SleekTextMuted

enum class ScrollyTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    STATS("Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    BATTLES("Battles", Icons.Filled.FlashOn, Icons.Outlined.FlashOn),
    BLOCK("Block", Icons.Filled.Block, Icons.Outlined.Block),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun ScrollyAppScaffold() {
    var selectedTab by rememberSaveable { mutableStateOf(ScrollyTab.HOME) }

    val homeViewModel: HomeViewModel = viewModel()
    val statsViewModel: StatsViewModel = viewModel()
    val battlesViewModel: BattlesViewModel = viewModel()
    val blockViewModel: BlockViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()

    val isNotchPreviewVisible by homeViewModel.isNotchBarPreviewVisible.collectAsStateWithLifecycle()
    val notchConfig by homeViewModel.notchConfig.collectAsStateWithLifecycle()
    val liveTotalScrolls by homeViewModel.liveTotalScrolls.collectAsStateWithLifecycle()
    val todayStats by homeViewModel.todayStats.collectAsStateWithLifecycle()
    val totalScrolls = if (liveTotalScrolls > 0) liveTotalScrolls else (todayStats?.totalScrolls ?: 0)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = SleekBg,
            bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .border(1.dp, SleekBorder, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .navigationBarsPadding(),
                containerColor = SleekCardSurface,
                tonalElevation = 0.dp
            ) {
                ScrollyTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SleekPillText,
                            selectedTextColor = SleekPillText,
                            indicatorColor = SleekPillBg,
                            unselectedIconColor = SleekTextMuted,
                            unselectedTextColor = SleekTextMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                ScrollyTab.HOME -> HomeScreen(viewModel = homeViewModel)
                ScrollyTab.STATS -> StatsScreen(viewModel = statsViewModel)
                ScrollyTab.BATTLES -> BattlesScreen(viewModel = battlesViewModel)
                ScrollyTab.BLOCK -> BlockScreen(viewModel = blockViewModel)
                ScrollyTab.PROFILE -> ProfileScreen(viewModel = profileViewModel)
            }
        }
    }

    // Dynamic Island Preview rendered at absolute screen coordinates (0 = top of display)
    com.example.ui.components.NotchBarPreview(
        scrollCount = totalScrolls,
        appName = "Reels",
        dailyGoal = todayStats?.goal ?: 100,
        visible = isNotchPreviewVisible,
        config = notchConfig,
        onDismiss = { homeViewModel.setNotchBarPreviewVisible(false) }
    )
}
}
