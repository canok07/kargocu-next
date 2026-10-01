package com.canok.kargotycoon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/**
 * Small original geometric marks, drawn on a 24x24 grid. No bitmap assets and
 * no Material icon dependency: the whole glyph set is owned here.
 */
enum class Mark {
    Ledger, Dispatch, Fleet, Team, More,
    Parcel, Route, Clock, Map, Lock, Coin, Gauge,
    Advance, Alert, Info, CheckMag,
}

@Composable
fun KargoMark(mark: Mark, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val k = size.minDimension / 24f
        val ox = (size.width - 24f * k) / 2f
        val oy = (size.height - 24f * k) / 2f
        translate(ox, oy) {
            scale(k, k, pivot = Offset.Zero) {
                drawMark(mark, tint)
            }
        }
    }
}

private fun DrawScope.stroke(width: Float = 1.8f) =
    Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawMark(mark: Mark, color: Color) {
    when (mark) {
        Mark.Ledger -> {
            drawRoundRect(color, Offset(3.5f, 3.5f), Size(17f, 17f), CornerRadius(2f), style = stroke())
            listOf(8f, 12f, 16f).forEach { y -> drawLine(color, Offset(7f, y), Offset(17f, y), 1.6f, cap = StrokeCap.Round) }
            drawCircle(color, 1.1f, Offset(5.4f, 8f))
            drawCircle(color, 1.1f, Offset(5.4f, 12f))
            drawCircle(color, 1.1f, Offset(5.4f, 16f))
        }
        Mark.Dispatch -> {
            listOf(6f, 12f, 18f).forEach { y ->
                drawCircle(color, 1.4f, Offset(5f, y))
                drawLine(color, Offset(9f, y), Offset(20f, y), 1.9f, cap = StrokeCap.Round)
            }
        }
        Mark.Fleet -> {
            drawRoundRect(color, Offset(2f, 8f), Size(12f, 8f), CornerRadius(1.2f), style = stroke(1.7f))
            val cab = Path().apply {
                moveTo(14f, 9f); lineTo(18.5f, 9f); lineTo(21.5f, 12f); lineTo(21.5f, 16f); lineTo(14f, 16f); close()
            }
            drawPath(cab, color, style = stroke(1.7f))
            drawCircle(color, 2.1f, Offset(7f, 17f), style = stroke(1.6f))
            drawCircle(color, 2.1f, Offset(18f, 17f), style = stroke(1.6f))
        }
        Mark.Team -> {
            drawCircle(color, 2.9f, Offset(8.5f, 8f), style = stroke(1.7f))
            val body = Path().apply { moveTo(3.5f, 19f); cubicTo(3.5f, 14.5f, 13.5f, 14.5f, 13.5f, 19f) }
            drawPath(body, color, style = stroke(1.7f))
            drawCircle(color, 2.2f, Offset(16.5f, 9f), style = stroke(1.5f))
            val shoulder = Path().apply { moveTo(14f, 18.6f); cubicTo(14.5f, 15.2f, 20.5f, 15.2f, 21f, 18.6f) }
            drawPath(shoulder, color, style = stroke(1.5f))
        }
        Mark.More -> listOf(6f, 12f, 18f).forEach { y -> drawCircle(color, 1.9f, Offset(12f, y)) }
        Mark.Parcel -> {
            drawRect(color, Offset(4f, 7f), Size(16f, 13f), style = stroke(1.7f))
            drawLine(color, Offset(4f, 11.5f), Offset(20f, 11.5f), 1.5f)
            drawLine(color, Offset(12f, 7f), Offset(12f, 20f), 1.5f)
        }
        Mark.Route -> {
            drawLine(color, Offset(6.5f, 16.5f), Offset(17.5f, 7.5f), 1.8f, cap = StrokeCap.Round)
            drawCircle(color, 2.7f, Offset(6.5f, 16.5f), style = stroke(1.7f))
            drawCircle(color, 2.7f, Offset(17.5f, 7.5f), style = stroke(1.7f))
        }
        Mark.Clock -> {
            drawCircle(color, 8.6f, Offset(12f, 12f), style = stroke(1.7f))
            drawLine(color, Offset(12f, 12f), Offset(12f, 6.5f), 1.8f, cap = StrokeCap.Round)
            drawLine(color, Offset(12f, 12f), Offset(16.4f, 13.6f), 1.8f, cap = StrokeCap.Round)
        }
        Mark.Map -> {
            val outline = Path().apply {
                moveTo(3f, 6.5f); lineTo(9f, 4f); lineTo(15f, 7f); lineTo(21f, 4.5f)
                lineTo(21f, 17.5f); lineTo(15f, 20f); lineTo(9f, 17f); lineTo(3f, 19.5f); close()
            }
            drawPath(outline, color, style = stroke(1.6f))
            drawLine(color, Offset(9f, 4f), Offset(9f, 17f), 1.3f)
            drawLine(color, Offset(15f, 7f), Offset(15f, 20f), 1.3f)
        }
        Mark.Lock -> {
            drawRoundRect(color, Offset(5f, 11f), Size(14f, 9f), CornerRadius(1.4f), style = stroke(1.7f))
            drawArc(color, 180f, 180f, false, Offset(8f, 5.5f), Size(8f, 11f), style = stroke(1.7f))
        }
        Mark.Coin -> {
            drawCircle(color, 8.6f, Offset(12f, 12f), style = stroke(1.7f))
            drawArc(color, 55f, 250f, false, Offset(7.6f, 7.6f), Size(8.8f, 8.8f), style = stroke(1.6f))
            drawLine(color, Offset(9.6f, 10.4f), Offset(14.4f, 10.4f), 1.4f, cap = StrokeCap.Round)
            drawLine(color, Offset(9.6f, 13.6f), Offset(14.4f, 13.6f), 1.4f, cap = StrokeCap.Round)
        }
        Mark.Gauge -> {
            drawArc(color, 180f, 180f, false, Offset(3.5f, 6.5f), Size(17f, 17f), style = stroke(1.7f))
            drawLine(color, Offset(12f, 15f), Offset(16f, 9.5f), 1.8f, cap = StrokeCap.Round)
            drawCircle(color, 1.5f, Offset(12f, 15f))
        }
        Mark.Advance -> {
            val first = Path().apply { moveTo(6f, 6.5f); lineTo(12f, 12f); lineTo(6f, 17.5f) }
            val second = Path().apply { moveTo(12f, 6.5f); lineTo(18f, 12f); lineTo(12f, 17.5f) }
            drawPath(first, color, style = stroke(1.9f))
            drawPath(second, color, style = stroke(1.9f))
        }
        Mark.Alert -> {
            val triangle = Path().apply { moveTo(12f, 4f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close() }
            drawPath(triangle, color, style = stroke(1.7f))
            drawLine(color, Offset(12f, 9.5f), Offset(12f, 14f), 1.7f, cap = StrokeCap.Round)
            drawCircle(color, 1.2f, Offset(12f, 16.6f))
        }
        Mark.Info -> {
            drawCircle(color, 8.6f, Offset(12f, 12f), style = stroke(1.7f))
            drawLine(color, Offset(12f, 11f), Offset(12f, 16f), 1.7f, cap = StrokeCap.Round)
            drawCircle(color, 1.2f, Offset(12f, 8f))
        }
        Mark.CheckMag -> {
            val check = Path().apply { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f) }
            drawPath(check, color, style = stroke(2f))
        }
    }
}
