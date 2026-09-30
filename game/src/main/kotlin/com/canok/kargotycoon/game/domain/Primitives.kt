package com.canok.kargotycoon.game.domain

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {
    operator fun plus(other: Money): Money = Money(Math.addExact(cents, other.cents))
    operator fun minus(other: Money): Money = Money(Math.subtractExact(cents, other.cents))
    operator fun times(multiplier: Long): Money = Money(Math.multiplyExact(cents, multiplier))
    fun percentage(basisPoints: Int): Money = Money(Math.multiplyExact(cents, basisPoints.toLong()) / 10_000L)
    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    companion object {
        val ZERO = Money(0)
        fun euros(euros: Long): Money = Money(Math.multiplyExact(euros, 100L))
    }
}

@Serializable
@JvmInline
value class GameInstant(val millis: Long) : Comparable<GameInstant> {
    fun plusMillis(delta: Long): GameInstant = GameInstant(Math.addExact(millis, delta))
    override fun compareTo(other: GameInstant): Int = millis.compareTo(other.millis)
}

@Serializable @JvmInline value class VehicleSpecId(val value: String)
@Serializable @JvmInline value class VehicleId(val value: String)
@Serializable @JvmInline value class DriverTierId(val value: String)
@Serializable @JvmInline value class DriverId(val value: String)
@Serializable @JvmInline value class PackageTypeId(val value: String)
@Serializable @JvmInline value class RiskId(val value: String)
@Serializable @JvmInline value class RegionId(val value: String)
@Serializable @JvmInline value class LocationId(val value: String)
@Serializable @JvmInline value class RouteId(val value: String)
@Serializable @JvmInline value class OfferId(val value: String)
@Serializable @JvmInline value class JobId(val value: String)
@Serializable @JvmInline value class LedgerEntryId(val value: String)
@Serializable @JvmInline value class CommandId(val value: String)

@Serializable
enum class Capability { REFRIGERATED, FRAGILE, HAZARDOUS }

@Serializable
enum class Ownership { RENTAL, OWNED }

@Serializable
enum class VehicleStatus { AVAILABLE, BUSY, MAINTENANCE }

@Serializable
enum class DriverStatus { AVAILABLE, DRIVING, UNAVAILABLE }

@Serializable
enum class TutorialStep { ACCEPT_FIRST_JOB, COMPLETE_FIRST_JOB, BUY_FIRST_VEHICLE, HIRE_FIRST_DRIVER, START_PARALLEL_JOBS, COMPLETE }

@Serializable
enum class LedgerType { RESERVATION, RESERVATION_REFUND, REVENUE, PENALTY, FUEL, RENTAL, VEHICLE_PURCHASE, VEHICLE_SALE, REPAIR, MAINTENANCE, DRIVER_HIRE, DRIVER_WAGE, DRIVER_SEVERANCE }
