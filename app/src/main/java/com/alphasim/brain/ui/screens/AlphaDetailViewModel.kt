package com.alphasim.brain.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alphasim.brain.AppContainer
import com.alphasim.brain.data.api.ApiException
import com.alphasim.brain.data.api.AuthExpiredException
import com.alphasim.brain.data.api.BrainApi
import com.alphasim.brain.data.api.RateLimitException
import com.alphasim.brain.data.db.AlphaEntity
import com.alphasim.brain.data.model.Recordset
import com.alphasim.brain.ui.components.SeriesPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.io.IOException

class AlphaDetailViewModel(private val c: AppContainer, private val rowId: Long) : ViewModel() {

    val alpha: StateFlow<AlphaEntity?> =
        c.repo.observeAlpha(rowId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class Ui(
        val pnl: List<SeriesPoint>? = null,
        val pnlLoading: Boolean = false,
        val pnlError: String? = null,
        val yearly: Recordset? = null,
        val yearlyLoading: Boolean = false,
        val busy: String? = null,
        val message: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private var loadedFor: String? = null

    fun ensureLoaded(alphaId: String) {
        if (loadedFor == alphaId) return
        loadedFor = alphaId
        _ui.update { it.copy(pnl = null, yearly = null, pnlError = null) }
        loadPnl(alphaId, force = false)
        loadYearly(alphaId, force = false)
    }

    fun loadPnl(alphaId: String, force: Boolean) {
        viewModelScope.launch {
            _ui.update { it.copy(pnlLoading = true, pnlError = null) }
            try {
                val rs = Recordset.parse(cachedRecordset(alphaId, "pnl", force))
                val d = rs.col("date").takeIf { it >= 0 } ?: 0
                val p = rs.col("pnl").takeIf { it >= 0 } ?: 1
                val points = rs.rows.indices.mapNotNull { i ->
                    val v = rs.doubleAt(i, p) ?: return@mapNotNull null
                    SeriesPoint(rs.stringAt(i, d) ?: "", v)
                }
                _ui.update { it.copy(pnl = points, pnlLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(pnlLoading = false, pnlError = describe(e)) }
            }
        }
    }

    fun loadYearly(alphaId: String, force: Boolean) {
        viewModelScope.launch {
            _ui.update { it.copy(yearlyLoading = true) }
            val rs = runCatching { Recordset.parse(cachedRecordset(alphaId, "yearly-stats", force)) }.getOrNull()
            _ui.update { it.copy(yearly = rs, yearlyLoading = false) }
        }
    }

    private suspend fun cachedRecordset(alphaId: String, name: String, force: Boolean): JsonObject {
        val key = "rs:$alphaId:$name"
        if (!force) {
            c.repo.cached(key)?.let { s -> c.api.parseObject(s)?.let { return it } }
        }
        val obj = c.api.recordsetJson(alphaId, name)
        c.repo.putCache(key, obj.toString())
        return obj
    }

    fun refresh() = action("Refreshing") {
        val id = requireAlphaId()
        c.repo.applyAlphaResult(rowId, c.api.getAlpha(id))
        c.repo.setMessage(rowId, null)
        loadPnl(id, force = true)
        loadYearly(id, force = true)
        "Updated from BRAIN"
    }

    fun runChecks() = action("Running submission checks") {
        val checks = c.api.check(requireAlphaId())
        c.repo.updateChecks(rowId, checks)
        val failed = checks.count { it.failed }
        val pending = checks.count { it.pending }
        when {
            failed > 0 -> "$failed check${if (failed > 1) "s" else ""} failing"
            pending > 0 -> "$pending check${if (pending > 1) "s" else ""} still pending"
            else -> "All submission checks pass"
        }
    }

    fun correlation(kind: String) = action(if (kind == "prod") "Computing production correlation" else "Computing self-correlation") {
        val (max, _) = c.api.correlation(requireAlphaId(), kind)
        c.repo.setCorrelation(rowId, kind, max)
        "${if (kind == "prod") "Prod" else "Self"} correlation: ${max?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "n/a"}"
    }

    fun submit() = action("Submitting") {
        val id = requireAlphaId()
        when (val r = c.api.submit(id)) {
            is BrainApi.SubmitResult.Submitted -> {
                runCatching { c.repo.applyAlphaResult(rowId, c.api.getAlpha(id)) }
                "Submitted to BRAIN"
            }
            is BrainApi.SubmitResult.Rejected -> {
                if (r.checks.isNotEmpty()) c.repo.updateChecks(rowId, r.checks)
                "Not submitted: ${r.message}"
            }
        }
    }

    fun saveProperties(name: String?, tags: List<String>) = action("Saving") {
        alpha.value?.alphaId?.let { c.api.updateAlphaProperties(it, name, tags, null) }
        c.repo.setNameAndTags(rowId, name, tags)
        "Saved"
    }

    fun toggleFavorite() {
        val a = alpha.value ?: return
        val next = !a.favorite
        viewModelScope.launch {
            c.repo.setFavorite(rowId, next)
            a.alphaId?.let { id -> runCatching { c.api.updateAlphaProperties(id, null, null, next) } }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }
    fun say(message: String) = _ui.update { it.copy(message = message) }

    private fun requireAlphaId(): String =
        alpha.value?.alphaId ?: throw IllegalStateException("Simulate this alpha first")

    private fun action(label: String, block: suspend () -> String?) {
        if (_ui.value.busy != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = label) }
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is AuthExpiredException) c.session.markExpired(e.message)
                describe(e)
            }
            _ui.update { it.copy(busy = null, message = message) }
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is AuthExpiredException -> e.message ?: "Sign in to BRAIN again"
        is RateLimitException -> "BRAIN is rate limiting requests — try again in a moment"
        is ApiException -> if (e.code == 403) "BRAIN refused: ${e.message}" else e.message ?: "HTTP ${e.code}"
        is IOException -> "Network error: ${e.message ?: "no connection"}"
        else -> e.message ?: e.javaClass.simpleName
    }
}
