package com.alphasim.brain.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.alphasim.brain.data.db.AlphaEntity
import com.alphasim.brain.ui.theme.Signals
import java.util.Locale
import kotlin.math.abs

object Fmt {
    fun num(v: Double?, digits: Int = 2): String = v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "—"
    fun pct(v: Double?, digits: Int = 2): String = v?.let { String.format(Locale.US, "%.${digits}f%%", it * 100) } ?: "—"
    /** BRAIN reports margin as a fraction; the platform shows it in basis points (‱). */
    fun bps(v: Double?): String = v?.let { String.format(Locale.US, "%.2f‱", it * 10_000) } ?: "—"
    fun int(v: Int?): String = v?.toString() ?: "—"

    fun money(v: Double?): String {
        if (v == null) return "—"
        val a = abs(v)
        val sign = if (v < 0) "-" else ""
        return when {
            a >= 1e9 -> sign + String.format(Locale.US, "%.2fB", a / 1e9)
            a >= 1e6 -> sign + String.format(Locale.US, "%.2fM", a / 1e6)
            a >= 1e3 -> sign + String.format(Locale.US, "%.1fK", a / 1e3)
            else -> sign + String.format(Locale.US, "%.0f", a)
        }
    }

    fun duration(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m ${s % 60}s"
            else -> "${s / 3600}h ${(s % 3600) / 60}m"
        }
    }
}

/** BRAIN's in-sample thresholds (delay 1 / delay 0). */
object Thresholds {
    fun sharpe(delay: Int) = if (delay == 0) 2.0 else 1.25
    fun fitness(delay: Int) = if (delay == 0) 1.3 else 1.0
    const val TURNOVER_MIN = 0.01
    const val TURNOVER_MAX = 0.70
}

@Composable
fun sharpeColor(v: Double?, delay: Int): Color = when {
    v == null -> Signals.current.muted
    v >= Thresholds.sharpe(delay) -> Signals.current.good
    v >= Thresholds.sharpe(delay) * 0.75 -> Signals.current.warn
    else -> Signals.current.bad
}

@Composable
fun fitnessColor(v: Double?, delay: Int): Color = when {
    v == null -> Signals.current.muted
    v >= Thresholds.fitness(delay) -> Signals.current.good
    v >= Thresholds.fitness(delay) * 0.75 -> Signals.current.warn
    else -> Signals.current.bad
}

@Composable
fun turnoverColor(v: Double?): Color = when {
    v == null -> Signals.current.muted
    v < Thresholds.TURNOVER_MIN || v > Thresholds.TURNOVER_MAX -> Signals.current.bad
    else -> Signals.current.good
}

fun AlphaEntity.tagList(): List<String> = tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
