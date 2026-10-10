package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AchievementEntity
import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.AppStatsEntity
import com.example.data.local.entity.DailyStatsEntity
import com.example.data.local.entity.FriendBattleEntity
import com.example.data.local.entity.ScrollEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrollyDao {

    // --- Daily Stats ---
    @Query("SELECT * FROM daily_stats WHERE date = :date LIMIT 1")
    fun getDailyStatsFlow(date: String): Flow<DailyStatsEntity?>

    @Query("SELECT * FROM daily_stats WHERE date = :date LIMIT 1")
    suspend fun getDailyStats(date: String): DailyStatsEntity?

    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT 7")
    fun getRecentDailyStatsFlow(): Flow<List<DailyStatsEntity>>

    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT 7")
    suspend fun getRecentDailyStats(): List<DailyStatsEntity>

    @Query("SELECT * FROM daily_stats ORDER BY date ASC")
    fun getAllDailyStatsFlow(): Flow<List<DailyStatsEntity>>

    @Query("SELECT * FROM daily_stats WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getDailyStatsBetweenFlow(startDate: String, endDate: String): Flow<List<DailyStatsEntity>>

    @Query("SELECT * FROM daily_stats WHERE date LIKE :pattern || '%' ORDER BY date ASC")
    fun getDailyStatsLikeFlow(pattern: String): Flow<List<DailyStatsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStats(stats: DailyStatsEntity)

    // --- App Stats ---
    @Query("SELECT * FROM app_stats WHERE date = :date ORDER BY scrollCount DESC")
    fun getAppStatsForDateFlow(date: String): Flow<List<AppStatsEntity>>

    @Query("SELECT packageName, appName, SUM(scrollCount) as totalScrolls FROM app_stats WHERE date = :date GROUP BY packageName ORDER BY totalScrolls DESC")
    fun getAppStatsSummaryForDateFlow(date: String): Flow<List<AppLifetimeTotal>>

    @Query("SELECT packageName, appName, SUM(scrollCount) as totalScrolls FROM app_stats WHERE date >= :startDate AND date <= :endDate GROUP BY packageName ORDER BY totalScrolls DESC")
    fun getAppStatsSummaryBetweenFlow(startDate: String, endDate: String): Flow<List<AppLifetimeTotal>>

    @Query("SELECT packageName, appName, SUM(scrollCount) as totalScrolls FROM app_stats WHERE date LIKE :pattern || '%' GROUP BY packageName ORDER BY totalScrolls DESC")
    fun getAppStatsSummaryLikeFlow(pattern: String): Flow<List<AppLifetimeTotal>>

    @Query("SELECT * FROM app_stats WHERE date = :date AND packageName = :packageName LIMIT 1")
    suspend fun getAppStat(date: String, packageName: String): AppStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppStats(stats: AppStatsEntity)

    @Query("SELECT packageName, appName, SUM(scrollCount) as totalScrolls FROM app_stats GROUP BY packageName ORDER BY totalScrolls DESC")
    fun getLifetimeAppTotalsFlow(): Flow<List<AppLifetimeTotal>>

    // --- Scroll Events ---
    @Insert
    suspend fun insertScrollEvent(event: ScrollEventEntity)

    @Query("SELECT COUNT(*) FROM scroll_events WHERE timestamp >= :sinceTimestamp")
    suspend fun getScrollCountSince(sinceTimestamp: Long): Int

    // --- Limits ---
    @Query("SELECT * FROM app_limits")
    fun getAllLimitsFlow(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName LIMIT 1")
    suspend fun getLimit(packageName: String): AppLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimit(limit: AppLimitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimits(limits: List<AppLimitEntity>)

    // --- Achievements ---
    @Query("SELECT * FROM achievements")
    fun getAllAchievementsFlow(): Flow<List<AchievementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAchievements(achievements: List<AchievementEntity>)

    @Update
    suspend fun updateAchievement(achievement: AchievementEntity)

    // --- Friend Battles ---
    @Query("SELECT * FROM friend_battles ORDER BY date DESC")
    fun getAllBattlesFlow(): Flow<List<FriendBattleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBattles(battles: List<FriendBattleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBattle(battle: FriendBattleEntity)
}

data class AppLifetimeTotal(
    val packageName: String,
    val appName: String,
    val totalScrolls: Int
)
