package com.canok.kargotycoon.game.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GameTimePolicyTest {
    private val policy = GameTimePolicy()

    @Test
    fun clockRollbackDoesNotReverseSimulation() {
        assertEquals(0L, policy.simulatedElapsedMillis(-1_000L))
    }

    @Test
    fun oneRealSecondIsOneGameMinute() {
        assertEquals(60_000L, policy.simulatedElapsedMillis(1_000L))
    }

    @Test
    fun longAbsenceIsBoundedToOneGameDay() {
        assertEquals(86_400_000L, policy.simulatedElapsedMillis(Long.MAX_VALUE))
    }

    @Test
    fun invalidPolicyCannotOverflowSavedTime() {
        assertThrows(IllegalArgumentException::class.java) {
            GameTimePolicy(simulationRate = 60L, maximumRealtimeStepMillis = Long.MAX_VALUE)
        }
    }

    @Test
    fun pausedOrInvalidSpeedIsNotAcceptedAsClockPolicy() {
        assertThrows(IllegalArgumentException::class.java) { GameTimePolicy(simulationRate = 0L) }
    }
}
