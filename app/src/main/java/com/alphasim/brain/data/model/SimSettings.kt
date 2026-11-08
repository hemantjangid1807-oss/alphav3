package com.alphasim.brain.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Simulation settings as accepted by POST /simulations ("settings" object). */
@Serializable
data class SimSettings(
    val instrumentType: String = "EQUITY",
    val region: String = "USA",
    val universe: String = "TOP3000",
    val delay: Int = 1,
    val decay: Int = 4,
    val neutralization: String = "SUBINDUSTRY",
    val truncation: Double = 0.08,
    val pasteurization: String = "ON",
    val unitHandling: String = "VERIFY",
    val nanHandling: String = "OFF",
    val language: String = "FASTEXPR",
    val visualization: Boolean = false,
    val testPeriod: String = "P0Y0M",
    /** "DEFAULT" means the field is left out of the request. */
    val maxTrade: String = "DEFAULT",
) {
    fun summary(): String = "$region · $universe · D$delay · $neutralization · decay $decay · trunc ${trimNum(truncation)}"

    fun toApiJson(): JsonObject = buildJsonObject {
        put("instrumentType", instrumentType)
        put("region", region)
        put("universe", universe)
        put("delay", delay)
        put("decay", decay)
        put("neutralization", neutralization)
        put("truncation", truncation)
        put("pasteurization", pasteurization)
        put("unitHandling", unitHandling)
        put("nanHandling", nanHandling)
        put("language", language)
        put("visualization", visualization)
        if (testPeriod.isNotBlank()) put("testPeriod", testPeriod)
        if (maxTrade == "ON" || maxTrade == "OFF") put("maxTrade", maxTrade)
    }

    fun encode(): String = SettingsJson.encodeToString(serializer(), this)

    companion object {
        private val SettingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(json: String?): SimSettings? =
            if (json.isNullOrBlank()) null else runCatching { SettingsJson.decodeFromString(serializer(), json) }.getOrNull()

        fun simulationPayload(expression: String, settings: SimSettings): JsonObject = buildJsonObject {
            put("type", "REGULAR")
            put("settings", settings.toApiJson())
            put("regular", expression)
        }

        private fun trimNum(d: Double): String {
            val s = d.toString()
            return if (s.endsWith(".0")) s.dropLast(2) else s
        }
    }
}

/** Option lists for the settings editor. Fields stay free-text editable because availability depends on the account. */
object SettingsCatalog {
    val regions = listOf("USA", "GLB", "EUR", "ASI", "CHN", "KOR", "TWN", "JPN", "HKG", "AMR")

    private val universes = mapOf(
        "USA" to listOf("TOP3000", "TOP1000", "TOP500", "TOP200", "TOPSP500", "ILLIQUID_MINVOL1M"),
        "GLB" to listOf("TOP3000", "MINVOL1M", "TOPDIV3000"),
        "EUR" to listOf("TOP2500", "TOP1200", "TOP800", "TOP400", "ILLIQUID_MINVOL1M"),
        "ASI" to listOf("MINVOL1M", "ILLIQUID_MINVOL1M"),
        "CHN" to listOf("TOP2000U"),
        "KOR" to listOf("TOP600"),
        "TWN" to listOf("TOP500", "TOP100"),
        "JPN" to listOf("TOP1600", "TOP1200"),
        "HKG" to listOf("TOP800", "TOP500"),
        "AMR" to listOf("TOP600"),
    )

    private val delay1Only = setOf("GLB", "ASI")

    val neutralizations = listOf(
        "NONE", "MARKET", "SECTOR", "INDUSTRY", "SUBINDUSTRY", "COUNTRY",
        "STATISTICAL", "CROWDING", "FAST", "SLOW", "SLOW_AND_FAST", "REVERSION_AND_MOMENTUM",
    )
    val onOff = listOf("ON", "OFF")
    val unitHandling = listOf("VERIFY")
    val languages = listOf("FASTEXPR")
    val testPeriods = listOf("P0Y0M", "P0Y6M", "P1Y0M", "P1Y6M", "P2Y0M", "P3Y0M")
    val maxTrade = listOf("DEFAULT", "ON", "OFF")

    fun universesFor(region: String): List<String> = universes[region] ?: listOf("TOP3000")
    fun delaysFor(region: String): List<Int> = if (region in delay1Only) listOf(1) else listOf(0, 1)

    fun defaultsFor(region: String): SimSettings = SimSettings(
        region = region,
        universe = universesFor(region).first(),
        delay = 1,
    )

    /** Guess the region from a file name like "brain_alphas_GLB_200.txt", falling back to the content. */
    fun guessRegion(fileName: String?, content: String): String {
        val upper = fileName.orEmpty().uppercase()
        regions.firstOrNull { Regex("(^|[^A-Z])$it([^A-Z]|$)").containsMatchIn(upper) }?.let { return it }
        return when {
            content.contains("fnd72_") || content.contains("closing_price_dlr1") -> "GLB"
            else -> "USA"
        }
    }
}
