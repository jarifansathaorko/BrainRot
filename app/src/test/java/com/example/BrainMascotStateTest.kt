package com.example

import com.example.ui.mascot.BrainExpression
import com.example.ui.mascot.BrainMascotState
import com.example.ui.mascot.ScrollIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainMascotStateTest {

    @Test
    fun `zero scrolls produces SERENE expression and MINIMAL intensity`() {
        val state = BrainMascotState.fromDailyScrolls(scrollCount = 0, dailyGoal = 100)
        assertEquals(BrainExpression.SERENE, state.expression)
        assertEquals(ScrollIntensity.MINIMAL, state.dailyScrollIntensity)
        assertEquals("Super Fresh", state.title)
        assertFalse(state.isGoalExceeded)
        assertFalse(state.showSweatDrop)
        assertFalse(state.showSteam)
    }

    @Test
    fun `low scrolls produces HAPPY expression and LOW intensity`() {
        val state = BrainMascotState.fromDailyScrolls(scrollCount = 30, dailyGoal = 100)
        assertEquals(BrainExpression.HAPPY, state.expression)
        assertEquals(ScrollIntensity.LOW, state.dailyScrollIntensity)
        assertEquals("Cruising", state.title)
        assertFalse(state.isGoalExceeded)
        assertFalse(state.showSweatDrop)
    }

    @Test
    fun `moderate scrolls produces DAZED expression and MODERATE intensity`() {
        val state = BrainMascotState.fromDailyScrolls(scrollCount = 75, dailyGoal = 100)
        assertEquals(BrainExpression.DAZED, state.expression)
        assertEquals(ScrollIntensity.MODERATE, state.dailyScrollIntensity)
        assertEquals("Slightly Dazed", state.title)
        assertFalse(state.isGoalExceeded)
    }

    @Test
    fun `elevated scrolls near goal produces FATIGUED expression`() {
        val state = BrainMascotState.fromDailyScrolls(scrollCount = 100, dailyGoal = 100)
        assertEquals(BrainExpression.FATIGUED, state.expression)
        assertEquals(ScrollIntensity.ELEVATED, state.dailyScrollIntensity)
        assertTrue(state.showSweatDrop)
    }

    @Test
    fun `exceeding limit produces intense fried state`() {
        val state = BrainMascotState.fromDailyScrolls(scrollCount = 150, dailyGoal = 100)
        assertTrue(state.isGoalExceeded)
        assertTrue(state.showSweatDrop)
    }

    @Test
    fun `high over-limit produces COOKED or NUCLEAR expression with steam`() {
        val cookedState = BrainMascotState.fromDailyScrolls(scrollCount = 300, dailyGoal = 100)
        assertTrue(cookedState.showSteam)
        assertTrue(cookedState.isGoalExceeded)

        val nuclearState = BrainMascotState.fromDailyScrolls(scrollCount = 700, dailyGoal = 100)
        assertEquals(BrainExpression.NUCLEAR, nuclearState.expression)
        assertEquals(ScrollIntensity.EXTREME, nuclearState.dailyScrollIntensity)
        assertTrue(nuclearState.showSteam)
        assertTrue(nuclearState.showSweatDrop)
        assertEquals("Nuclear Brainrot", nuclearState.title)
    }

    @Test
    fun `user interaction reaction sets quote properly`() {
        val reaction = "✨ Feeling refreshed!"
        val state = BrainMascotState.fromDailyScrolls(
            scrollCount = 10,
            dailyGoal = 100,
            interactionFeedback = reaction
        )
        assertEquals(reaction, state.userInteractionReaction)
        assertNotNull(state.advice)
    }
}
