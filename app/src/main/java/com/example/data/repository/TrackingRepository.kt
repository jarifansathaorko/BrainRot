package com.example.data.repository

import com.example.data.local.ScrollyDatabase
import com.example.data.local.dao.ScrollyDao
import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.AppStatsEntity
import com.example.data.local.entity.DailyStatsEntity
import com.example.data.local.entity.ScrollEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LimitReachedEvent(
    val packageName: String,
    val appName: String,
    val currentScrolls: Int,
    val limit: Int
)

class TrackingRepository(
    private val dao: ScrollyDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val mutex = Mutex()
    private val _limitReachedEvents = MutableSharedFlow<LimitReachedEvent>(extraBufferCapacity = 5)
    val limitReachedEvents = _limitReachedEvents.asSharedFlow()

    private val _todayTotalScrolls = MutableStateFlow(0)
    val todayTotalScrolls = _todayTotalScrolls.asStateFlow()

    init {
        scope.launch {
            val today = ScrollyDatabase.getTodayDate()
            val initial = dao.getDailyStats(today)?.totalScrolls ?: 0
            _todayTotalScrolls.value = initial
            dao.getDailyStatsFlow(today).collect { stats ->
                if (stats != null && stats.totalScrolls >= _todayTotalScrolls.value) {
                    _todayTotalScrolls.value = stats.totalScrolls
                }
            }
        }
    }

    fun getTodayStatsFlow(): Flow<DailyStatsEntity?> {
        val today = ScrollyDatabase.getTodayDate()
        return dao.getDailyStatsFlow(today)
    }

    fun getDailyStatsFlow(date: String): Flow<DailyStatsEntity?> {
        return dao.getDailyStatsFlow(date)
    }

    fun getTodayAppStatsFlow(): Flow<List<AppStatsEntity>> {
        val today = ScrollyDatabase.getTodayDate()
        return dao.getAppStatsForDateFlow(today)
    }

    fun getWeeklyStatsFlow(): Flow<List<DailyStatsEntity>> {
        return dao.getRecentDailyStatsFlow()
    }

    fun getAllDailyStatsFlow(): Flow<List<DailyStatsEntity>> {
        return dao.getAllDailyStatsFlow()
    }

    fun getDailyStatsBetweenFlow(startDate: String, endDate: String): Flow<List<DailyStatsEntity>> {
        return dao.getDailyStatsBetweenFlow(startDate, endDate)
    }

    fun getDailyStatsLikeFlow(pattern: String): Flow<List<DailyStatsEntity>> {
        return dao.getDailyStatsLikeFlow(pattern)
    }

    fun getAppStatsSummaryForDateFlow(date: String): Flow<List<com.example.data.local.dao.AppLifetimeTotal>> {
        return dao.getAppStatsSummaryForDateFlow(date)
    }

    fun getAppStatsSummaryBetweenFlow(startDate: String, endDate: String): Flow<List<com.example.data.local.dao.AppLifetimeTotal>> {
        return dao.getAppStatsSummaryBetweenFlow(startDate, endDate)
    }

    fun getAppStatsSummaryLikeFlow(pattern: String): Flow<List<com.example.data.local.dao.AppLifetimeTotal>> {
        return dao.getAppStatsSummaryLikeFlow(pattern)
    }

    fun getAppLimitsFlow(): Flow<List<AppLimitEntity>> {
        return dao.getAllLimitsFlow()
    }

    suspend fun recordScroll(packageName: String, appName: String, delta: Int = 1) {
        // 1. Instant zero-latency memory update
        _todayTotalScrolls.value += delta

        // 2. Persist safely in background IO
        mutex.withLock {
            val today = ScrollyDatabase.getTodayDate()

            // 1. Update Daily Stats
            val currentDaily = dao.getDailyStats(today) ?: DailyStatsEntity(
                date = today,
                totalScrolls = 0,
                goal = 100,
                isGoalMet = false,
                xpEarned = 0
            )
            val newTotal = currentDaily.totalScrolls + delta
            val updatedDaily = currentDaily.copy(
                totalScrolls = newTotal,
                isGoalMet = newTotal <= currentDaily.goal
            )
            dao.upsertDailyStats(updatedDaily)

            // 2. Update App Stats
            val currentAppStat = dao.getAppStat(today, packageName) ?: AppStatsEntity(
                date = today,
                packageName = packageName,
                appName = appName,
                scrollCount = 0
            )
            val newAppScrolls = currentAppStat.scrollCount + delta
            dao.upsertAppStats(currentAppStat.copy(scrollCount = newAppScrolls))

            // 3. Record scroll event
            dao.insertScrollEvent(
                ScrollEventEntity(
                    packageName = packageName,
                    appName = appName,
                    scrollDelta = delta
                )
            )

            // 4. Check App Limits
            val limit = dao.getLimit(packageName)
            if (limit != null && limit.isEnabled && newAppScrolls >= limit.dailyLimit && !limit.isBlocked) {
                dao.upsertLimit(limit.copy(isBlocked = true))
                _limitReachedEvents.emit(
                    LimitReachedEvent(
                        packageName = packageName,
                        appName = appName,
                        currentScrolls = newAppScrolls,
                        limit = limit.dailyLimit
                    )
                )
            }
        }
    }

    suspend fun updateAppLimit(packageName: String, newLimit: Int, warningThreshold: Int, isEnabled: Boolean) {
        val existing = dao.getLimit(packageName) ?: AppLimitEntity(
            packageName = packageName,
            appName = when (packageName) {
                "com.instagram.android" -> "Instagram"
                "com.google.android.youtube" -> "YouTube Shorts"
                "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme" -> "TikTok"
                "com.facebook.katana" -> "Facebook"
                "com.snapchat.android" -> "Snapchat"
                else -> "App"
            },
            dailyLimit = newLimit,
            warningThreshold = warningThreshold,
            isEnabled = isEnabled
        )
        dao.upsertLimit(
            existing.copy(
                dailyLimit = newLimit,
                warningThreshold = warningThreshold,
                isEnabled = isEnabled,
                isBlocked = false // Reset block when limit is adjusted
            )
        )
    }

    suspend fun unblockAppTemporarily(packageName: String, extraScrolls: Int) {
        val limit = dao.getLimit(packageName) ?: return
        dao.upsertLimit(
            limit.copy(
                dailyLimit = limit.dailyLimit + extraScrolls,
                isBlocked = false
            )
        )
    }

    suspend fun setDailyGoal(goal: Int) {
        val today = ScrollyDatabase.getTodayDate()
        val currentDaily = dao.getDailyStats(today) ?: DailyStatsEntity(
            date = today,
            totalScrolls = 0,
            goal = goal
        )
        dao.upsertDailyStats(currentDaily.copy(goal = goal))
    }
}
