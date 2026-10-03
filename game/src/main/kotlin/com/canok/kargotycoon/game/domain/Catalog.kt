package com.canok.kargotycoon.game.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class VehicleSpec(
    val id: VehicleSpecId,
    val name: String,
    val capacityCount: Int,
    val capacityGrams: Long,
    val capabilities: Set<Capability>,
    val purchasePrice: Money,
    val resaleBasisPoints: Int,
    val fuelCentsPerKilometer: Long,
    val dailyUpkeep: Money,
    val repairCentsPerConditionPoint: Long,
    val minimumCompanyLevel: Int,
    val rental: Boolean = false,
)

@Serializable data class DriverTier(val id: DriverTierId, val name: String, val hiringCost: Money, val dailyWage: Money, val errorChancePerMillion: Int, val minimumCompanyLevel: Int)
@Serializable data class PackageType(val id: PackageTypeId, val name: String, val capabilities: Set<Capability>, val minWeightGrams: Long, val maxWeightGrams: Long, val baseReward: Money, val minimumCompanyLevel: Int = 1)
@Serializable data class RiskSpec(val id: RiskId, val name: String, val chancePerMillion: Int, val rewardBasisPoints: Int, val penaltyBasisPoints: Int)
@Serializable data class RegionSpec(val id: RegionId, val name: String, val minimumCompletedJobs: Int, val minimumOwnedVehicles: Int, val minimumGameDay: Int, val minimumDistinctVehicleSpecs: Int = 0)
@Serializable data class LocationSpec(val id: LocationId, val regionId: RegionId, val name: String)
@Serializable data class RouteSpec(val id: RouteId, val originId: LocationId, val destinationId: LocationId, val distanceMeters: Long, val durationGameMinutes: Int, val requiredCapabilities: Set<Capability> = emptySet())
@Serializable data class ProgressionLevel(val level: Int, val minimumCompletedJobs: Int, val minimumOwnedVehicles: Int, val minimumDrivers: Int, val minimumGameDay: Int, val minimumDistinctVehicleSpecs: Int = 0)
@Serializable data class EconomyConfig(val offersPerDay: Int, val offerLifetimeGameMinutes: Int, val distanceRewardCentsPerKilometer: Long, val rentalCentsPerJob: Long, val maximumPenaltyBasisPoints: Int, val maintenanceIntervalMeters: Long, val maintenanceCostBasisPoints: Int, val severanceWageDays: Int, val historyLimit: Int, val parcelRewardCents: Long = 150, val weightRewardCentsPerKilogram: Long = 0, val offersPerUnlockedRegion: Int = 0, val offersPerCompanyLevel: Int = 0, val maximumOffersPerDay: Int = 64, val parcelsPerCompanyLevel: Int = 0)
@Serializable data class ProgressionConfig(val levels: List<ProgressionLevel>)

@Serializable
data class GameCatalog(
    val version: Int,
    val starterVehicleSpecId: VehicleSpecId,
    val vehicles: List<VehicleSpec>,
    val driverTiers: List<DriverTier>,
    val packageTypes: List<PackageType>,
    val risks: List<RiskSpec>,
    val regions: List<RegionSpec>,
    val locations: List<LocationSpec>,
    val routes: List<RouteSpec>,
    val economy: EconomyConfig,
    val progression: ProgressionConfig,
)

sealed interface CatalogIssue {
    data class DuplicateId(val category: String, val id: String) : CatalogIssue
    data class InvalidRange(val field: String) : CatalogIssue
    data class MissingReference(val field: String, val id: String) : CatalogIssue
    data object MissingStarterVehicle : CatalogIssue
    data object NoFeasibleStarterJob : CatalogIssue
}

