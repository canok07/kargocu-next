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
        "essen-hub" to MapPosition(0.12f, 0.36f, R.string.map_city_essen),
        "dortmund-market" to MapPosition(0.24f, 0.50f, R.string.map_city_dortmund),
        "frankfurt-terminal" to MapPosition(0.49f, 0.47f, R.string.map_city_frankfurt),
        "mainz-quay" to MapPosition(0.45f, 0.65f, R.string.map_city_mainz),
        "hamburg-port" to MapPosition(0.50f, 0.07f, R.string.map_city_hamburg),
        "leipzig-yard" to MapPosition(0.58f, 0.27f, R.string.map_city_leipzig),
        "bremen-port" to MapPosition(0.14f, 0.06f, R.string.map_city_bremen),
        "kiel-dock" to MapPosition(0.23f, 0.19f, R.string.map_city_kiel),
        "berlin-hub" to MapPosition(0.84f, 0.12f, R.string.map_city_berlin),
        "magdeburg-depot" to MapPosition(0.81f, 0.33f, R.string.map_city_magdeburg),
        "nuremberg-terminal" to MapPosition(0.76f, 0.54f, R.string.map_city_nuremberg),
        "munich-centre" to MapPosition(0.86f, 0.74f, R.string.map_city_munich),
        "stuttgart-terminal" to MapPosition(0.17f, 0.76f, R.string.map_city_stuttgart),
        "freiburg-hub" to MapPosition(0.28f, 0.90f, R.string.map_city_freiburg),
    )

    val regions: List<RegionBox> = listOf(
        RegionBox("north-sea", 0.04f, 0.02f, 0.33f, 0.26f),
        RegionBox("ruhr", 0.04f, 0.29f, 0.33f, 0.61f),
        RegionBox("elbe", 0.36f, 0.02f, 0.70f, 0.39f),
        RegionBox("berlin", 0.71f, 0.06f, 0.96f, 0.46f),
        RegionBox("rhein-main", 0.34f, 0.43f, 0.65f, 0.78f),
        RegionBox("danube", 0.67f, 0.49f, 0.96f, 0.88f),
        RegionBox("alpine", 0.05f, 0.70f, 0.33f, 0.98f),
    )

    fun position(locationId: String): MapPosition? = positions[locationId]
}
