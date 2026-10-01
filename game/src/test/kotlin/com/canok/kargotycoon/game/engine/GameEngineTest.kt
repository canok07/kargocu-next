package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private val engine = GameEngine()

    @Test
    fun seededOfferGenerationIsReproducibleAndUnique() {
        val first = applied(engine.reduce(newGame(seed = 77), GameCommand.GenerateDailyOffers(CommandId("offers"))))
        val second = applied(engine.reduce(newGame(seed = 77), GameCommand.GenerateDailyOffers(CommandId("offers"))))
        assertEquals(first.state.offers, second.state.offers)
        assertEquals(first.state.randomCounter, second.state.randomCounter)
        assertEquals(first.state.offers.size, first.state.offers.map { it.id }.toSet().size)
    }

    @Test
    fun countAndWeightBoundariesAreIndependent() {
        val base = generated()
        val spec = DefaultCatalog.value.vehicles.first { it.id == DefaultCatalog.value.starterVehicleSpecId }
        val offer = base.offers.first().copy(count = spec.capacityCount + 1, totalWeightGrams = spec.capacityGrams)
        val countState = base.copy(offers = listOf(offer))
        val countResult = engine.reduce(countState, accept(offer))
        assertTrue((countResult as GameResult.Rejected).reason is Rejection.CapacityExceeded)
        assertEquals(countState, countResult.state)

        val heavy = offer.copy(id = OfferId("heavy"), count = spec.capacityCount, totalWeightGrams = spec.capacityGrams + 1)
        val weightState = base.copy(offers = listOf(heavy))
        val weightResult = engine.reduce(weightState, accept(heavy))
        assertTrue((weightResult as GameResult.Rejected).reason is Rejection.CapacityExceeded)
        assertEquals(weightState, weightResult.state)
    }

    @Test
    fun capabilityAndInsufficientFundsRejectWithoutMutation() {
        val base = generated()
        val refrigerated = base.offers.first().copy(id = OfferId("cold"), packageTypeId = PackageTypeId("parcel"), requiredCapabilities = setOf(Capability.REFRIGERATED), count = 1, totalWeightGrams = 2_000)
        val capabilityState = base.copy(offers = listOf(refrigerated))
        val capabilityResult = engine.reduce(capabilityState, accept(refrigerated)) as GameResult.Rejected
        assertTrue(capabilityResult.reason is Rejection.MissingCapability)
        assertEquals(capabilityState, capabilityResult.state)

        val affordable = feasibleOffer(base)
        val poor = base.copy(money = Money.ZERO, offers = listOf(affordable))
        val fundsResult = engine.reduce(poor, accept(affordable)) as GameResult.Rejected
        assertTrue(fundsResult.reason is Rejection.InsufficientFunds)
        assertEquals(poor, fundsResult.state)
    }

    @Test
    fun acceptanceReservesMaximumAndCompletionReconcilesLedger() {
        val base = generated()
        val offer = feasibleOffer(base)
        val accepted = applied(engine.reduce(base.copy(offers = listOf(offer)), accept(offer)))
        val job = accepted.state.activeJobs.single()
        assertEquals(Money.euros(100) - job.reserved, accepted.state.money)
        assertEquals(-job.reserved.cents, accepted.state.ledger.single().amount.cents)
        assertTrue(accepted.state.money.cents >= 0)

        val completed = applied(engine.reduce(accepted.state, GameCommand.AdvanceTime(CommandId("finish"), job.completionAt.millis - accepted.state.gameTime.millis)))
        val result = completed.state.completedJobs.single()
        assertTrue(completed.state.activeJobs.isEmpty())
        assertEquals(Money.euros(100) + result.net, completed.state.money)
        val ledgerDelta = completed.state.ledger.fold(0L) { sum, entry -> Math.addExact(sum, entry.amount.cents) }
        assertEquals(result.net.cents, ledgerDelta)
        assertTrue(result.costs <= job.reserved)
        assertFalse(completed.state.vehicles.single().status == VehicleStatus.BUSY)
    }

    @Test
    fun duplicateCompletionCommandCannotPayTwice() {
        val accepted = acceptFirst()
        val job = accepted.activeJobs.single()
        val command = GameCommand.AdvanceTime(CommandId("finish-once"), job.completionAt.millis)
        val first = applied(engine.reduce(accepted, command)).state
        val duplicate = engine.reduce(first, command) as GameResult.Rejected
        assertTrue(duplicate.reason is Rejection.DuplicateCommand)
        assertEquals(first, duplicate.state)
        assertEquals(1, first.completedJobs.size)
    }

    @Test
    fun resumeUsesSameBoundedEnginePathAsOnlineAdvance() {
        val accepted = acceptFirst().copy(lastRealtimeMillis = 1_000)
        val online = applied(engine.reduce(accepted, GameCommand.AdvanceTime(CommandId("online"), 600_000))).state
        val resumed = applied(engine.reduce(accepted, GameCommand.Resume(CommandId("resume"), 11_000))).state
        assertEquals(online.copy(lastRealtimeMillis = resumed.lastRealtimeMillis, processedCommandIds = resumed.processedCommandIds), resumed)
    }

    @Test
    fun arithmeticOverflowIsTypedAndLeavesStateUnchanged() {
        val state = newGame(seed = 1).copy(gameTime = GameInstant(Long.MAX_VALUE))
        val result = engine.reduce(state, GameCommand.AdvanceTime(CommandId("overflow"), 1)) as GameResult.Rejected
        assertTrue(result.reason is Rejection.ArithmeticFailure)
        assertEquals(state, result.state)
    }

    @Test
    fun dailySummaryAndTargetsAreEngineProjections() {
        val state = generated()
        val summary = engine.dailySummary(state)
        assertEquals(state.offers.size, summary.availableOffers)
        assertTrue(summary.potentialGross.cents > 0)
        assertTrue(engine.nextTargets(state).any { it.target == "first-owned-vehicle" })
    }

    private fun generated(): GameState = applied(engine.reduce(newGame(seed = 99), GameCommand.GenerateDailyOffers(CommandId("generate")))).state

    private fun feasibleOffer(state: GameState): JobOffer {
        val starter = DefaultCatalog.value.vehicles.first { it.id == DefaultCatalog.value.starterVehicleSpecId }
        return state.offers.firstOrNull { it.count <= starter.capacityCount && it.totalWeightGrams <= starter.capacityGrams && it.requiredCapabilities.all(starter.capabilities::contains) }
            ?: state.offers.first().copy(packageTypeId = PackageTypeId("parcel"), count = 1, totalWeightGrams = 2_000, requiredCapabilities = emptySet(), riskId = RiskId("calm"))
    }

    private fun accept(offer: JobOffer) = GameCommand.AcceptJob(CommandId("accept-${offer.id.value}"), offer.id, VehicleId("vehicle-1"), offer.routeOptions.first())

    private fun acceptFirst(): GameState {
        val state = generated()
        val offer = feasibleOffer(state)
        return applied(engine.reduce(state.copy(offers = listOf(offer)), accept(offer))).state
    }

    private fun applied(result: GameResult): GameResult.Applied = result as GameResult.Applied
}
