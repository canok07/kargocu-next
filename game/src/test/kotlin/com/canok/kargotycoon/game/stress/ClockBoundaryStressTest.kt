package com.canok.kargotycoon.game.stress

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import com.canok.kargotycoon.game.persistence.SaveCodec
import com.canok.kargotycoon.game.persistence.SaveDecodeResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

/** Backwards/large clocks, bounded catch-up, day boundaries and repeated-tick idempotency. */
@Category(StressTest::class)
class ClockBoundaryStressTest {
    private val catalog = DefaultCatalog.value
    private val engine = GameEngine(catalog)
    private val codec = SaveCodec(catalog)

    @Test
    fun backwardsAndNegativeClocksAreRejectedWithoutMutation() {
        val state = newGame(catalog, seed = 1)
        for (delta in listOf(-1L, -1_000_000L, Long.MIN_VALUE)) {
            val result = engine.reduce(state, GameCommand.AdvanceTime(CommandId("neg-$delta"), delta))
            assertTrue("negative advance $delta must be rejected", (result as GameResult.Rejected).reason is Rejection.InvalidAdvance)
            assertEquals(state, result.state)
        }
    }

    @Test
    fun overflowAdvanceAndHugeDayareTypedRejections() {
        val nearMax = newGame(catalog, seed = 2).copy(gameTime = GameInstant(Long.MAX_VALUE - 5))
        assertTrue((engine.reduce(nearMax, GameCommand.AdvanceTime(CommandId("overflow"), 1_000_000L)) as GameResult.Rejected).reason is Rejection.ArithmeticFailure)
        assertTrue((engine.reduce(newGame(catalog, seed = 2), GameCommand.AdvanceTime(CommandId("huge"), Long.MAX_VALUE)) as GameResult.Rejected).reason is Rejection.ArithmeticFailure)
        val hugeDay = newGame(catalog, seed = 2).copy(gameDay = Int.MAX_VALUE)
        assertTrue((engine.reduce(hugeDay, GameCommand.AdvanceDay(CommandId("huge-day"))) as GameResult.Rejected).reason is Rejection.ArithmeticFailure)
    }

    @Test
    fun largeBoundedAdvanceClosesEachCalendarDayExactlyOnce() {
        val start = newGame(catalog, seed = 3)
        val advanced = applied(engine.reduce(start, GameCommand.AdvanceTime(CommandId("month"), 30L * GameEngine.DAY_MILLIS)))
        assertEquals(31, advanced.state.gameDay)
        assertEquals(31, advanced.state.offersGeneratedGameDay)
        assertEquals(30, advanced.state.dailySummaries.size)
        assertEquals((1..30).toSet(), advanced.state.dailySummaries.map { it.gameDay }.toSet())
        assertTrue(advanced.state.dailySummaries.all { it.completedJobs == 0 && it.revenue == Money.ZERO && it.costs == Money.ZERO })
        // Re-advancing a tiny amount must not duplicate any day summary.
        val ticked = applied(engine.reduce(advanced.state, GameCommand.AdvanceTime(CommandId("tick"), 1)))
        assertEquals(advanced.state.dailySummaries.size, ticked.state.dailySummaries.size)
    }

    @Test
    fun resumeIsBoundedToOneGameDayAndRollbackNeverReversesTime() {
        val state = newGame(catalog, seed = 4).copy(lastRealtimeMillis = 0)
        val resumed = applied(engine.reduce(state, GameCommand.Resume(CommandId("resume"), Long.MAX_VALUE)))
        assertEquals(2, resumed.state.gameDay)
        assertEquals(GameEngine.DAY_MILLIS, resumed.state.gameTime.millis)
        assertEquals(Long.MAX_VALUE, resumed.state.lastRealtimeMillis)

        val rollback = applied(engine.reduce(resumed.state, GameCommand.Resume(CommandId("rollback"), 0)))
        assertEquals("clock rollback must not move game time backwards", resumed.state.gameTime, rollback.state.gameTime)
        assertFalse(rollback.state.gameTime < resumed.state.gameTime)
    }

    @Test
    fun repeatedTicksAndResumeCannotDuplicateCompletionOrPayment() {
        val accepted = acceptFirstManual(generated(seed = 5))
        val job = accepted.activeJobs.single()
        val settled = applied(engine.reduce(accepted, GameCommand.AdvanceTime(CommandId("finish"), job.completionAt.millis - accepted.gameTime.millis))).state
        val baseline = settled.money
        var state = settled
        repeat(64) { index ->
            state = applied(engine.reduce(state, GameCommand.AdvanceTime(CommandId("zero-$index"), 0))).state
            state = applied(engine.reduce(state, GameCommand.Resume(CommandId("resume-$index"), state.lastRealtimeMillis))).state
        }
        assertEquals("no duplicate payout on repeated ticks", baseline, state.money)
        assertEquals(settled.completedJobs, state.completedJobs)
        assertEquals(1, state.ledger.count { it.type == LedgerType.REVENUE })
    }

    @Test
    fun repeatedDailyBoardGenerationIsIdempotent() {
        val generated = generated(seed = 6)
        val again = applied(engine.reduce(generated, GameCommand.GenerateDailyOffers(CommandId("again"))))
        assertEquals(generated.offers, again.state.offers)
        assertEquals(generated.randomCounter, again.state.randomCounter)
        assertEquals(generated.offersGeneratedGameDay, again.state.offersGeneratedGameDay)
    }

