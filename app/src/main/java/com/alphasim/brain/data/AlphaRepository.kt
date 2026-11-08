package com.alphasim.brain.data

import androidx.room.withTransaction
import com.alphasim.brain.data.db.AlphaEntity
import com.alphasim.brain.data.db.AlphaListEntity
import com.alphasim.brain.data.db.AppDatabase
import com.alphasim.brain.data.db.CacheEntity
import com.alphasim.brain.data.db.overrideSettings
import com.alphasim.brain.data.db.settings
import com.alphasim.brain.data.model.AlphaResult
import com.alphasim.brain.data.model.AlphaStatus
import com.alphasim.brain.data.model.CheckResult
import com.alphasim.brain.data.model.SimSettings
import kotlinx.coroutines.flow.Flow

class AlphaRepository(private val db: AppDatabase) {
    private val lists = db.listDao()
    private val alphas = db.alphaDao()
    private val cache = db.cacheDao()

    /* ---------- observation ---------- */

    fun observeLists(): Flow<List<AlphaListEntity>> = lists.observeAll()
    fun observeStats() = lists.observeStats()
    fun observeList(id: Long) = lists.observe(id)
    fun observeAlphas(listId: Long) = alphas.observeByList(listId)
    fun observeAlpha(id: Long) = alphas.observe(id)
    fun observeCount(status: String) = alphas.observeCount(status)

    suspend fun list(id: Long) = lists.get(id)
    suspend fun allLists() = lists.all()
    suspend fun alpha(id: Long) = alphas.get(id)
    suspend fun alphasOf(listId: Long) = alphas.byList(listId)

    /* ---------- lists ---------- */

    suspend fun createList(name: String, source: String?, settings: SimSettings, expressions: List<String>): Long =
        db.withTransaction {
            val id = lists.insert(AlphaListEntity(name = name, source = source, settingsJson = settings.encode()))
            alphas.insertAll(expressions.mapIndexed { i, e -> AlphaEntity(listId = id, position = i + 1, expression = e) })
            id
        }

    /** Appends expressions to a list, skipping ones already in it. Returns how many were added. */
    suspend fun addExpressions(listId: Long, text: String): ExpressionTools.ParseResult = db.withTransaction {
        val parsed = ExpressionTools.parse(text, dedupe = true, existing = alphas.expressions(listId))
        val start = (alphas.maxPosition(listId) ?: 0) + 1
        alphas.insertAll(parsed.expressions.mapIndexed { i, e -> AlphaEntity(listId = listId, position = start + i, expression = e) })
        parsed
    }

    suspend fun renameList(id: Long, name: String) {
        lists.get(id)?.let { lists.update(it.copy(name = name)) }
    }

    suspend fun updateListSettings(id: Long, settings: SimSettings) {
        lists.get(id)?.let { lists.update(it.copy(settingsJson = settings.encode())) }
    }

    suspend fun duplicateList(id: Long): Long? {
        val src = lists.get(id) ?: return null
        val exprs = alphas.byList(id).map { it.expression }
        return createList("${src.name} (copy)", src.source, src.settings, exprs)
    }

    suspend fun deleteList(id: Long) = db.withTransaction {
        alphas.deleteByList(id)
        lists.delete(id)
    }

    /* ---------- alphas ---------- */

    suspend fun effectiveSettings(alpha: AlphaEntity): SimSettings =
        alpha.overrideSettings ?: lists.get(alpha.listId)?.settings ?: SimSettings()

    suspend fun updateExpression(id: Long, expression: String) {
        val a = alphas.get(id) ?: return
        if (a.status == AlphaStatus.RUNNING || a.status == AlphaStatus.QUEUED) return
        alphas.resetIds(listOf(id))
        alphas.get(id)?.let { alphas.update(it.copy(expression = expression.trim())) }
    }

    suspend fun setOverride(id: Long, settings: SimSettings?) {
        alphas.get(id)?.let { alphas.update(it.copy(settingsOverrideJson = settings?.encode())) }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        alphas.get(id)?.let { alphas.update(it.copy(favorite = favorite)) }
    }

    suspend fun setNameAndTags(id: Long, name: String?, tags: List<String>) {
        alphas.get(id)?.let { alphas.update(it.copy(alphaName = name, tags = tags.joinToString(","))) }
    }

    suspend fun queue(ids: List<Long>) {
        val now = System.currentTimeMillis()
        ids.chunked(500).forEach { alphas.queueIds(it, now) }
    }

    suspend fun reset(ids: List<Long>) = ids.chunked(500).forEach { alphas.resetIds(it) }
    suspend fun delete(ids: List<Long>) = ids.chunked(500).forEach { alphas.deleteIds(it) }
    suspend fun unqueueAll() = alphas.unqueueAll()
    suspend fun unqueueList(listId: Long) = alphas.unqueueList(listId)

