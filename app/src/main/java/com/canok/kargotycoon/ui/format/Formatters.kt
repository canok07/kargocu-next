package com.canok.kargotycoon.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Locale currently applied to the composition (game language, not device default). */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

private fun groupDigits(value: BigDecimal, locale: Locale, minFraction: Int, maxFraction: Int): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = minFraction
        maximumFractionDigits = maxFraction
        isGroupingUsed = true
    }.format(value)

/** Exact euro formatting from integer cents; never routes money through a Double. */
fun formatMoney(cents: Long, locale: Locale): String =
    NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("EUR")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(cents, 2))

fun formatMoneySigned(cents: Long, locale: Locale): String {
    val base = formatMoney(cents, locale)
    return if (cents > 0) "+$base" else base
}

fun formatKilograms(grams: Long, locale: Locale): String =
    groupDigits(BigDecimal.valueOf(grams, 3), locale, 0, if (grams % 1_000L == 0L) 0 else 1)

fun formatKilometers(meters: Long, locale: Locale): String =
    groupDigits(BigDecimal.valueOf(meters, 3), locale, 0, if (meters % 1_000L == 0L) 0 else 1)

fun formatInteger(value: Long, locale: Locale): String = groupDigits(BigDecimal.valueOf(value), locale, 0, 0)

fun formatPercent(value: Int, locale: Locale): String = "%d%%".format(value)

fun formatGameMinutesOfDay(millis: Long): String {
    val minutesOfDay = Math.floorMod(millis, DAY_MILLIS) / MINUTE_MILLIS
    return "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)
}

fun dayOfInstant(millis: Long): Int = Math.toIntExact(Math.floorDiv(millis, DAY_MILLIS) + 1L)

fun progressFraction(nowMillis: Long, startMillis: Long, endMillis: Long): Float {
    val span = endMillis - startMillis
    if (span <= 0L) return if (nowMillis >= endMillis) 1f else 0f
    return ((nowMillis - startMillis).toDouble() / span.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

const val MINUTE_MILLIS = 60_000L
const val DAY_MILLIS = 24L * 60L * MINUTE_MILLIS
