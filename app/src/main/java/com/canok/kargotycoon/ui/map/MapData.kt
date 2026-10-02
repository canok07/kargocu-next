package com.canok.kargotycoon.ui.map

import com.canok.kargotycoon.R

/** Fractional (0..1) schematic positions on the Germany board. Presentation data only. */
data class MapPosition(val x: Float, val y: Float, val cityLabel: Int)

/** Fractional bounding box for a region blob. */
data class RegionBox(val regionId: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * A hand-placed schematic layout of the catalog network. Kept separate from the
 * map composable so screen code holds no geometry literals.
 */
object MapBoard {
    private val positions: Map<String, MapPosition> = mapOf(
        "essen-hub" to MapPosition(0.15f, 0.30f, R.string.map_city_essen),
        "dortmund-market" to MapPosition(0.22f, 0.60f, R.string.map_city_dortmund),
        "frankfurt-terminal" to MapPosition(0.53f, 0.36f, R.string.map_city_frankfurt),
        "mainz-quay" to MapPosition(0.44f, 0.66f, R.string.map_city_mainz),
        "hamburg-port" to MapPosition(0.74f, 0.20f, R.string.map_city_hamburg),
        "leipzig-yard" to MapPosition(0.86f, 0.60f, R.string.map_city_leipzig),
    )

    val regions: List<RegionBox> = listOf(
        RegionBox("ruhr", 0.04f, 0.14f, 0.35f, 0.76f),
        RegionBox("rhein-main", 0.37f, 0.20f, 0.66f, 0.80f),
        RegionBox("elbe", 0.62f, 0.06f, 0.97f, 0.76f),
    )

    fun position(locationId: String): MapPosition? = positions[locationId]
}
