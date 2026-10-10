package com.example.ui.mascot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.TrackingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel dedicated to managing the Brain Mascot state, observing the user's
 * scroll activity, calculating daily scroll intensity, and driving expressive visual changes.
 */
class BrainMascotViewModel(
    private val trackingRepository: TrackingRepository
) : ViewModel() {

    private val _interactionReaction = MutableStateFlow<String?>(null)
    val interactionReaction: StateFlow<String?> = _interactionReaction.asStateFlow()

    /**
     * Reactive state of the Brain Mascot, automatically recalculated whenever
     * scroll count changes, daily goal updates, or the user interacts with the mascot.
     */
    val mascotState: StateFlow<BrainMascotState> = combine(
        trackingRepository.todayTotalScrolls,
        trackingRepository.getTodayStatsFlow(),
        _interactionReaction
    ) { liveScrolls, dailyStats, interaction ->
        val effectiveScrolls = if (liveScrolls > 0) liveScrolls else (dailyStats?.totalScrolls ?: 0)
        val dailyGoal = dailyStats?.goal ?: 100
        BrainMascotState.fromDailyScrolls(
            scrollCount = effectiveScrolls,
            dailyGoal = dailyGoal,
            interactionFeedback = interaction
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BrainMascotState.fromDailyScrolls(0, 100)
    )

    /**
     * Responds to user tapping on the Brain Mascot with dynamic voice quotes
     * matching the current scroll intensity level.
     */
    fun onMascotTapped() {
        val currentState = mascotState.value
        val reaction = when (currentState.dailyScrollIntensity) {
            ScrollIntensity.MINIMAL -> listOf(
                "✨ Ahh, fresh neurons! Feeling sharp and energized!",
                "🧠 Crystal clear mind! Ready to crush today's goals!",
                "🌟 Look at that focus! Keep shining!"
            ).random()

            ScrollIntensity.LOW -> listOf(
                "👀 Cruising along nicely! Keep your thumb intentional.",
                "🌿 Still balanced! Don't let autoplay steal your evening.",
                "💡 A little entertainment is fine, stay mindful!"
            ).random()

            ScrollIntensity.MODERATE -> listOf(
                "⚠️ Starting to feel that short-form vortex pull...",
                "👀 Hey friend! Blink twice and look out the window.",
                "🧘 Take a deep breath before the next reel!"
            ).random()

            ScrollIntensity.ELEVATED -> listOf(
                "💦 Whew! My little folds are getting warm!",
                "⚡ Focus is slipping! How about a sip of water?",
                "⏳ Nearing your limit! Time to wrap it up soon."
            ).random()

            ScrollIntensity.INTENSE -> listOf(
                "🍳 Ouch! My synapses are sizzling in butter!",
                "🔥 Dopamine overload! Please save me from the feed!",
                "🛑 Limit exceeded! Put the phone face-down for 10 minutes!"
            ).random()

            ScrollIntensity.EXTREME -> listOf(
                "💀 Nuclear brainrot emergency! My neurons are melting!",
                "🚨 Put down the phone! Step outside into the real world!",
                "🌋 Doomscroll catastrophe! Total cognitive overload!"
            ).random()
        }

        _interactionReaction.value = reaction

        // Auto-clear interaction feedback after 6 seconds to return to default quote
        viewModelScope.launch {
            delay(6000)
            if (_interactionReaction.value == reaction) {
                _interactionReaction.value = null
            }
        }
    }

    /**
     * Clears any active temporary speech bubble / reaction.
     */
    fun clearTapReaction() {
        _interactionReaction.value = null
    }

    /**
     * Cheering up the mascot gives a quick positive nudge.
     */
    fun cheerUp() {
        _interactionReaction.value = "💖 Thanks for checking in on me! You've got this!"
        viewModelScope.launch {
            delay(4000)
            if (_interactionReaction.value?.startsWith("💖") == true) {
                _interactionReaction.value = null
            }
        }
    }
}