    @Test
    fun invalidIdentifiersAreTypedRejectionsThatPreserveState() {
        val board = generated(seed = 7)
        val state = board.copy(money = Money.euros(1_000), gameDay = 4, progression = ProgressionState(completedJobs = 8))
        val offer = state.offers.first()
        val commands = listOf(
            GameCommand.AcceptJob(CommandId("a1"), OfferId("missing"), VehicleId("vehicle-1"), offer.routeOptions.first()),
            GameCommand.AcceptJob(CommandId("a2"), offer.id, VehicleId("missing"), offer.routeOptions.first()),
            GameCommand.AcceptJob(CommandId("a3"), offer.id, VehicleId("vehicle-1"), RouteId("missing")),
            GameCommand.PurchaseVehicle(CommandId("p1"), VehicleSpecId("missing")),
            GameCommand.SellVehicle(CommandId("s1"), VehicleId("missing")),
            GameCommand.RepairVehicle(CommandId("r1"), VehicleId("missing")),
            GameCommand.MaintainVehicle(CommandId("m1"), VehicleId("missing")),
            GameCommand.HireDriver(CommandId("h1"), DriverTierId("missing"), "X"),
            GameCommand.FireDriver(CommandId("f1"), DriverId("missing")),
            GameCommand.AssignDriver(CommandId("as1"), DriverId("missing"), VehicleId("vehicle-1")),
            GameCommand.UnassignDriver(CommandId("u1"), DriverId("missing")),
        )
        for (command in commands) {
            val result = engine.reduce(state, command)
            assertTrue("$command must be rejected", result is GameResult.Rejected)
            assertEquals("$command must not mutate state", state, result.state)
        }
    }

    @Test
    fun zeroMoneyBlocksOptionalCommandsButNotSettlement() {
        val board = generated(seed = 8).copy(gameDay = 4, progression = ProgressionState(completedJobs = 8))
        val broke = board.copy(money = Money.ZERO)
        assertTrue((engine.reduce(broke, GameCommand.PurchaseVehicle(CommandId("buy"), VehicleSpecId("city-van"))) as GameResult.Rejected).reason is Rejection.InsufficientFunds)
        assertTrue((engine.reduce(broke, GameCommand.HireDriver(CommandId("hire"), DriverTierId("junior"), "Mina")) as GameResult.Rejected).reason is Rejection.InsufficientFunds)
        val acceptRejections = broke.offers.flatMap { offer ->
            offer.routeOptions.map { route -> engine.reduce(broke, GameCommand.AcceptJob(CommandId("acc-${offer.id.value}-${route.value}"), offer.id, VehicleId("vehicle-1"), route)) }
        }
        assertTrue("all accepts must be rejected at zero cash", acceptRejections.all { it is GameResult.Rejected })
        assertTrue("a capacity-feasible offer must fail on funds", acceptRejections.any { it is GameResult.Rejected && it.reason is Rejection.InsufficientFunds })

        val accepted = acceptFirstManual(board)
        val zeroCash = accepted.copy(money = Money.ZERO)
        val finished = applied(engine.reduce(zeroCash, GameCommand.AdvanceTime(CommandId("finish"), zeroCash.activeJobs.single().completionAt.millis - zeroCash.gameTime.millis)))
        assertTrue("settlement must raise cash above zero", finished.state.money.cents > 0)
        assertTrue(finished.state.activeJobs.isEmpty())
    }

    @Test
    fun idleAssignedFleetProducesNoDebtOverManyIdleDays() {
        val driverId = DriverId("driver-2")
        val owned = VehicleState(VehicleId("vehicle-2"), VehicleSpecId("city-van"), Ownership.OWNED, assignedDriverId = driverId)
        val driver = DriverState(driverId, DriverTierId("junior"), "Mina", assignedVehicleId = owned.id)
        var state = newGame(catalog, seed = 9).copy(vehicles = newGame(catalog, seed = 9).vehicles + owned, drivers = listOf(driver), nextEntitySequence = 3, gameDay = 4, progression = ProgressionState(completedJobs = 8))
        val startMoney = state.money
        repeat(20) { day ->
            state = applied(engine.reduce(state, GameCommand.AdvanceDay(CommandId("idle-$day")))).state
        }
        assertEquals("idle days must not change cash", startMoney, state.money)
        assertTrue(state.ledger.none { it.type == LedgerType.VEHICLE_UPKEEP || it.type == LedgerType.DRIVER_WAGE })
        assertTrue(state.vehicles.first { it.id == owned.id }.upkeepChargedGameDays.isEmpty())
        assertTrue(state.drivers.single().wageChargedGameDays.isEmpty())
    }

    @Test
    fun longCareerSaveStaysBoundedAndRoundTrips() {
        val state = StressCareers.greedyRun(engine, catalog, seed = 424242L, days = 120)
        val bytes = codec.encode(state)
        assertTrue("bounded ledger", state.ledger.size <= catalog.economy.historyLimit)
        assertTrue("bounded completed jobs", state.completedJobs.size <= catalog.economy.historyLimit)
        assertTrue("save below 1 MiB", bytes.size < 1_048_576)
        assertEquals(state, (codec.decode(bytes) as SaveDecodeResult.Success).state)
    }

    private fun generated(seed: Long): GameState =
        applied(engine.reduce(newGame(catalog, seed), GameCommand.GenerateDailyOffers(CommandId("gen-$seed")))).state

    private fun acceptFirstManual(state: GameState): GameState {
        for ((index, offer) in state.offers.withIndex()) {
            for (route in offer.routeOptions) {
                val result = engine.reduce(state, GameCommand.AcceptJob(CommandId("accept-$index-${route.value}"), offer.id, VehicleId("vehicle-1"), route))
                if (result is GameResult.Applied) return result.state
            }
        }
        error("no acceptable offer: $state")
    }

    private fun applied(result: GameResult): GameResult.Applied = result as? GameResult.Applied ?: error("Unexpected rejection: $result")
}
