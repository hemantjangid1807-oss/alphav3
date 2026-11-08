package com.alphasim.brain.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

object AlphaStatus {
    const val IDLE = "IDLE"
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val COMPLETE = "COMPLETE"
    const val ERROR = "ERROR"
}

@Serializable
data class CheckResult(
    val name: String,
    val result: String,
    val limit: Double? = null,
    val value: Double? = null,
    val message: String? = null,
) {
    val passed get() = result == "PASS"
    val failed get() = result == "FAIL" || result == "ERROR"
    val pending get() = result == "PENDING"

    val label: String
        get() = name.lowercase().split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    companion object {
        private val CheckJson = Json { ignoreUnknownKeys = true }
        private val listSer = ListSerializer(serializer())
        fun encodeList(list: List<CheckResult>): String = CheckJson.encodeToString(listSer, list)
        fun decodeList(json: String?): List<CheckResult> =
            if (json.isNullOrBlank()) emptyList() else runCatching { CheckJson.decodeFromString(listSer, json) }.getOrDefault(emptyList())
    }
}

/** The subset of GET /alphas/{id} the app stores. */
data class AlphaResult(
    val id: String,
    val sharpe: Double?,
    val fitness: Double?,
    val turnover: Double?,
    val returns: Double?,
    val drawdown: Double?,
    val margin: Double?,
    val pnl: Double?,
    val bookSize: Double?,
    val longCount: Int?,
    val shortCount: Int?,
    val grade: String?,
    val stage: String?,
    val status: String?,
    val name: String?,
    val favorite: Boolean,
    val tags: List<String>,
    val checks: List<CheckResult>,
)

/** A BRAIN recordset (e.g. pnl, yearly-stats): column names plus row arrays. */
data class Recordset(val columns: List<String>, val titles: List<String>, val rows: List<List<JsonElement>>) {
    fun col(name: String): Int = columns.indexOf(name)

    fun doubleAt(row: Int, col: Int): Double? =
        (rows.getOrNull(row)?.getOrNull(col) as? JsonPrimitive)?.doubleOrNull

    fun stringAt(row: Int, col: Int): String? =
        (rows.getOrNull(row)?.getOrNull(col) as? JsonPrimitive)?.contentOrNull

    companion object {
        fun parse(obj: JsonObject): Recordset {
            val props = (obj.obj("schema")?.get("properties") as? JsonArray).orEmpty()
            val names = props.map { (it as? JsonObject)?.str("name").orEmpty() }
            val titles = props.map { p -> (p as? JsonObject)?.let { it.str("title") ?: it.str("name") }.orEmpty() }
            val rows = (obj["records"] as? JsonArray).orEmpty().map { r -> (r as? JsonArray)?.toList().orEmpty() }
            return Recordset(names, titles, rows)
        }
    }
}

/* ---------- small JSON helpers shared by the API layer ---------- */

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
fun JsonObject.int(key: String): Int? = dbl(key)?.toInt()
fun JsonObject.bool(key: String): Boolean? = when (str(key)?.lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
}
