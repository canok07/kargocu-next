package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.persistence.*
import org.junit.Assert.*
import org.junit.Test

class EconomyBalanceRegressionTest {
    private val catalog = DefaultCatalog.value
    private val engine = GameEngine(catalog)

    @Test
    fun heavierLargerLoadsPayMoreWhileStarterReservationStaysAffordable() {
        val generated = (engine.reduce(newGame(seed = 7), GameCommand.GenerateDailyOffers(CommandId("offers"))) as GameResult.Applied).state
        val light = generated.offers.first().copy(packageTypeId = PackageTypeId("parcel"), count = 1, totalWeightGrams = 2_000, requiredCapabilities = emptySet(), riskId = RiskId("calm"))
        val heavy = light.copy(count = 6, totalWeightGrams = 850_000)
        fun invoice(offer: JobOffer) = (engine.reduce(generated.copy(offers = listOf(offer)), GameCommand.AcceptJob(CommandId("accept"), offer.id, VehicleId("vehicle-1"), offer.routeOptions.first())) as GameResult.Applied).state.activeJobs.single().invoice
        val small = invoice(light)
        val large = invoice(heavy)
        assertTrue(large.maximumRevenue > small.maximumRevenue)
        assertTrue(large.maximumReservation <= Money.euros(100))
        assertTrue(small.maximumRevenue > small.maximumReservation)
        assertTrue(large.maximumRevenue - large.maximumReservation > small.maximumRevenue - small.maximumReservation)
    }

    @Test
    fun actual011SavePreservesCashAssetsAndInFlightInvoices() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/legacy/011-endgame-save.json")).use { it.readBytes() }
        val codec = SaveCodec(catalog)
        val decoded = codec.decode(bytes) as? SaveDecodeResult.Success ?: error("Actual 0.1.1 save could not be opened")
        val before = decoded.state
        assertEquals(Money(50_446), before.money)
        assertEquals(28, before.progression.completedJobs)
        assertTrue(before.activeJobs.isNotEmpty())
        assertEquals(before, (codec.decode(codec.encode(before)) as SaveDecodeResult.Success).state)
        val delta = before.activeJobs.maxOf { it.completionAt.millis } - before.gameTime.millis
        val settled = (engine.reduce(before, GameCommand.AdvanceTime(CommandId("legacy-finish"), delta)) as GameResult.Applied).state
        for (job in before.activeJobs) {
            val result = settled.completedJobs.single { it.jobId == job.id }
            assertEquals("An accepted quote must survive a balance update", job.invoice.maximumRevenue, result.revenue)
            assertTrue(result.costs <= job.reserved)
        }
        assertEquals(before.progression.completedJobs + before.activeJobs.size, settled.progression.completedJobs)
    }

    @Test
    fun everyPurchasableVehicleLosesMoneyOnImmediateResale() {
        val top = catalog.progression.levels.maxBy { it.level }
        val base = newGame(seed = 8)
        val rich = base.copy(money = Money.euros(1_000_000), gameDay = top.minimumGameDay,
            progression = base.progression.copy(completedJobs = top.minimumCompletedJobs),
            vehicles = base.vehicles + (0 until top.minimumOwnedVehicles).map { VehicleState(VehicleId("owned-$it"), catalog.vehicles.filterNot { spec -> spec.rental }[it].id, Ownership.OWNED) },
            drivers = (0 until top.minimumDrivers).map { DriverState(DriverId("driver-$it"), DriverTierId("junior"), "Driver $it") })
        for (spec in catalog.vehicles.filterNot { it.rental }) {
            val bought = (engine.reduce(rich, GameCommand.PurchaseVehicle(CommandId("buy-${spec.id.value}"), spec.id)) as GameResult.Applied).state
            val sold = (engine.reduce(bought, GameCommand.SellVehicle(CommandId("sell-${spec.id.value}"), bought.vehicles.last().id)) as GameResult.Applied).state
            assertTrue("Purchase/sale arbitrage for ${spec.id}", sold.money < rich.money)
        }
    }

    @Test
    fun invalidRewardConfigIsRejectedBeforeItReachesPlay() {
        assertTrue(CatalogValidator.validate(catalog.copy(economy = catalog.economy.copy(weightRewardCentsPerKilogram = -1))).isNotEmpty())
        assertTrue(CatalogValidator.validate(catalog.copy(economy = catalog.economy.copy(parcelRewardCents = Long.MAX_VALUE))).isNotEmpty())
    }
}
