package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ScrollyDao
import com.example.data.local.entity.AchievementEntity
import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.AppStatsEntity
import com.example.data.local.entity.DailyStatsEntity
import com.example.data.local.entity.FriendBattleEntity
import com.example.data.local.entity.ScrollEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        DailyStatsEntity::class,
        AppStatsEntity::class,
        ScrollEventEntity::class,
        AppLimitEntity::class,
        AchievementEntity::class,
        FriendBattleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ScrollyDatabase : RoomDatabase() {
    abstract fun scrollyDao(): ScrollyDao

    companion object {
        @Volatile
        private var INSTANCE: ScrollyDatabase? = null

        fun getInstance(context: Context): ScrollyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScrollyDatabase::class.java,
                    "scrolly_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getTodayDate(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }

        fun getDateOffset(daysOffset: Int): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, daysOffset)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(cal.time)
        }

        fun getMonthPrefix(offsetMonths: Int = 0): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, offsetMonths)
            val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            return sdf.format(cal.time)
        }

        fun getYearPrefix(offsetYears: Int = 0): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.YEAR, offsetYears)
            val sdf = SimpleDateFormat("yyyy", Locale.getDefault())
            return sdf.format(cal.time)
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                populateInitialData(INSTANCE?.scrollyDao())
            }
        }

        private suspend fun populateInitialData(dao: ScrollyDao?) {
            dao ?: return

            // 1. Prepopulate default App Limits
            val defaultLimits = listOf(
                AppLimitEntity(
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    dailyLimit = 100,
                    warningThreshold = 80,
                    isBlocked = false,
                    isEnabled = true
                ),
                AppLimitEntity(
                    packageName = "com.google.android.youtube",
                    appName = "YouTube Shorts",
                    dailyLimit = 75,
                    warningThreshold = 60,
                    isBlocked = false,
                    isEnabled = true
                ),
                AppLimitEntity(
                    packageName = "com.facebook.katana",
                    appName = "Facebook",
                    dailyLimit = 60,
                    warningThreshold = 45,
                    isBlocked = false,
                    isEnabled = true
                ),
                AppLimitEntity(
                    packageName = "com.snapchat.android",
                    appName = "Snapchat",
                    dailyLimit = 50,
                    warningThreshold = 35,
                    isBlocked = false,
                    isEnabled = true
                ),
                AppLimitEntity(
                    packageName = "com.zhiliaoapp.musically",
                    appName = "TikTok",
                    dailyLimit = 80,
                    warningThreshold = 60,
                    isBlocked = false,
                    isEnabled = true
                )
            )
            dao.upsertLimits(defaultLimits)

            // 2. Prepopulate Achievements starting from 0 progress
            val achievements = listOf(
                AchievementEntity(
                    id = "first_step",
                    title = "First Step",
                    description = "Log your very first short-form scroll session",
                    iconName = "flag",
                    targetValue = 1,
                    currentValue = 0,
                    isUnlocked = false,
                    unlockedAt = null
                ),
                AchievementEntity(
                    id = "under_100",
                    title = "Under 100",
                    description = "Stay under 100 scrolls in a single day",
                    iconName = "shield",
                    targetValue = 100,
                    currentValue = 0,
                    isUnlocked = false,
                    unlockedAt = null
                ),
                AchievementEntity(
                    id = "streak_7",
                    title = "7-Day Streak",
                    description = "Maintain a 7-day streak under your daily limit",
                    iconName = "fire",
                    targetValue = 7,
                    currentValue = 0,
                    isUnlocked = false
                ),
                AchievementEntity(
                    id = "touch_grass",
                    title = "Touch Grass Champion",
                    description = "Keep daily scrolls below 30 on a weekend day",
                    iconName = "grass",
                    targetValue = 30,
                    currentValue = 0,
                    isUnlocked = false,
                    unlockedAt = null
                ),
                AchievementEntity(
                    id = "battle_winner",
                    title = "Scroll Ninja",
                    description = "Defeat a friend in a 1v1 Scroll Battle",
                    iconName = "sword",
                    targetValue = 1,
                    currentValue = 0,
                    isUnlocked = false,
                    unlockedAt = null
                ),
                AchievementEntity(
                    id = "zen_master",
                    title = "Zen Master",
                    description = "Complete 5 mindful focus breaks to earn bonus scrolls",
                    iconName = "lotus",
                    targetValue = 5,
                    currentValue = 0,
                    isUnlocked = false
                )
            )
            dao.upsertAchievements(achievements)

            // 3. First-time install: Today's progress starts strictly at 0
            val today = getTodayDate()
            dao.upsertDailyStats(
                DailyStatsEntity(
                    date = today,
                    totalScrolls = 0,
                    goal = 100,
                    isGoalMet = true,
                    xpEarned = 0
                )
            )

            // Initialize app stats for supported apps at 0 scrolls
            dao.upsertAppStats(AppStatsEntity(date = today, packageName = "com.instagram.android", appName = "Instagram", scrollCount = 0))
            dao.upsertAppStats(AppStatsEntity(date = today, packageName = "com.google.android.youtube", appName = "YouTube Shorts", scrollCount = 0))
            dao.upsertAppStats(AppStatsEntity(date = today, packageName = "com.facebook.katana", appName = "Facebook", scrollCount = 0))
            dao.upsertAppStats(AppStatsEntity(date = today, packageName = "com.snapchat.android", appName = "Snapchat", scrollCount = 0))
            dao.upsertAppStats(AppStatsEntity(date = today, packageName = "com.zhiliaoapp.musically", appName = "TikTok", scrollCount = 0))

            // 4. Friend Battles initial invites starting at 0 user scrolls
            val battles = listOf(
                FriendBattleEntity(
                    id = "battle_alex",
                    friendName = "Alex Rivera",
                    friendUsername = "@alex_r",
                    userScrolls = 0,
                    friendScrolls = 42,
                    date = today,
                    status = "WINNING"
                ),
                FriendBattleEntity(
                    id = "battle_sarah",
                    friendName = "Sarah Chen",
                    friendUsername = "@schen",
                    userScrolls = 0,
                    friendScrolls = 35,
                    date = today,
                    status = "WINNING"
                )
            )
            dao.upsertBattles(battles)
        }
    }
}