object CatalogValidator {
    fun validate(catalog: GameCatalog): List<CatalogIssue> = buildList {
        if (catalog.version <= 0) add(CatalogIssue.InvalidRange("version"))
        duplicates("vehicle", catalog.vehicles.map { it.id.value }, this)
        duplicates("driverTier", catalog.driverTiers.map { it.id.value }, this)
        duplicates("packageType", catalog.packageTypes.map { it.id.value }, this)
        duplicates("risk", catalog.risks.map { it.id.value }, this)
        duplicates("region", catalog.regions.map { it.id.value }, this)
        duplicates("location", catalog.locations.map { it.id.value }, this)
        duplicates("route", catalog.routes.map { it.id.value }, this)
        val vehicleIds = catalog.vehicles.map { it.id }.toSet()
        val regionIds = catalog.regions.map { it.id }.toSet()
        val locationIds = catalog.locations.map { it.id }.toSet()
        if (catalog.starterVehicleSpecId !in vehicleIds) add(CatalogIssue.MissingStarterVehicle)
        catalog.vehicles.forEach {
            if (it.capacityCount <= 0 || it.capacityGrams <= 0 || it.purchasePrice.cents < 0 || it.resaleBasisPoints !in 0..10_000 || it.fuelCentsPerKilometer < 0 || it.dailyUpkeep.cents < 0 || it.repairCentsPerConditionPoint < 0 || it.minimumCompanyLevel <= 0) add(CatalogIssue.InvalidRange("vehicle:${it.id.value}"))
        }
        catalog.driverTiers.forEach { if (it.hiringCost.cents < 0 || it.dailyWage.cents < 0 || it.errorChancePerMillion !in 0..250_000 || it.minimumCompanyLevel <= 0) add(CatalogIssue.InvalidRange("driverTier:${it.id.value}")) }
        catalog.packageTypes.forEach { if (it.minWeightGrams <= 0 || it.maxWeightGrams < it.minWeightGrams || it.baseReward.cents <= 0) add(CatalogIssue.InvalidRange("package:${it.id.value}")) }
        catalog.risks.forEach { if (it.chancePerMillion !in 0..250_000 || it.rewardBasisPoints !in 0..5_000 || it.penaltyBasisPoints !in 0..5_000) add(CatalogIssue.InvalidRange("risk:${it.id.value}")) }
        catalog.locations.forEach { if (it.regionId !in regionIds) add(CatalogIssue.MissingReference("location.region", it.regionId.value)) }
        catalog.routes.forEach {
            if (it.originId !in locationIds) add(CatalogIssue.MissingReference("route.origin", it.originId.value))
            if (it.destinationId !in locationIds) add(CatalogIssue.MissingReference("route.destination", it.destinationId.value))
            if (it.distanceMeters <= 0 || it.durationGameMinutes !in 1..120 || it.originId == it.destinationId) add(CatalogIssue.InvalidRange("route:${it.id.value}"))
        }
        val e = catalog.economy
        if (e.offersPerUnlockedRegion !in 0..16 || e.offersPerCompanyLevel !in 0..16 || e.maximumOffersPerDay !in e.offersPerDay..128 || e.parcelsPerCompanyLevel !in 0..8) add(CatalogIssue.InvalidRange("offerGrowth"))
        catalog.packageTypes.forEach { if (it.minimumCompanyLevel <= 0) add(CatalogIssue.InvalidRange("packageLevel:${it.id.value}")) }
        duplicates("companyLevel", catalog.progression.levels.map { it.level.toString() }, this)
        catalog.progression.levels.forEach {
            if (it.level <= 0 || it.minimumCompletedJobs < 0 || it.minimumOwnedVehicles < 0 || it.minimumDrivers < 0 || it.minimumGameDay < 1 || it.minimumDistinctVehicleSpecs !in 0..it.minimumOwnedVehicles) add(CatalogIssue.InvalidRange("progression:${it.level}"))
            val availableTypes = catalog.vehicles.count { vehicle -> !vehicle.rental && vehicle.minimumCompanyLevel < it.level }
            if (it.minimumDistinctVehicleSpecs > availableTypes) add(CatalogIssue.InvalidRange("unreachableVehicleTypes:${it.level}"))
        }
        catalog.regions.forEach { if (it.minimumCompletedJobs < 0 || it.minimumOwnedVehicles < 0 || it.minimumGameDay < 1 || it.minimumDistinctVehicleSpecs !in 0..it.minimumOwnedVehicles) add(CatalogIssue.InvalidRange("regionGate:${it.id.value}")) }
        if (e.parcelRewardCents !in 0..10_000 || e.weightRewardCentsPerKilogram !in 0..1_000) add(CatalogIssue.InvalidRange("loadReward"))
        if (e.offersPerDay <= 0 || e.offerLifetimeGameMinutes <= 0 || e.distanceRewardCentsPerKilometer <= 0 || e.rentalCentsPerJob < 0 || e.maximumPenaltyBasisPoints !in 0..10_000 || e.maintenanceIntervalMeters <= 0 || e.maintenanceCostBasisPoints !in 0..10_000 || e.severanceWageDays !in 0..30 || e.historyLimit !in 10..10_000) add(CatalogIssue.InvalidRange("economy"))
        val starter = catalog.vehicles.firstOrNull { it.id == catalog.starterVehicleSpecId && it.rental }
        val feasible = starter != null && catalog.routes.any { route -> route.requiredCapabilities.all(starter.capabilities::contains) } && catalog.packageTypes.any { type -> type.capabilities.all(starter.capabilities::contains) && type.minWeightGrams <= starter.capacityGrams && type.baseReward >= Money(e.rentalCentsPerJob) }
        if (!feasible) add(CatalogIssue.NoFeasibleStarterJob)
    }

