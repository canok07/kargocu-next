package com.canok.kargotycoon.game.time

/** Converts bounded elapsed real time to accelerated simulation time. */
data class GameTimePolicy(
    val simulationRate: Long = 60L,
    val maximumRealtimeStepMillis: Long = 24L * 60L * 1_000L,
) {
    init {
        require(simulationRate > 0)
        require(maximumRealtimeStepMillis > 0)
        require(maximumRealtimeStepMillis <= Long.MAX_VALUE / simulationRate)
    }

    fun simulatedElapsedMillis(realtimeElapsedMillis: Long): Long =
        realtimeElapsedMillis.coerceIn(0L, maximumRealtimeStepMillis) * simulationRate
}
