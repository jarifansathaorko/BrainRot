package com.example.ui.mascot

import androidx.compose.ui.graphics.Color

/**
 * Represents the emotional and physical expression of the Scrolly Brain Mascot.
 */
enum class BrainExpression(val displayName: String) {
    SERENE("Serene"),
    HAPPY("Happy"),
    DAZED("Dazed"),
    FATIGUED("Fatigued"),
    FRIED("Fried"),
    COOKED("Cooked"),
    NUCLEAR("Meltdown")
}

/**
 * Categorizes the user's daily scroll intensity based on total scrolls
 * and progress relative to the daily limit.
 */
enum class ScrollIntensity(
    val label: String,
    val description: String,
    val color: Color
) {
    MINIMAL(
        label = "Minimal",
        description = "Crystal clear focus. No short-form brain fog.",
        color = Color(0xFF34D399) // Mint emerald
    ),
    LOW(
        label = "Light",
        description = "Light entertainment within healthy boundaries.",
        color = Color(0xFFFBBF24) // Gold
    ),
    MODERATE(
        label = "Moderate",
        description = "Attention starts to fragment. Time to be mindful.",
        color = Color(0xFFFB923C) // Warm orange
    ),
    ELEVATED(
        label = "Elevated",
        description = "Dopamine loop active. Nearing daily quota.",
        color = Color(0xFFF87171) // Coral red
    ),
    INTENSE(
        label = "Intense",
        description = "Daily limit exceeded! High mental fatigue.",
        color = Color(0xFFE11D48) // Crimson
    ),
    EXTREME(
        label = "Nuclear",
        description = "Severe doomscrolling! High dopamine overload.",
        color = Color(0xFF991B1B) // Deep dark crimson
    )
}

/**
 * Data class representing the state of the Brain Mascot, its emotional expression,
 * visual styling, and behavioral nudges based on daily scroll intensity.
 */