    private fun duplicates(category: String, ids: List<String>, issues: MutableList<CatalogIssue>) {
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { issues += CatalogIssue.DuplicateId(category, it) }
    }
}

object DefaultCatalog {
    val json = Json { encodeDefaults = true; prettyPrint = false; explicitNulls = false }
    val value: GameCatalog by lazy {
        json.decodeFromString<GameCatalog>(CATALOG_JSON).also { catalog ->
            val issues = CatalogValidator.validate(catalog)
            require(issues.isEmpty()) { "Invalid bundled catalog: $issues" }
        }
    }

    private val CATALOG_JSON = """
{
  "version": 1,
  "starterVehicleSpecId": "rental-panelvan",
  "vehicles": [
    {
      "id": "rental-panelvan",
      "name": "Miet-Transporter",
      "capacityCount": 12,
      "capacityGrams": 900000,
      "capabilities": [
        "FRAGILE"
      ],
      "purchasePrice": 0,
      "resaleBasisPoints": 0,
      "fuelCentsPerKilometer": 16,
      "dailyUpkeep": 0,
      "repairCentsPerConditionPoint": 0,
      "minimumCompanyLevel": 1,
      "rental": true
    },
    {
      "id": "city-van",
      "name": "Stadtvan",
      "capacityCount": 18,
      "capacityGrams": 1400000,
      "capabilities": [
        "FRAGILE"
      ],
      "purchasePrice": 90000,
      "resaleBasisPoints": 6500,
      "fuelCentsPerKilometer": 13,
      "dailyUpkeep": 180,
      "repairCentsPerConditionPoint": 55,
      "minimumCompanyLevel": 2
    },
    {
      "id": "cool-box",
      "name": "Kühlboxer",
      "capacityCount": 14,
      "capacityGrams": 1200000,
      "capabilities": [
        "FRAGILE",
        "REFRIGERATED"
      ],
      "purchasePrice": 280000,
      "resaleBasisPoints": 6800,
      "fuelCentsPerKilometer": 20,
      "dailyUpkeep": 600,
      "repairCentsPerConditionPoint": 90,
      "minimumCompanyLevel": 3
    },
    {
      "id": "cargo-truck",
      "name": "Fernlastwagen",
      "capacityCount": 36,
      "capacityGrams": 5000000,
      "capabilities": [
        "FRAGILE",
        "HAZARDOUS"
      ],
      "purchasePrice": 600000,
      "resaleBasisPoints": 7000,
      "fuelCentsPerKilometer": 32,
      "dailyUpkeep": 1200,
      "repairCentsPerConditionPoint": 130,
      "minimumCompanyLevel": 4
    },
    {
      "id": "long-van",
      "name": "Long Wheelbase Van",
      "capacityCount": 30,
      "capacityGrams": 3000000,
      "capabilities": [
        "FRAGILE"
      ],
      "purchasePrice": 380000,
      "resaleBasisPoints": 6500,
      "fuelCentsPerKilometer": 18,
      "dailyUpkeep": 900,
      "repairCentsPerConditionPoint": 100,
      "minimumCompanyLevel": 4
    },
    {
      "id": "cold-rig",
      "name": "Cold Chain Truck",
      "capacityCount": 40,
      "capacityGrams": 8000000,
      "capabilities": [
        "FRAGILE",
        "REFRIGERATED"
      ],
      "purchasePrice": 880000,
      "resaleBasisPoints": 6500,
      "fuelCentsPerKilometer": 32,
      "dailyUpkeep": 1500,
      "repairCentsPerConditionPoint": 200,
      "minimumCompanyLevel": 5
    },
    {
      "id": "freight-rig",
      "name": "Industrial Freight Truck",
      "capacityCount": 52,
      "capacityGrams": 12000000,
      "capabilities": [
        "FRAGILE",
        "HAZARDOUS"
      ],
      "purchasePrice": 1400000,
      "resaleBasisPoints": 6500,
      "fuelCentsPerKilometer": 38,
      "dailyUpkeep": 2200,
      "repairCentsPerConditionPoint": 260,
      "minimumCompanyLevel": 6
    },
    {
      "id": "all-rounder",
      "name": "Multi-role Hauler",
      "capacityCount": 64,
      "capacityGrams": 16000000,
      "capabilities": [
        "FRAGILE",
        "REFRIGERATED",
        "HAZARDOUS"
      ],
      "purchasePrice": 2400000,
      "resaleBasisPoints": 6500,
      "fuelCentsPerKilometer": 45,
      "dailyUpkeep": 3000,
      "repairCentsPerConditionPoint": 350,
      "minimumCompanyLevel": 7
    },
    {
      "id": "euro-hauler",
      "name": "Continental Hauler",
      "capacityCount": 96,
      "capacityGrams": 24000000,
      "capabilities": [
        "FRAGILE",
        "REFRIGERATED",
        "HAZARDOUS"
      ],
      "purchasePrice": 3600000,
      "resaleBasisPoints": 6500,
      "fuelCentsPerKilometer": 50,
      "dailyUpkeep": 4500,
      "repairCentsPerConditionPoint": 420,
      "minimumCompanyLevel": 8
    }
  ],
  "driverTiers": [
    {
      "id": "junior",
      "name": "Junior",
      "hiringCost": 22000,
      "dailyWage": 950,
      "errorChancePerMillion": 70000,
      "minimumCompanyLevel": 2
    },
    {
      "id": "profi",
      "name": "Profi",
      "hiringCost": 42000,
      "dailyWage": 1450,
      "errorChancePerMillion": 25000,
      "minimumCompanyLevel": 4
    }
  ],
  "packageTypes": [
    {
      "id": "parcel",
      "name": "Paket",
      "capabilities": [],
      "minWeightGrams": 2000,
      "maxWeightGrams": 220000,
      "baseReward": 7200,
      "minimumCompanyLevel": 1
    },
    {
      "id": "glass",
      "name": "Glasware",
      "capabilities": [
        "FRAGILE"
      ],
      "minWeightGrams": 5000,
      "maxWeightGrams": 180000,
      "baseReward": 9800,
      "minimumCompanyLevel": 1
    },
    {
      "id": "fresh",
      "name": "Frischware",
      "capabilities": [
        "REFRIGERATED"
      ],
      "minWeightGrams": 10000,
      "maxWeightGrams": 300000,
      "baseReward": 12600,
      "minimumCompanyLevel": 3
    },
    {
      "id": "chemical",
      "name": "Laborbedarf",
      "capabilities": [
        "HAZARDOUS"
      ],
      "minWeightGrams": 20000,
      "maxWeightGrams": 450000,
      "baseReward": 17800,
      "minimumCompanyLevel": 4
    },
    {
      "id": "electronics",
      "name": "Electronics",
      "capabilities": [
        "FRAGILE"
      ],
      "minWeightGrams": 10000,
      "maxWeightGrams": 150000,
      "baseReward": 15000,
      "minimumCompanyLevel": 3
    },
    {
      "id": "medical",
      "name": "Medical cold chain",
      "capabilities": [
        "FRAGILE",
        "REFRIGERATED"
      ],
      "minWeightGrams": 20000,
      "maxWeightGrams": 280000,
      "baseReward": 22000,
      "minimumCompanyLevel": 5
    },
    {
      "id": "bulk",
      "name": "Bulk supplies",
      "capabilities": [],
      "minWeightGrams": 400000,
      "maxWeightGrams": 1600000,
      "baseReward": 20000,
      "minimumCompanyLevel": 4
    },
    {
      "id": "machinery",
      "name": "Industrial machinery",
      "capabilities": [
        "FRAGILE"
      ],
      "minWeightGrams": 800000,
      "maxWeightGrams": 2400000,
      "baseReward": 36000,
      "minimumCompanyLevel": 6
    }
  ],
  "risks": [
    {
      "id": "calm",
      "name": "Ruhige Fahrt",
      "chancePerMillion": 20000,
      "rewardBasisPoints": 0,
      "penaltyBasisPoints": 800
    },
    {
      "id": "rush",
      "name": "Berufsverkehr",
      "chancePerMillion": 90000,
      "rewardBasisPoints": 900,
      "penaltyBasisPoints": 1600
    }
  ],
  "regions": [
    {
      "id": "ruhr",
      "name": "Ruhrgebiet",
      "minimumCompletedJobs": 0,
      "minimumOwnedVehicles": 0,
      "minimumGameDay": 1
    },
    {
      "id": "rhein-main",
      "name": "Rhein-Main",
      "minimumCompletedJobs": 8,
      "minimumOwnedVehicles": 1,
      "minimumGameDay": 4
    },
    {
      "id": "elbe",
      "name": "Elbe-Korridor",
      "minimumCompletedJobs": 18,
      "minimumOwnedVehicles": 2,
      "minimumGameDay": 10
    },
    {
      "id": "danube",
      "name": "Danube Network",
      "minimumCompletedJobs": 45,
      "minimumOwnedVehicles": 3,
      "minimumGameDay": 12,
      "minimumDistinctVehicleSpecs": 3
    },
    {
      "id": "north-sea",
      "name": "North Sea Network",
      "minimumCompletedJobs": 90,
      "minimumOwnedVehicles": 4,
      "minimumGameDay": 18,
      "minimumDistinctVehicleSpecs": 4
    },
    {
      "id": "berlin",
      "name": "Capital Network",
      "minimumCompletedJobs": 160,
      "minimumOwnedVehicles": 5,
      "minimumGameDay": 23,
      "minimumDistinctVehicleSpecs": 5
    },
    {
      "id": "alpine",
      "name": "Black Forest Network",
      "minimumCompletedJobs": 280,
      "minimumOwnedVehicles": 6,
      "minimumGameDay": 30,
      "minimumDistinctVehicleSpecs": 6
    }
  ],
  "locations": [
    {
      "id": "essen-hub",
      "regionId": "ruhr",
      "name": "Essen Depot"
    },
    {
      "id": "dortmund-market",
      "regionId": "ruhr",
      "name": "Dortmund Markt"
    },
    {
      "id": "frankfurt-terminal",
      "regionId": "rhein-main",
      "name": "Frankfurt Terminal"
    },
    {
      "id": "mainz-quay",
      "regionId": "rhein-main",
      "name": "Mainz Kai"
    },
    {
      "id": "hamburg-port",
      "regionId": "elbe",
      "name": "Hamburg Hafen"
    },
    {
      "id": "leipzig-yard",
      "regionId": "elbe",
      "name": "Leipzig Hof"
    },
    {
      "id": "munich-centre",
      "regionId": "danube",
      "name": "Munich Logistics Centre"
    },
    {
      "id": "nuremberg-terminal",
      "regionId": "danube",
      "name": "Nuremberg Terminal"
    },
    {
      "id": "bremen-port",
      "regionId": "north-sea",
      "name": "Bremen Port"
    },
    {
      "id": "kiel-dock",
      "regionId": "north-sea",
      "name": "Kiel Dock"
    },
    {
      "id": "berlin-hub",
      "regionId": "berlin",
      "name": "Berlin Hub"
    },
    {
      "id": "magdeburg-depot",
      "regionId": "berlin",
      "name": "Magdeburg Depot"
    },
    {
      "id": "stuttgart-terminal",
      "regionId": "alpine",
      "name": "Stuttgart Terminal"
    },
    {
      "id": "freiburg-hub",
      "regionId": "alpine",
      "name": "Freiburg Hub"
    }
  ],
  "routes": [
    {
      "id": "ruhr-east",
      "originId": "essen-hub",
      "destinationId": "dortmund-market",
      "distanceMeters": 34000,
      "durationGameMinutes": 12
    },
    {
      "id": "ruhr-west",
      "originId": "dortmund-market",
      "destinationId": "essen-hub",
      "distanceMeters": 36000,
      "durationGameMinutes": 13
    },
    {
      "id": "main-loop",
      "originId": "frankfurt-terminal",
      "destinationId": "mainz-quay",
      "distanceMeters": 44000,
      "durationGameMinutes": 16
    },
    {
      "id": "elbe-line",
      "originId": "hamburg-port",
      "destinationId": "leipzig-yard",
      "distanceMeters": 390000,
      "durationGameMinutes": 90
    },
    {
      "id": "ruhr-scenic",
      "originId": "essen-hub",
      "destinationId": "dortmund-market",
      "distanceMeters": 46000,
      "durationGameMinutes": 17
    },
    {
      "id": "main-scenic",
      "originId": "frankfurt-terminal",
      "destinationId": "mainz-quay",
      "distanceMeters": 58000,
      "durationGameMinutes": 23
    },
    {
      "id": "danube-east",
      "originId": "munich-centre",
      "destinationId": "nuremberg-terminal",
      "distanceMeters": 170000,
      "durationGameMinutes": 48
    },
    {
      "id": "danube-west",
      "originId": "nuremberg-terminal",
      "destinationId": "munich-centre",
      "distanceMeters": 180000,
      "durationGameMinutes": 52
    },
    {
      "id": "north-sea-east",
      "originId": "bremen-port",
      "destinationId": "kiel-dock",
      "distanceMeters": 240000,
      "durationGameMinutes": 60
    },
    {
      "id": "north-sea-west",
      "originId": "kiel-dock",
      "destinationId": "bremen-port",
      "distanceMeters": 270000,
      "durationGameMinutes": 66
    },
    {
      "id": "capital-west",
      "originId": "berlin-hub",
      "destinationId": "magdeburg-depot",
      "distanceMeters": 170000,
      "durationGameMinutes": 45
    },
    {
      "id": "capital-east",
      "originId": "magdeburg-depot",
      "destinationId": "berlin-hub",
      "distanceMeters": 180000,
      "durationGameMinutes": 50
    },
    {
      "id": "forest-south",
      "originId": "stuttgart-terminal",
      "destinationId": "freiburg-hub",
      "distanceMeters": 200000,
      "durationGameMinutes": 56
    },
    {
      "id": "forest-north",
      "originId": "freiburg-hub",
      "destinationId": "stuttgart-terminal",
      "distanceMeters": 210000,
      "durationGameMinutes": 59
    },
    {
      "id": "ruhr-main",
      "originId": "essen-hub",
      "destinationId": "frankfurt-terminal",
      "distanceMeters": 180000,
      "durationGameMinutes": 50
    },
    {
      "id": "main-elbe",
      "originId": "frankfurt-terminal",
      "destinationId": "hamburg-port",
      "distanceMeters": 410000,
      "durationGameMinutes": 90
    },
    {
      "id": "elbe-capital",
      "originId": "leipzig-yard",
      "destinationId": "berlin-hub",
      "distanceMeters": 185000,
      "durationGameMinutes": 50
    },
    {
      "id": "main-danube",
      "originId": "frankfurt-terminal",
      "destinationId": "nuremberg-terminal",
      "distanceMeters": 390000,
      "durationGameMinutes": 85
    },
    {
      "id": "danube-forest",
      "originId": "munich-centre",
      "destinationId": "stuttgart-terminal",
      "distanceMeters": 220000,
      "durationGameMinutes": 60
    },
    {
      "id": "north-sea-ruhr",
      "originId": "bremen-port",
      "destinationId": "dortmund-market",
      "distanceMeters": 250000,
      "durationGameMinutes": 68
    },
    {
      "id": "elbe-north-sea",
      "originId": "hamburg-port",
      "destinationId": "kiel-dock",
      "distanceMeters": 240000,
      "durationGameMinutes": 62
    },
    {
      "id": "capital-forest",
      "originId": "berlin-hub",
      "destinationId": "stuttgart-terminal",
      "distanceMeters": 650000,
      "durationGameMinutes": 118
    }
  ],
  "economy": {
    "offersPerDay": 6,
    "offerLifetimeGameMinutes": 1440,
    "distanceRewardCentsPerKilometer": 55,
    "rentalCentsPerJob": 500,
    "maximumPenaltyBasisPoints": 2500,
    "maintenanceIntervalMeters": 250000,
    "maintenanceCostBasisPoints": 600,
    "severanceWageDays": 1,
    "historyLimit": 256,
    "parcelRewardCents": 350,
    "weightRewardCentsPerKilogram": 1,
    "offersPerUnlockedRegion": 2,
    "offersPerCompanyLevel": 1,
    "maximumOffersPerDay": 24,
    "parcelsPerCompanyLevel": 2
  },
  "progression": {
    "levels": [
      {
        "level": 1,
        "minimumCompletedJobs": 0,
        "minimumOwnedVehicles": 0,
        "minimumDrivers": 0,
        "minimumGameDay": 1
      },
      {
        "level": 2,
        "minimumCompletedJobs": 4,
        "minimumOwnedVehicles": 0,
        "minimumDrivers": 0,
        "minimumGameDay": 2
      },
      {
        "level": 3,
        "minimumCompletedJobs": 8,
        "minimumOwnedVehicles": 1,
        "minimumDrivers": 0,
        "minimumGameDay": 4
      },
      {
        "level": 4,
        "minimumCompletedJobs": 16,
        "minimumOwnedVehicles": 2,
        "minimumDrivers": 1,
        "minimumGameDay": 8
      },
      {
        "level": 5,
        "minimumCompletedJobs": 40,
        "minimumOwnedVehicles": 3,
        "minimumDrivers": 2,
        "minimumGameDay": 12,
        "minimumDistinctVehicleSpecs": 3
      },
      {
        "level": 6,
        "minimumCompletedJobs": 90,
        "minimumOwnedVehicles": 4,
        "minimumDrivers": 3,
        "minimumGameDay": 18,
        "minimumDistinctVehicleSpecs": 4
      },
      {
        "level": 7,
        "minimumCompletedJobs": 170,
        "minimumOwnedVehicles": 5,
        "minimumDrivers": 4,
        "minimumGameDay": 24,
        "minimumDistinctVehicleSpecs": 5
      },
      {
        "level": 8,
        "minimumCompletedJobs": 300,
        "minimumOwnedVehicles": 6,
        "minimumDrivers": 5,
        "minimumGameDay": 32,
        "minimumDistinctVehicleSpecs": 6
      }
    ]
  }
}
""".trimIndent()
}

fun newGame(catalog: GameCatalog = DefaultCatalog.value, seed: Long): GameState = GameState(
    catalogVersion = catalog.version,
    randomSeed = seed,
    vehicles = listOf(VehicleState(VehicleId("vehicle-1"), catalog.starterVehicleSpecId, Ownership.RENTAL)),
    nextEntitySequence = 2,
)
