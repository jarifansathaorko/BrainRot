package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey
    val date: String, // format: YYYY-MM-DD
    val totalScrolls: Int,
    val goal: Int = 100,
    val isGoalMet: Boolean = false,
    val xpEarned: Int = 0
)

@Entity(tableName = "app_stats")
data class AppStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val packageName: String,
    val appName: String,
    val scrollCount: Int
)

@Entity(tableName = "scroll_events")
data class ScrollEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val appName: String,
    val scrollDelta: Int = 1
)

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dailyLimit: Int,
    val warningThreshold: Int,
    val isBlocked: Boolean = false,
    val isEnabled: Boolean = true
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val targetValue: Int,
    val currentValue: Int = 0,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)

@Entity(tableName = "friend_battles")
data class FriendBattleEntity(
    @PrimaryKey
    val id: String,
    val friendName: String,
    val friendUsername: String,
    val userScrolls: Int,
    val friendScrolls: Int,
    val date: String, // YYYY-MM-DD
    val status: String // "WINNING", "LOSING", "TIED", "COMPLETED"
)
