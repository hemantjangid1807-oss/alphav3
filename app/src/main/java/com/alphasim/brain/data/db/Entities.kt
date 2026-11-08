package com.alphasim.brain.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alphasim.brain.data.model.AlphaStatus
import com.alphasim.brain.data.model.CheckResult
import com.alphasim.brain.data.model.SimSettings

@Entity(tableName = "lists")
data class AlphaListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String? = null,
    val settingsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "alphas",
    indices = [Index("listId"), Index("status"), Index("simulationUrl")],
)
data class AlphaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val position: Int,
    val expression: String,
    val settingsOverrideJson: String? = null,
    val status: String = AlphaStatus.IDLE,
    val progress: Double? = null,
    /** Location returned by POST /simulations; kept so polling can resume after a restart. */
    val simulationUrl: String? = null,
    /** Index of this alpha inside a multi-simulation request. */
    val multiIndex: Int? = null,
    val queuedAt: Long? = null,
    val alphaId: String? = null,
    val message: String? = null,
    val attempts: Int = 0,
    val sharpe: Double? = null,
    val fitness: Double? = null,
    val turnover: Double? = null,
    val returns: Double? = null,
    val drawdown: Double? = null,
    val margin: Double? = null,
    val pnl: Double? = null,
    val bookSize: Double? = null,
    val longCount: Int? = null,
    val shortCount: Int? = null,
    val grade: String? = null,
    val alphaStage: String? = null,
    val alphaStatus: String? = null,
    val alphaName: String? = null,
    val tags: String? = null,
    val checksJson: String? = null,
    val failedChecks: Int? = null,
    val pendingChecks: Int? = null,
    val selfCorr: Double? = null,
    val prodCorr: Double? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val favorite: Boolean = false,
)

// Computed helpers live outside the entities so Room never treats them as columns.
val AlphaListEntity.settings: SimSettings get() = SimSettings.decode(settingsJson) ?: SimSettings()
val AlphaEntity.checks: List<CheckResult> get() = CheckResult.decodeList(checksJson)
val AlphaEntity.overrideSettings: SimSettings? get() = SimSettings.decode(settingsOverrideJson)
val AlphaEntity.passesAllChecks: Boolean get() = status == AlphaStatus.COMPLETE && failedChecks == 0

@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey val key: String,
    val value: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)

/** Aggregates per list for the home screen. */
data class ListStats(
    val listId: Long,
    val total: Int,
    val complete: Int,
    val running: Int,
    val queued: Int,
    val errors: Int,
    val passing: Int,
    val bestSharpe: Double?,
)
