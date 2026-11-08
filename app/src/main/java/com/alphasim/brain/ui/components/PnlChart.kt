package com.alphasim.brain.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.alphasim.brain.ui.theme.CodeStyle
import com.alphasim.brain.ui.theme.Signals

data class SeriesPoint(val label: String, val value: Double)

/** Cumulative PnL line chart drawn on a Canvas; drag or tap to read a value. */
@Composable
fun PnlChart(points: List<SeriesPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Text("Not enough data to draw a chart.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val data = remember(points) { downsample(points, 600) }
    val minV = remember(data) { minOf(0.0, data.minOf { it.value }) }
    val maxV = remember(data) { maxOf(0.0, data.maxOf { it.value }) }
    val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
    var selected by remember(data) { mutableStateOf<Int?>(null) }
    val colors = Signals.current
    val zeroColor = MaterialTheme.colorScheme.outline
    val cursorColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        val shown = selected?.let { data[it] } ?: data.last()
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(shown.label, style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(Fmt.money(shown.value), style = CodeStyle, color = if (shown.value >= 0) colors.good else colors.bad)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(data) {
                    detectTapGestures { o -> selected = indexAt(o.x, size.width.toFloat(), data.size) }
                }
                .pointerInput(data) {
                    detectDragGestures(
                        onDragEnd = { selected = null },
                        onDragCancel = { selected = null },
                    ) { change, _ -> selected = indexAt(change.position.x, size.width.toFloat(), data.size) }
                }
        ) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = if (data.size == 1) 0f else i * w / (data.size - 1)
            fun y(v: Double) = (h - ((v - minV) / span * h)).toFloat()

            // grid
            for (k in 1..3) {
                val gy = h * k / 4f
                drawLine(colors.grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            // zero line
            drawLine(
                zeroColor, Offset(0f, y(0.0)), Offset(w, y(0.0)), strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )

            val line = Path()
            data.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(i), y(p.value)) else line.lineTo(x(i), y(p.value)) }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(data.lastIndex), y(0.0))
                lineTo(x(0), y(0.0))
                close()
            }
            drawPath(fill, colors.chartFill)
            drawPath(line, colors.chartLine, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            selected?.let { i ->
                val cx = x(i)
                drawLine(cursorColor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1f)
                drawCircle(colors.chartLine, radius = 5f, center = Offset(cx, y(data[i].value)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(data.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("max ${Fmt.money(maxV)} · min ${Fmt.money(minV)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(data.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun indexAt(x: Float, width: Float, count: Int): Int =
    if (width <= 0f || count <= 1) 0 else ((x / width) * (count - 1)).toInt().coerceIn(0, count - 1)

private fun downsample(points: List<SeriesPoint>, max: Int): List<SeriesPoint> {
    if (points.size <= max) return points
    val step = points.size.toDouble() / max
    val out = ArrayList<SeriesPoint>(max + 1)
    var i = 0.0
    while (i < points.size) {
        out += points[i.toInt()]
        i += step
    }
    if (out.last() != points.last()) out += points.last()
    return out
}