    /* ---------- engine support ---------- */

    /**
     * Picks the next unit of work and marks it RUNNING: either an interrupted simulation (all rows sharing
     * its URL) or up to [maxBatch] fresh rows from the same list.
     */
    suspend fun claimNext(maxBatch: Int): List<AlphaEntity> = db.withTransaction {
        val head = alphas.nextQueued() ?: return@withTransaction emptyList<AlphaEntity>()
        val url = head.simulationUrl
        val batch = when {
            url != null -> alphas.queuedByUrl(url)
            maxBatch <= 1 -> listOf(head)
            else -> alphas.nextFreshInList(head.listId, maxBatch)
        }.ifEmpty { listOf(head) }
        alphas.markRunning(batch.map { it.id }, System.currentTimeMillis())
        batch.map { it.copy(status = AlphaStatus.RUNNING) }
    }

    suspend fun parkRunning() = alphas.parkRunning()
    suspend fun setSimulationUrl(id: Long, url: String?, multiIndex: Int?) = alphas.setSimulationUrl(id, url, multiIndex)
    suspend fun setProgress(ids: List<Long>, progress: Double?) = alphas.setProgress(ids, progress)
    suspend fun setMessage(id: Long, message: String?) = alphas.setMessage(id, message)
    suspend fun markError(id: Long, message: String) = alphas.markError(id, message.take(1000), System.currentTimeMillis())

    suspend fun markComplete(id: Long, result: AlphaResult, warning: String?) {
        val a = alphas.get(id) ?: return
        alphas.update(applyResult(a, result).copy(
            status = AlphaStatus.COMPLETE,
            message = warning,
            progress = null,
            simulationUrl = null,
            multiIndex = null,
            finishedAt = System.currentTimeMillis(),
        ))
    }

    /** Simulation finished but the alpha details couldn't be fetched; keep the id so it can be refreshed later. */
    suspend fun markCompleteWithoutStats(id: Long, alphaId: String, message: String) {
        val a = alphas.get(id) ?: return
        alphas.update(a.copy(
            status = AlphaStatus.COMPLETE, alphaId = alphaId, message = message, progress = null,
            simulationUrl = null, multiIndex = null, finishedAt = System.currentTimeMillis(),
        ))
    }

    /** Refresh stats for an alpha that already has a BRAIN id (doesn't touch status). */
    suspend fun applyAlphaResult(id: Long, result: AlphaResult) {
        val a = alphas.get(id) ?: return
        alphas.update(applyResult(a, result))
    }

    suspend fun updateChecks(id: Long, checks: List<CheckResult>) {
        val a = alphas.get(id) ?: return
        alphas.update(a.copy(
            checksJson = CheckResult.encodeList(checks),
            failedChecks = checks.count { it.failed },
            pendingChecks = checks.count { it.pending },
        ))
    }

    suspend fun setCorrelation(id: Long, kind: String, value: Double?) {
        val a = alphas.get(id) ?: return
        alphas.update(if (kind == "prod") a.copy(prodCorr = value) else a.copy(selfCorr = value))
    }

    suspend fun setAlphaStatus(id: Long, status: String?) {
        val a = alphas.get(id) ?: return
        alphas.update(a.copy(alphaStatus = status))
    }

    private fun applyResult(a: AlphaEntity, r: AlphaResult): AlphaEntity = a.copy(
        alphaId = r.id.ifBlank { a.alphaId },
        sharpe = r.sharpe,
        fitness = r.fitness,
        turnover = r.turnover,
        returns = r.returns,
        drawdown = r.drawdown,
        margin = r.margin,
        pnl = r.pnl,
        bookSize = r.bookSize,
        longCount = r.longCount,
        shortCount = r.shortCount,
        grade = r.grade,
        alphaStage = r.stage,
        alphaStatus = r.status,
        alphaName = r.name ?: a.alphaName,
        tags = if (r.tags.isEmpty()) a.tags else r.tags.joinToString(","),
        checksJson = CheckResult.encodeList(r.checks),
        failedChecks = r.checks.count { it.failed },
        pendingChecks = r.checks.count { it.pending },
    )

    /* ---------- cache (recordsets) ---------- */

    suspend fun cached(key: String): String? = cache.get(key)?.value
    suspend fun putCache(key: String, value: String) = cache.put(CacheEntity(key, value))
    suspend fun clearCache() = cache.clear()

    suspend fun resetEverything() = db.withTransaction {
        lists.all().forEach { l -> alphas.deleteByList(l.id); lists.delete(l.id) }
        cache.clear()
    }
}
