package com.alphasim.brain.sim

import com.alphasim.brain.data.AlphaRepository
import com.alphasim.brain.data.api.ApiException
import com.alphasim.brain.data.api.AuthExpiredException
import com.alphasim.brain.data.api.BrainApi
import com.alphasim.brain.data.api.RateLimitException
import com.alphasim.brain.data.db.AlphaEntity
import com.alphasim.brain.data.model.SimSettings
import com.alphasim.brain.data.model.arr
import com.alphasim.brain.data.model.str
import com.alphasim.brain.data.prefs.AppPrefs
import com.alphasim.brain.data.prefs.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Runs the persistent simulation queue stored in Room.
 *
 * - Up to `concurrency` simulations are in flight at once (BRAIN's per-user limit is usually 3).
 * - In multi-simulation mode each request carries up to 10 alphas from the same list.
 * - The progress URL is saved as soon as BRAIN accepts a simulation, so if the app is killed the
 *   next run resumes polling instead of simulating the alpha again.
 * - 429s (rate limit / concurrent limit) wait and retry without using up the retry budget.
 */
class SimulationEngine(
    private val repo: AlphaRepository,
    private val api: BrainApi,
    private val prefs: AppPrefs,
    private val onAuthLost: (String?) -> Unit,
) {
    data class State(
        val running: Boolean = false,
        val pausing: Boolean = false,
        val active: Int = 0,
        val completed: Int = 0,
        val failed: Int = 0,
        val message: String? = null,
        val startedAt: Long? = null,
        val lastRun: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var pauseRequested = false
    private val active = AtomicInteger(0)

    val isRunning: Boolean get() = job?.isActive == true

    @Synchronized
    fun start() {
        pauseRequested = false
        if (job?.isActive == true) {
            _state.update { it.copy(pausing = false, message = null) }
            return
        }
        _state.value = State(running = true, startedAt = System.currentTimeMillis(), lastRun = _state.value.lastRun)
        job = scope.launch {
            try {
                runLoop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note("Engine stopped: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                withContext(NonCancellable) { repo.parkRunning() }
                active.set(0)
                _state.update { s ->
                    s.copy(
                        running = false, pausing = false, active = 0,
                        lastRun = "${s.completed} completed · ${s.failed} failed",
                    )
                }
            }
        }
    }

    /** Stop launching new simulations; the ones already on BRAIN are tracked to completion. */
    fun pause() {
        if (job?.isActive != true) return
        pauseRequested = true
        _state.update { it.copy(pausing = true, message = "Finishing simulations already on BRAIN…") }
    }

    /** Stop immediately. In-flight simulations go back to the queue and resume polling next time. */
    fun stop() {
        job?.cancel()
    }

    private suspend fun runLoop() = coroutineScope {
        val cfg = prefs.settings.first()
        repo.parkRunning() // anything left RUNNING by a killed process
        val slots = Semaphore(cfg.concurrency.coerceIn(1, 10))
        val batchSize = if (cfg.multiSim) cfg.multiBatch.coerceIn(2, 10) else 1

        while (isActive && !pauseRequested) {
            slots.acquire()
            if (pauseRequested) {
                slots.release()
                break
            }
            val batch = repo.claimNext(batchSize)
            if (batch.isEmpty()) {
                slots.release()
                if (active.get() == 0) break // nothing queued and nothing in flight
                delay(2_000)
                continue
            }
            publishActive(active.incrementAndGet())
            launch {
                try {
                    if (batch.size == 1 && batch[0].multiIndex == null) runSingle(batch[0], cfg) else runMulti(batch, cfg)
                } finally {
                    publishActive(active.decrementAndGet())
                    slots.release()
                }
            }
        }
    }

    /* ------------------------------------------------------------ single */

    private suspend fun runSingle(alpha: AlphaEntity, cfg: AppSettings) {
        var url = alpha.simulationUrl
        var attempts = 0
        var networkAttempts = 0
        val settings = repo.effectiveSettings(alpha)
        while (true) {
            try {
                val current = url ?: api.startSimulation(SimSettings.simulationPayload(alpha.expression, settings)).also {
                    url = it
                    repo.setSimulationUrl(alpha.id, it, null)
                }
                val sim = api.poll(current, cfg.minPollSec) { p -> repo.setProgress(listOf(alpha.id), p) }
                finish(alpha.id, sim, cfg)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: RateLimitException) {
                waitRateLimit(e)
            } catch (e: AuthExpiredException) {
                authLost(e.message)
                return
            } catch (e: ApiException) {
                if (url == null && e.code in 400..499) {
                    fail(alpha.id, e.message ?: "Rejected by BRAIN (HTTP ${e.code})")
                    return
                }
                if (e.code == 404 && url != null) { // simulation expired on the server: submit again
                    url = null
                    repo.setSimulationUrl(alpha.id, null, null)
                }
                if (++attempts > cfg.maxRetries) {
                    fail(alpha.id, e.message ?: "HTTP ${e.code}")
                    return
                }
                note("BRAIN error ${e.code}, retrying…")
                backoff(attempts)
            } catch (e: IOException) {
                if (++networkAttempts > NETWORK_RETRIES) {
                    fail(alpha.id, "Network error: ${e.message ?: "connection failed"}")
                    return
                }
                note("Network problem — retrying (${e.message ?: "no connection"})")
                backoff(networkAttempts)
            } catch (e: Exception) {
                fail(alpha.id, e.message ?: e.javaClass.simpleName)
                return
            }
        }
    }

    /* ------------------------------------------------------------- multi */

    private suspend fun runMulti(batch: List<AlphaEntity>, cfg: AppSettings) {
        val rows = batch.sortedBy { it.multiIndex ?: Int.MAX_VALUE }
        val ids = rows.map { it.id }
        val done = mutableSetOf<Long>()
        var url = rows.first().simulationUrl
        var attempts = 0
        var networkAttempts = 0
        while (true) {
            try {
                val current = url ?: run {
                    val payload = JsonArray(rows.map { SimSettings.simulationPayload(it.expression, repo.effectiveSettings(it)) })
                    api.startSimulation(payload).also { u ->
                        url = u
                        rows.forEachIndexed { i, a -> repo.setSimulationUrl(a.id, u, i) }
                    }
                }
                val parent = api.poll(current, cfg.minPollSec) { p -> repo.setProgress(ids - done, p) }
                val children = parent.arr("children")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                if (children.isEmpty()) {
                    val msg = parent.str("message") ?: "Multi-simulation ended with status ${parent.str("status") ?: "UNKNOWN"}"
                    rows.filter { it.id !in done }.forEach { fail(it.id, msg) }
                    return
                }
                rows.forEachIndexed { i, a ->
                    if (a.id in done) return@forEachIndexed
                    val childId = children.getOrNull(a.multiIndex ?: i)
                    if (childId == null) {
                        fail(a.id, "BRAIN returned no result for this alpha")
                    } else {
                        val child = api.poll(api.simulationUrl(childId), cfg.minPollSec)
                        finish(a.id, child, cfg)
                    }
                    done += a.id
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: RateLimitException) {
                waitRateLimit(e)
            } catch (e: AuthExpiredException) {
                authLost(e.message)
                return
            } catch (e: ApiException) {
                if (url == null && e.code in 400..499) {
                    val msg = e.message ?: "Rejected by BRAIN (HTTP ${e.code})"
                    rows.forEach { fail(it.id, "Multi-simulation rejected: $msg") }
                    return
                }
                if (++attempts > cfg.maxRetries) {
                    rows.filter { it.id !in done }.forEach { fail(it.id, e.message ?: "HTTP ${e.code}") }
                    return
                }
                note("BRAIN error ${e.code}, retrying…")
                backoff(attempts)
            } catch (e: IOException) {
                if (++networkAttempts > NETWORK_RETRIES) {
                    rows.filter { it.id !in done }.forEach { fail(it.id, "Network error: ${e.message ?: "connection failed"}") }
                    return
                }
                note("Network problem — retrying")
                backoff(networkAttempts)
            } catch (e: Exception) {
                rows.filter { it.id !in done }.forEach { fail(it.id, e.message ?: e.javaClass.simpleName) }
                return
            }
        }
    }

    /* ----------------------------------------------------------- results */

    private suspend fun finish(id: Long, sim: JsonObject, cfg: AppSettings) {
        val status = sim.str("status")?.uppercase() ?: "UNKNOWN"
        val alphaId = sim.str("alpha")
        if (alphaId.isNullOrBlank() || status in FAILED_STATES) {
            fail(id, sim.str("message") ?: "Simulation ended with status $status")
            return
        }
        val warning = if (status == "WARNING") sim.str("message") ?: "Completed with warnings" else null
        try {
            repo.markComplete(id, api.getAlpha(alphaId), warning)
            if (cfg.autoSelfCorr) {
                runCatching { api.correlation(alphaId, "self").first }.getOrNull()?.let { repo.setCorrelation(id, "self", it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthExpiredException) {
            repo.markCompleteWithoutStats(id, alphaId, "Simulated — sign in and tap Refresh to load the stats")
            authLost(e.message)
        } catch (e: Exception) {
            repo.markCompleteWithoutStats(id, alphaId, "Simulated, but the stats didn't load (${e.message}). Tap Refresh.")
        }
        _state.update { it.copy(completed = it.completed + 1, message = if (it.pausing) it.message else null) }
    }

    private suspend fun fail(id: Long, message: String) {
        repo.markError(id, message)
        _state.update { it.copy(failed = it.failed + 1) }
    }

    /* ----------------------------------------------------------- helpers */

    private fun authLost(message: String?) {
        pauseRequested = true
        note(message ?: "BRAIN session expired — sign in again to continue")
        onAuthLost(message)
    }

    private suspend fun waitRateLimit(e: RateLimitException) {
        val seconds = if (e.concurrentLimit) maxOf(e.retryAfterSec, 10.0) else maxOf(e.retryAfterSec, 2.0)
        note(
            if (e.concurrentLimit) "BRAIN concurrent-simulation limit reached — waiting for a free slot"
            else "Rate limited by BRAIN — waiting ${seconds.toInt()}s"
        )
        delay((seconds * 1000).toLong())
    }

    private suspend fun backoff(attempt: Int) {
        delay(min(60_000L, 5_000L shl (attempt - 1).coerceIn(0, 4)))
    }

    private fun note(message: String?) = _state.update { it.copy(message = message) }
    private fun publishActive(n: Int) = _state.update { it.copy(active = n.coerceAtLeast(0)) }

    private companion object {
        const val NETWORK_RETRIES = 10
        val FAILED_STATES = setOf("ERROR", "FAIL", "FAILED", "CANCELLED", "CANCELED", "TIMEOUT")
    }
}