data class BrainMascotState(
    val expression: BrainExpression = BrainExpression.SERENE,
    val dailyScrollIntensity: ScrollIntensity = ScrollIntensity.MINIMAL,
    val scrollCount: Int = 0,
    val dailyGoal: Int = 100,
    val intensityRatio: Float = 0f,
    val title: String = "Super Fresh",
    val quote: String = "Brain is crystal clear! Ready to conquer the day.",
    val advice: String = "Keep this intentional momentum going!",
    val baseColor: Color = Color(0xFFFF9E8E),
    val cheekColor: Color = Color(0xFFFF6350),
    val fatigueProgress: Float = 0f, // 0.0 (fresh) to 1.0 (overheated)
    val isGoalExceeded: Boolean = false,
    val bounceSpeedMs: Int = 1200,
    val eyeSquint: Float = 1f,
    val showSweatDrop: Boolean = false,
    val showSteam: Boolean = false,
    val userInteractionReaction: String? = null
) {
    companion object {
        /**
         * Calculates the Brain Mascot state and expression dynamically from daily scroll statistics.
         *
         * @param scrollCount Number of short-form videos/reels scrolled today.
         * @param dailyGoal The user's targeted maximum scroll limit for the day.
         * @param interactionFeedback Optional temporary reaction quote triggered by tapping the mascot.
         */
        fun fromDailyScrolls(
            scrollCount: Int,
            dailyGoal: Int = 100,
            interactionFeedback: String? = null
        ): BrainMascotState {
            val safeGoal = dailyGoal.coerceAtLeast(1)
            val ratio = (scrollCount.toFloat() / safeGoal).coerceAtLeast(0f)
            val isExceeded = scrollCount > safeGoal

            val intensity = when {
                scrollCount <= 15 && ratio <= 0.20f -> ScrollIntensity.MINIMAL
                scrollCount <= 50 && ratio <= 0.55f -> ScrollIntensity.LOW
                scrollCount < 90 && ratio < 0.85f -> ScrollIntensity.MODERATE
                ratio <= 1.2f || scrollCount <= 180 -> ScrollIntensity.ELEVATED
                ratio <= 2.2f && scrollCount <= 350 -> ScrollIntensity.INTENSE
                else -> ScrollIntensity.EXTREME
            }

            val expression = when (intensity) {
                ScrollIntensity.MINIMAL -> if (scrollCount == 0) BrainExpression.SERENE else BrainExpression.HAPPY
                ScrollIntensity.LOW -> BrainExpression.HAPPY
                ScrollIntensity.MODERATE -> BrainExpression.DAZED
                ScrollIntensity.ELEVATED -> BrainExpression.FATIGUED
                ScrollIntensity.INTENSE -> BrainExpression.FRIED
                ScrollIntensity.EXTREME -> if (scrollCount >= 500 || ratio >= 3.5f) BrainExpression.NUCLEAR else BrainExpression.COOKED
            }

            val title = when (expression) {
                BrainExpression.SERENE -> "Super Fresh"
                BrainExpression.HAPPY -> "Cruising"
                BrainExpression.DAZED -> "Slightly Dazed"
                BrainExpression.FATIGUED -> "Brain Getting Tired"
                BrainExpression.FRIED -> "Brain Fried"
                BrainExpression.COOKED -> "Completely Cooked"
                BrainExpression.NUCLEAR -> "Nuclear Brainrot"
            }

            val quote = when (expression) {
                BrainExpression.SERENE -> "Brain is crystal clear! Ready to conquer the day."
                BrainExpression.HAPPY -> "Cruising along nicely. Keep it intentional!"
                BrainExpression.DAZED -> "Starting to zone out. Maybe touch some grass 😌"
                BrainExpression.FATIGUED -> "Thumb is getting overtime. Focus is melting! ⚡"
                BrainExpression.FRIED -> "Dopamine receptors sizzling. Put the phone down! 🍳"
                BrainExpression.COOKED -> "Your brain is well-done. Serious digital detox needed!"
                BrainExpression.NUCLEAR -> "Emergency! Dopamine overload catastrophic level! 💀"
            }

            val advice = when (expression) {
                BrainExpression.SERENE -> "Perfect time for deep focus, learning, or creative work."
                BrainExpression.HAPPY -> "Stay intentional before opening another endless feed."
                BrainExpression.DAZED -> "Look out the window for 20 seconds and rest your vision."
                BrainExpression.FATIGUED -> "Take a 5-minute hydration break and stretch your neck."
                BrainExpression.FRIED -> "Close short-form apps and take a brief screen-free walk."
                BrainExpression.COOKED -> "Lock your phone and switch to real-world tasks."
                BrainExpression.NUCLEAR -> "Step away from screens immediately to reset your focus!"
            }

            val baseColor = when (expression) {
                BrainExpression.SERENE -> Color(0xFFFF9E8E)
                BrainExpression.HAPPY -> Color(0xFFFFAE75)
                BrainExpression.DAZED -> Color(0xFFFFBC60)
                BrainExpression.FATIGUED -> Color(0xFFF29B63)
                BrainExpression.FRIED -> Color(0xFFE58867)
                BrainExpression.COOKED -> Color(0xFFB5705C)
                BrainExpression.NUCLEAR -> Color(0xFF82544B)
            }

            val cheekColor = when (expression) {
                BrainExpression.SERENE -> Color(0xFFFF6350)
                BrainExpression.HAPPY -> Color(0xFFFF7A45)
                BrainExpression.DAZED -> Color(0xFFEE8A2A)
                BrainExpression.FATIGUED -> Color(0xFFDB6A38)
                BrainExpression.FRIED -> Color(0xFFC75530)
                BrainExpression.COOKED -> Color(0xFF8A4030)
                BrainExpression.NUCLEAR -> Color(0xFF5A2E26)
            }

            val fatigueProgress = (scrollCount.toFloat() / (safeGoal * 1.5f)).coerceIn(0f, 1f)
            val bounceSpeedMs = when (expression) {
                BrainExpression.SERENE -> 1400
                BrainExpression.HAPPY -> 1200
                BrainExpression.DAZED -> 1000
                BrainExpression.FATIGUED -> 850
                BrainExpression.FRIED -> 650
                BrainExpression.COOKED -> 500
                BrainExpression.NUCLEAR -> 380
            }

            return BrainMascotState(
                expression = expression,
                dailyScrollIntensity = intensity,
                scrollCount = scrollCount,
                dailyGoal = safeGoal,
                intensityRatio = ratio,
                title = title,
                quote = quote,
                advice = advice,
                baseColor = baseColor,
                cheekColor = cheekColor,
                fatigueProgress = fatigueProgress,
                isGoalExceeded = isExceeded,
                bounceSpeedMs = bounceSpeedMs,
                eyeSquint = if (expression in listOf(BrainExpression.FRIED, BrainExpression.COOKED, BrainExpression.NUCLEAR)) 0.85f else 1f,
                showSweatDrop = expression in listOf(BrainExpression.FATIGUED, BrainExpression.FRIED, BrainExpression.COOKED, BrainExpression.NUCLEAR),
                showSteam = expression in listOf(BrainExpression.COOKED, BrainExpression.NUCLEAR),
                userInteractionReaction = interactionFeedback
            )
        }
    }
}
