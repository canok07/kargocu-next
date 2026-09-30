package com.canok.kargotycoon.game.domain

import kotlinx.serialization.Serializable

@Serializable
data class VehicleState(
    val id: VehicleId,
    val specId: VehicleSpecId,
    val ownership: Ownership,
    val status: VehicleStatus = VehicleStatus.AVAILABLE,
    val conditionPercent: Int = 100,
    val mileageMeters: Long = 0,
    val totalEarned: Money = Money.ZERO,
    val assignedDriverId: DriverId? = null,
)

@Serializable
data class DriverState(
    val id: DriverId,
    val tierId: DriverTierId,
    val name: String,
    val experienceJobs: Int = 0,
    val status: DriverStatus = DriverStatus.AVAILABLE,
    val assignedVehicleId: VehicleId? = null,
    val wageChargedGameDays: Set<Int> = emptySet(),
)

@Serializable
data class JobInvoice(
    val baseReward: Money,
    val distanceReward: Money,
    val riskBonus: Money,
    val reservedFuel: Money,
    val reservedRental: Money,
    val reservedPenalty: Money,
) {
    val maximumReservation: Money get() = reservedFuel + reservedRental + reservedPenalty
    val maximumRevenue: Money get() = baseReward + distanceReward + riskBonus
}

@Serializable
data class JobOffer(
    val id: OfferId,
    val packageTypeId: PackageTypeId,
    val count: Int,
    val totalWeightGrams: Long,
    val originId: LocationId,
    val destinationId: LocationId,
    val routeOptions: List<RouteId>,
    val requiredCapabilities: Set<Capability>,
    val riskId: RiskId,
    val expiresAt: GameInstant,
)

@Serializable
data class ActiveJob(
    val id: JobId,
    val acceptedOfferId: OfferId,
    val vehicleId: VehicleId,
    val driverId: DriverId?,
    val manualDriving: Boolean,
    val routeId: RouteId,
    val startedAt: GameInstant,
    val dueAt: GameInstant,
    val completionAt: GameInstant,
    val invoice: JobInvoice,
    val reserved: Money,
    val distanceMeters: Long,
    val riskRollPerMillion: Int,
)

@Serializable
data class JobResult(
    val jobId: JobId,
    val completedAt: GameInstant,
    val onTime: Boolean,
    val riskOccurred: Boolean,
    val revenue: Money,
    val costs: Money,
    val net: Money,
)

@Serializable
data class LedgerEntry(
    val id: LedgerEntryId,
    val at: GameInstant,
    val type: LedgerType,
    val amount: Money,
    val jobId: JobId? = null,
    val vehicleId: VehicleId? = null,
    val driverId: DriverId? = null,
)

@Serializable
data class ProgressionState(
    val completedJobs: Int = 0,
    val onTimeJobs: Int = 0,
    val unlockedRegionIds: Set<RegionId> = setOf(RegionId("ruhr")),
    val claimedUnlocks: Set<String> = emptySet(),
)

@Serializable
data class TutorialState(
    val step: TutorialStep = TutorialStep.ACCEPT_FIRST_JOB,
    val dismissed: Boolean = false,
)

@Serializable
data class GameSettings(
    val languageTag: String = "tr",
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
)

@Serializable
data class DailySummary(
    val gameDay: Int,
    val completedJobs: Int,
    val revenue: Money,
    val costs: Money,
)

@Serializable
data class GameState(
    val stateVersion: Int = 1,
    val catalogVersion: Int,
    val revision: Long = 0,
    val gameTime: GameInstant = GameInstant(0),
    val lastRealtimeMillis: Long = 0,
    val gameDay: Int = 1,
    val money: Money = Money.euros(100),
    val nextEntitySequence: Long = 1,
    val randomSeed: Long,
    val randomCounter: Long = 0,
    val vehicles: List<VehicleState>,
    val drivers: List<DriverState> = emptyList(),
    val offers: List<JobOffer> = emptyList(),
    val activeJobs: List<ActiveJob> = emptyList(),
    val completedJobs: List<JobResult> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val dailySummaries: List<DailySummary> = emptyList(),
    val progression: ProgressionState = ProgressionState(),
    val tutorial: TutorialState = TutorialState(),
    val settings: GameSettings = GameSettings(),
    val processedCommandIds: List<CommandId> = emptyList(),
) {
    fun companyLevel(catalog: GameCatalog): Int = catalog.progression.levels
        .filter { progression.completedJobs >= it.minimumCompletedJobs && vehicles.count { vehicle -> vehicle.ownership == Ownership.OWNED } >= it.minimumOwnedVehicles && drivers.size >= it.minimumDrivers && gameDay >= it.minimumGameDay }
        .maxOfOrNull { it.level } ?: 1
}
