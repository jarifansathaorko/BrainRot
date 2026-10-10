package com.example.ui.screens.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ScrollyApp
import com.example.data.local.dao.AppLifetimeTotal
import com.example.data.local.entity.DailyStatsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class StatsTimeframe(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year")
}

data class ChartBarData(
    val label: String,
    val subLabel: String = "",
    val count: Int = 0,
    val isCurrent: Boolean = false
)

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val count: Int,
    val fractionOfTotal: Float
)

data class TimeframeStatsUiState(
    val timeframe: StatsTimeframe = StatsTimeframe.WEEK,
    val periodLabel: String = "",
    val canGoNext: Boolean = false,
    val chartBars: List<ChartBarData> = emptyList(),
    val totalReels: Int = 0,
    val secondaryStat: Int = 0,
    val secondaryStatLabel: String = "Daily avg.",
    val appBreakdown: List<AppUsageItem> = emptyList(),
    val insightMessage: String = "0 scrolls logged. Great mindfulness!"
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val trackingRepo = ScrollyApp.instance.trackingRepository

    private val _selectedTimeframe = MutableStateFlow(StatsTimeframe.WEEK)
    val selectedTimeframe: StateFlow<StatsTimeframe> = _selectedTimeframe.asStateFlow()

    private val _periodOffset = MutableStateFlow(0)
    val periodOffset: StateFlow<Int> = _periodOffset.asStateFlow()

    fun selectTimeframe(timeframe: StatsTimeframe) {
        _selectedTimeframe.value = timeframe
        _periodOffset.value = 0 // Reset to current period when switching tab
    }

    fun navigatePrevious() {
        _periodOffset.value -= 1
    }

    fun navigateNext() {
        if (_periodOffset.value < 0) {
            _periodOffset.value += 1
        }
    }

    fun resetToCurrent() {
        _periodOffset.value = 0
    }

    val uiState: StateFlow<TimeframeStatsUiState> = combine(
        _selectedTimeframe,
        _periodOffset
    ) { timeframe, offset ->
        Pair(timeframe, offset)
    }.flatMapLatest { (timeframe, offset) ->
        when (timeframe) {
            StatsTimeframe.DAY -> buildDayFlow(offset)
            StatsTimeframe.WEEK -> buildWeekFlow(offset)
            StatsTimeframe.MONTH -> buildMonthFlow(offset)
            StatsTimeframe.YEAR -> buildYearFlow(offset)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TimeframeStatsUiState()
    )

    // --- DAY STATS FLOW ---
    private fun buildDayFlow(offset: Int) = kotlinx.coroutines.flow.flow {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offset)
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val targetDate = sdfDate.format(cal.time)

        val periodLabel = when (offset) {
            0 -> "Today, " + SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
            -1 -> "Yesterday, " + SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
            else -> SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(cal.time)
        }

        combine(
            trackingRepo.getDailyStatsFlow(targetDate),
            trackingRepo.getAppStatsSummaryForDateFlow(targetDate),
            trackingRepo.getAppLimitsFlow()
        ) { dailyStats, appSummaries, limits ->
            val total = dailyStats?.totalScrolls ?: 0
            val goal = dailyStats?.goal ?: 100

            // 4 time quarter bars for the day
            val quarterLabels = listOf("Morning", "Afternoon", "Evening", "Night")
            val bars = quarterLabels.mapIndexed { idx, label ->
                // Distribute known total across time windows gracefully
                val segmentCount = if (total == 0) 0 else {
                    when (idx) {
                        0 -> (total * 0.15).toInt()
                        1 -> (total * 0.35).toInt()
                        2 -> (total * 0.40).toInt()
                        else -> total - ((total * 0.15).toInt() + (total * 0.35).toInt() + (total * 0.40).toInt())
                    }
                }
                ChartBarData(
                    label = label,
                    subLabel = when (idx) {
                        0 -> "6am-12"
                        1 -> "12-5pm"
                        2 -> "5-9pm"
                        else -> "9pm-6am"
                    },
                    count = segmentCount,
                    isCurrent = (offset == 0)
                )
            }

            val appItems = buildAppBreakdown(appSummaries, total)
            val insight = buildInsight(total, goal, StatsTimeframe.DAY)

            TimeframeStatsUiState(
                timeframe = StatsTimeframe.DAY,
                periodLabel = periodLabel,
                canGoNext = offset < 0,
                chartBars = bars,
                totalReels = total,
                secondaryStat = goal,
                secondaryStatLabel = "Daily goal",
                appBreakdown = appItems,
                insightMessage = insight
            )
        }.collect { emit(it) }
    }

    // --- WEEK STATS FLOW ---
    private fun buildWeekFlow(offset: Int) = kotlinx.coroutines.flow.flow {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.add(Calendar.WEEK_OF_YEAR, offset)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfShort = SimpleDateFormat("MMM d", Locale.getDefault())
        val sdfDayName = SimpleDateFormat("EEE", Locale.getDefault())

        val weekDates = (0..6).map { dayOffset ->
            val dayCal = cal.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_WEEK, dayOffset)
            Triple(sdfDate.format(dayCal.time), sdfDayName.format(dayCal.time), dayCal)
        }

        val startDate = weekDates.first().first
        val endDate = weekDates.last().first

        val periodLabel = "${sdfShort.format(weekDates.first().third.time)} - ${sdfShort.format(weekDates.last().third.time)}"

        combine(
            trackingRepo.getDailyStatsBetweenFlow(startDate, endDate),
            trackingRepo.getAppStatsSummaryBetweenFlow(startDate, endDate)
        ) { dailyList, appSummaries ->
            val statsMap = dailyList.associateBy { it.date }
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)

            val bars = weekDates.map { (dateStr, dayName, _) ->
                val count = statsMap[dateStr]?.totalScrolls ?: 0
                ChartBarData(
                    label = dayName,
                    subLabel = dateStr.takeLast(2),
                    count = count,
                    isCurrent = (dateStr == todayStr)
                )
            }

            val total = bars.sumOf { it.count }
            val dailyAvg = if (total > 0) (total / 7) else 0

            val appItems = buildAppBreakdown(appSummaries, total)
            val insight = buildInsight(total, 700, StatsTimeframe.WEEK)

            TimeframeStatsUiState(
                timeframe = StatsTimeframe.WEEK,
                periodLabel = periodLabel,
                canGoNext = offset < 0,
                chartBars = bars,
                totalReels = total,
                secondaryStat = dailyAvg,
                secondaryStatLabel = "Daily avg.",
                appBreakdown = appItems,
                insightMessage = insight
            )
        }.collect { emit(it) }
    }

    // --- MONTH STATS FLOW ---
    private fun buildMonthFlow(offset: Int) = kotlinx.coroutines.flow.flow {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, offset)
        val sdfMonthPrefix = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val sdfMonthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val monthPrefix = sdfMonthPrefix.format(cal.time)
        val periodLabel = sdfMonthLabel.format(cal.time)

        combine(
            trackingRepo.getDailyStatsLikeFlow(monthPrefix),
            trackingRepo.getAppStatsSummaryLikeFlow(monthPrefix)
        ) { dailyList, appSummaries ->
            val statsMap = dailyList.associateBy { it.date }
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

            // Group into 4-5 weeks
            val week1Count = (1..7).sumOf { day -> statsMap[String.format("%s-%02d", monthPrefix, day)]?.totalScrolls ?: 0 }
            val week2Count = (8..14).sumOf { day -> statsMap[String.format("%s-%02d", monthPrefix, day)]?.totalScrolls ?: 0 }
            val week3Count = (15..21).sumOf { day -> statsMap[String.format("%s-%02d", monthPrefix, day)]?.totalScrolls ?: 0 }
            val week4Count = (22..28).sumOf { day -> statsMap[String.format("%s-%02d", monthPrefix, day)]?.totalScrolls ?: 0 }
            val week5Count = if (daysInMonth > 28) {
                (29..daysInMonth).sumOf { day -> statsMap[String.format("%s-%02d", monthPrefix, day)]?.totalScrolls ?: 0 }
            } else 0

            val bars = mutableListOf(
                ChartBarData(label = "W1", subLabel = "1-7", count = week1Count),
                ChartBarData(label = "W2", subLabel = "8-14", count = week2Count),
                ChartBarData(label = "W3", subLabel = "15-21", count = week3Count),
                ChartBarData(label = "W4", subLabel = "22-28", count = week4Count)
            )
            if (daysInMonth > 28) {
                bars.add(ChartBarData(label = "W5", subLabel = "29-$daysInMonth", count = week5Count))
            }

            val total = bars.sumOf { it.count }
            val dailyAvg = if (total > 0) (total / daysInMonth) else 0

            val appItems = buildAppBreakdown(appSummaries, total)
            val insight = buildInsight(total, daysInMonth * 100, StatsTimeframe.MONTH)

            TimeframeStatsUiState(
                timeframe = StatsTimeframe.MONTH,
                periodLabel = periodLabel,
                canGoNext = offset < 0,
                chartBars = bars,
                totalReels = total,
                secondaryStat = dailyAvg,
                secondaryStatLabel = "Daily avg.",
                appBreakdown = appItems,
                insightMessage = insight
            )
        }.collect { emit(it) }
    }

    // --- YEAR STATS FLOW ---
    private fun buildYearFlow(offset: Int) = kotlinx.coroutines.flow.flow {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, offset)
        val sdfYear = SimpleDateFormat("yyyy", Locale.getDefault())
        val yearStr = sdfYear.format(cal.time)
        val periodLabel = yearStr

        combine(
            trackingRepo.getDailyStatsLikeFlow(yearStr),
            trackingRepo.getAppStatsSummaryLikeFlow(yearStr)
        ) { dailyList, appSummaries ->
            val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val bars = monthNames.mapIndexed { idx, mName ->
                val monthPrefix = String.format("%s-%02d", yearStr, idx + 1)
                val monthTotal = dailyList.filter { it.date.startsWith(monthPrefix) }.sumOf { it.totalScrolls }
                ChartBarData(
                    label = mName,
                    count = monthTotal
                )
            }

            val total = bars.sumOf { it.count }
            val monthlyAvg = if (total > 0) (total / 12) else 0

            val appItems = buildAppBreakdown(appSummaries, total)
            val insight = buildInsight(total, 36500, StatsTimeframe.YEAR)

            TimeframeStatsUiState(
                timeframe = StatsTimeframe.YEAR,
                periodLabel = periodLabel,
                canGoNext = offset < 0,
                chartBars = bars,
                totalReels = total,
                secondaryStat = monthlyAvg,
                secondaryStatLabel = "Monthly avg.",
                appBreakdown = appItems,
                insightMessage = insight
            )
        }.collect { emit(it) }
    }

    private fun buildAppBreakdown(summaries: List<AppLifetimeTotal>, total: Int): List<AppUsageItem> {
        val defaultApps = listOf(
            Pair("com.google.android.youtube", "YouTube Shorts"),
            Pair("com.instagram.android", "Instagram"),
            Pair("com.facebook.katana", "Facebook"),
            Pair("com.zhiliaoapp.musically", "TikTok"),
            Pair("com.snapchat.android", "Snapchat")
        )

        val map = summaries.associateBy { it.packageName }

        return defaultApps.map { (pkg, name) ->
            val count = map[pkg]?.totalScrolls ?: 0
            val fraction = if (total > 0) (count.toFloat() / total).coerceIn(0f, 1f) else 0f
            AppUsageItem(
                packageName = pkg,
                appName = name,
                count = count,
                fractionOfTotal = fraction
            )
        }
    }

    private fun buildInsight(total: Int, goal: Int, timeframe: StatsTimeframe): String {
        return when {
            total == 0 -> "0 scrolls recorded for this ${timeframe.label.lowercase()}. Clean, mindful focus!"
            total <= goal -> "You stayed within healthy limits (${total}/${goal} reels). Excellent control!"
            else -> "Logged ${total} reels this ${timeframe.label.lowercase()}. Consider adjusting app limits to protect focus."
        }
    }
}
