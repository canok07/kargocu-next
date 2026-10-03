package com.canok.kargotycoon.game.career

import com.canok.kargotycoon.game.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CareerBalanceTest {
    private val outputJson = Json { prettyPrint = true }
    @Test
    fun seededCareersRemainReachableAndDaySkippingCannotBypassWorkOrInvestment() {
        val catalog = DefaultCatalog.value
        val findings = listOf(7L, 99L, 20261003L, 700001L, 804730L).flatMap { seed ->
            val normal = runCareer(catalog, seed, false)
            val skipped = runCareer(catalog, seed, true)
            assertEquals(normal.completedJobs, skipped.completedJobs)
            assertEquals(normal.balanceCents, skipped.balanceCents)
            assertEquals(normal.endDay, skipped.endDay)
            assertTrue("First car inaccessible: $normal", normal.firstOwnedAfterJobs in 1..25)
            assertTrue(normal.drivers > 0 && normal.maxParallel >= 2 && normal.minimumBalanceCents >= 0)
            assertTrue(skipped.daySkipMinimumWaitMillis < skipped.normalClockMillis)
            listOf(normal, skipped)
        }
        val dir = File(System.getProperty("career.metrics.dir")).apply { mkdirs() }
        File(dir, "current.json").writeText(outputJson.encodeToString(findings))
    }

    @Test
    fun originalCatalogProvidesARecordedComparisonBaseline() {
        val old = requireNotNull(javaClass.getResourceAsStream("/legacy/011-catalog.json")).use { DefaultCatalog.json.decodeFromString<GameCatalog>(it.readBytes().decodeToString()) }
        val findings = listOf(7L, 99L, 20261003L, 700001L, 804730L).map { runCareer(old, it, false) }
        val dir = File(System.getProperty("career.metrics.dir")).apply { mkdirs() }
        File(dir, "original-011.json").writeText(outputJson.encodeToString(findings))
        assertTrue(findings.all { it.stage == 4 && it.regions == 3 && it.endDay >= 10 })
    }
}
