package com.canok.kargotycoon.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** Screens reachable in the app. Detail routes carry only stable entity ids. */
sealed interface Route {
    data object Dashboard : Route
    data object Jobs : Route
    data object Fleet : Route
    data object Team : Route
    data object More : Route
    data class JobOffer(val offerId: String) : Route
    data class Vehicle(val vehicleId: String) : Route
    data object Purchase : Route
    data class Driver(val driverId: String) : Route
    data object Hire : Route
    data class AssignDriver(val vehicleId: String) : Route
    data object Map : Route
    data object Company : Route
    data object Settings : Route

    fun encode(): String = when (this) {
        Dashboard -> "dashboard"
        Jobs -> "jobs"
        Fleet -> "fleet"
        Team -> "team"
        More -> "more"
        is JobOffer -> "offer|$offerId"
        is Vehicle -> "vehicle|$vehicleId"
        Purchase -> "purchase"
        is Driver -> "driver|$driverId"
        Hire -> "hire"
        is AssignDriver -> "assign|$vehicleId"
        Map -> "map"
        Company -> "company"
        Settings -> "settings"
    }

    /** The bottom-bar root this route belongs to. */
    val tab: Route
        get() = when (this) {
            Dashboard -> Dashboard
            Jobs, is JobOffer -> Jobs
            Fleet, is Vehicle, Purchase, is AssignDriver -> Fleet
            Team, is Driver, Hire -> Team
            More, Map, Company, Settings -> More
        }
}

fun decodeRoute(value: String): Route? {
    val parts = value.split('|')
    return when (parts.firstOrNull()) {
        "dashboard" -> Route.Dashboard
        "jobs" -> Route.Jobs
        "fleet" -> Route.Fleet
        "team" -> Route.Team
        "more" -> Route.More
        "offer" -> parts.getOrNull(1)?.let { Route.JobOffer(it) }
        "vehicle" -> parts.getOrNull(1)?.let { Route.Vehicle(it) }
        "purchase" -> Route.Purchase
        "driver" -> parts.getOrNull(1)?.let { Route.Driver(it) }
        "hire" -> Route.Hire
        "assign" -> parts.getOrNull(1)?.let { Route.AssignDriver(it) }
        "map" -> Route.Map
        "company" -> Route.Company
        "settings" -> Route.Settings
        else -> null
    }
}

val BackStackSaver: Saver<SnapshotStateList<Route>, Any> = Saver(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        (saved as List<String>).mapNotNull(::decodeRoute).ifEmpty { listOf(Route.Dashboard) }.toMutableStateList()
    },
)

/** A tiny saveable back stack; no navigation library required. */
class Navigator(val stack: SnapshotStateList<Route>) {
    constructor(initial: Route = Route.Dashboard) : this(mutableStateListOf(initial))

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: Route) {
        if (stack.last() != route) stack.add(route)
    }

    fun selectTab(root: Route) {
        stack.clear()
        stack.add(root)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun popToRoot() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
