package com.alphasim.brain.data.api

import com.alphasim.brain.data.model.AlphaResult
import com.alphasim.brain.data.model.CheckResult
import com.alphasim.brain.data.model.Recordset
import com.alphasim.brain.data.model.arr
import com.alphasim.brain.data.model.bool
import com.alphasim.brain.data.model.dbl
import com.alphasim.brain.data.model.int
import com.alphasim.brain.data.model.obj
import com.alphasim.brain.data.model.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

class ApiException(val code: Int, message: String, val body: String? = null) : Exception(message)
class AuthExpiredException(message: String) : Exception(message)
class RateLimitException(val retryAfterSec: Double, val concurrentLimit: Boolean, message: String) : Exception(message)

/**
 * Minimal client for the WorldQuant BRAIN REST API (https://api.worldquantbrain.com).
 *
 * Flow: POST /authentication (basic auth, sets a session cookie) → POST /simulations → poll the
 * Location URL while the server sends Retry-After → GET /alphas/{id} for the in-sample stats.
 */
class BrainApi(private val cookieJar: PersistentCookieJar) {

    companion object {
        const val BASE = "https://api.worldquantbrain.com"
        const val WEB = "https://platform.worldquantbrain.com"
        fun alphaWebUrl(alphaId: String) = "$WEB/alpha/$alphaId"
        private val JSON_TYPE = "application/json".toMediaType()
        private val EMPTY = ByteArray(0).toRequestBody(null)
        private const val MAX_POLL_MS = 3L * 60 * 60 * 1000
    }

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "AlphaSim-Android/1.0").build())
        }
        .build()

    /** Kept in memory so expired sessions can be renewed without bothering the user. */
    @Volatile var credentials: Pair<String, String>? = null

    private val authMutex = Mutex()
    @Volatile private var authGeneration = 0

    /* ------------------------------------------------------------------ auth */

    data class AuthInfo(val userId: String?, val expirySec: Double?, val permissions: List<String>)

    sealed interface AuthResult {
        data class Success(val info: AuthInfo?) : AuthResult
        data class PersonaRequired(val url: String) : AuthResult
        data class Failure(val message: String) : AuthResult
    }

    suspend fun authenticate(email: String, password: String): AuthResult {
        val request = Request.Builder()
            .url("$BASE/authentication")
            .header("Authorization", Credentials.basic(email, password))
            .post(EMPTY)
            .build()
        val raw = client.newCall(request).await().use { it.toRaw() }
        return when {
            raw.code in 200..299 -> {
                credentials = email to password
                authGeneration++
                AuthResult.Success(parseAuthInfo(raw.body) ?: authInfo())
            }
            raw.code == 401 && raw.wwwAuthenticate?.contains("persona", ignoreCase = true) == true -> {
                credentials = email to password
                val location = raw.location
                    ?: return AuthResult.Failure("BRAIN asked for biometric verification but sent no link.")
                AuthResult.PersonaRequired(raw.resolve(location))
            }
            raw.code == 401 -> AuthResult.Failure(detail(raw.body) ?: "Incorrect email or password.")
            raw.code == 429 -> AuthResult.Failure("Too many sign-in attempts. Wait a minute and try again.")
            else -> AuthResult.Failure("Sign-in failed (HTTP ${raw.code}). ${detail(raw.body).orEmpty()}".trim())
        }
    }

    /** After the user finishes the biometric check in a browser, confirm it with BRAIN. */
    suspend fun completePersona(url: String): AuthResult {
        val builder = Request.Builder().url(url).post(EMPTY)
        credentials?.let { builder.header("Authorization", Credentials.basic(it.first, it.second)) }
        val raw = client.newCall(builder.build()).await().use { it.toRaw() }
        return when {
            raw.code in 200..299 -> {
                authGeneration++
                AuthResult.Success(parseAuthInfo(raw.body) ?: authInfo())
            }
            raw.code == 401 -> AuthResult.Failure("Verification isn't finished yet. Complete it in the browser, then tap again.")
            else -> AuthResult.Failure("Verification failed (HTTP ${raw.code}). ${detail(raw.body).orEmpty()}".trim())
        }
    }

    /** GET /authentication — null when there is no valid session. */
    suspend fun authInfo(): AuthInfo? {
        val raw = client.newCall(Request.Builder().url("$BASE/authentication").get().build()).await().use { it.toRaw() }
        return if (raw.code in 200..299) parseAuthInfo(raw.body) else null
    }

    suspend fun logout() {
        runCatching {
            client.newCall(Request.Builder().url("$BASE/authentication").delete().build()).await().close()
        }
        credentials = null
        cookieJar.clear()
    }

    fun hasSessionCookie() = cookieJar.hasSession()

    private fun parseAuthInfo(body: String): AuthInfo? {
        val obj = parseObject(body) ?: return null
        val perms = obj.arr("permissions")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return AuthInfo(obj.obj("user")?.str("id"), obj.obj("token")?.dbl("expiry"), perms)
    }

    /* ------------------------------------------------------------ simulation */

    /** POST /simulations with one simulation object or an array (multi-simulation). Returns the progress URL. */
    suspend fun startSimulation(payload: JsonElement): String {
        val raw = send(Request.Builder().url("$BASE/simulations").post(payload.toString().toRequestBody(JSON_TYPE)))
        raw.ensureOk()
        val location = raw.location ?: throw ApiException(raw.code, "BRAIN accepted the simulation but returned no progress URL", raw.body)
        return raw.resolve(location)
    }

    fun simulationUrl(id: String) = "$BASE/simulations/$id"

    /**
     * Polls [url] while the server answers with Retry-After, then returns the final JSON.
     * [onProgress] receives the 0..1 progress value when BRAIN reports one.
     */
    suspend fun poll(
        url: String,
        minDelaySec: Double = 1.0,
        onProgress: suspend (Double?) -> Unit = {},
    ): JsonObject {
        val started = System.currentTimeMillis()
        while (true) {
            val raw = send(Request.Builder().url(url).get())
            if (raw.code == 429) {
                delay(((raw.retryAfter ?: 10.0) * 1000).toLong())
                continue
            }
            raw.ensureOk()
            val wait = raw.retryAfter ?: 0.0
            if (wait <= 0.0) return parseObject(raw.body) ?: JsonObject(emptyMap())
            onProgress(parseObject(raw.body)?.dbl("progress"))
            if (System.currentTimeMillis() - started > MAX_POLL_MS) throw ApiException(408, "Gave up waiting for BRAIN after 3 hours")
            delay((max(wait, minDelaySec) * 1000).toLong())
        }
    }

    /* ----------------------------------------------------------------- alphas */

    suspend fun getAlphaJson(alphaId: String): JsonObject {
        val raw = send(Request.Builder().url("$BASE/alphas/$alphaId").get())
        raw.ensureOk()
        return parseObject(raw.body) ?: throw ApiException(raw.code, "Unreadable alpha response", raw.body)
    }

    suspend fun getAlpha(alphaId: String): AlphaResult = parseAlpha(getAlphaJson(alphaId))

    /** Raw recordset JSON (pnl, daily-pnl, yearly-stats, turnover, sharpe…), polled until ready. */
    suspend fun recordsetJson(alphaId: String, name: String): JsonObject =
        poll("$BASE/alphas/$alphaId/recordsets/$name", 1.0)

    suspend fun recordset(alphaId: String, name: String): Recordset = Recordset.parse(recordsetJson(alphaId, name))

    /** kind = "self" or "prod". Returns the max correlation and the raw table. */
    suspend fun correlation(alphaId: String, kind: String): Pair<Double?, Recordset> {
        val obj = poll("$BASE/alphas/$alphaId/correlations/$kind", 1.0)
        val rs = Recordset.parse(obj)
        val max = obj.dbl("max") ?: run {
            val c = rs.col("correlation")
            if (c < 0) null else rs.rows.indices.mapNotNull { rs.doubleAt(it, c) }.maxOrNull()
        }
        return max to rs
    }

    /** GET /alphas/{id}/check — runs the full submission checks (including self-correlation). */
    suspend fun check(alphaId: String): List<CheckResult> {
        val obj = poll("$BASE/alphas/$alphaId/check", 1.0)
        return parseChecks(obj.obj("is") ?: obj)
    }

    sealed interface SubmitResult {
        data object Submitted : SubmitResult
        data class Rejected(val message: String, val checks: List<CheckResult>) : SubmitResult
    }

    suspend fun submit(alphaId: String): SubmitResult {
        val url = "$BASE/alphas/$alphaId/submit"
        var raw = send(Request.Builder().url(url).post(EMPTY))
        val started = System.currentTimeMillis()
        while (true) {
            if (raw.code == 429) {
                delay(((raw.retryAfter ?: 10.0) * 1000).toLong())
                raw = send(Request.Builder().url(url).get())
                continue
            }
            val wait = raw.retryAfter ?: 0.0
            if (wait <= 0.0 || System.currentTimeMillis() - started > MAX_POLL_MS) break
            delay((max(wait, 1.0) * 1000).toLong())
            raw = send(Request.Builder().url(url).get())
        }
        val obj = parseObject(raw.body)
        val checks = obj?.let { parseChecks(it.obj("is") ?: it) }.orEmpty()
        return when {
            raw.code == 401 -> throw AuthExpiredException("Session expired — sign in again")
            raw.code in 200..299 && checks.none { it.failed } -> SubmitResult.Submitted
            raw.code in 200..299 || raw.code == 403 || raw.code == 400 -> SubmitResult.Rejected(
                detail(raw.body) ?: if (checks.any { it.failed }) "Submission checks failed" else "Submission rejected",
                checks,
            )
            else -> throw ApiException(raw.code, detail(raw.body) ?: "Submit failed (HTTP ${raw.code})", raw.body)
        }
    }

    /** PATCH /alphas/{id} — name, tags and favourite flag. */
    suspend fun updateAlphaProperties(alphaId: String, name: String?, tags: List<String>?, favorite: Boolean?) {
        val body = buildJsonObject {
            if (name != null) put("name", name)
            if (favorite != null) put("favorite", favorite)
            if (tags != null) putJsonArray("tags") { tags.forEach { add(JsonPrimitive(it)) } }
        }
        val raw = send(Request.Builder().url("$BASE/alphas/$alphaId").patch(body.toString().toRequestBody(JSON_TYPE)))
        raw.ensureOk()
    }

    /* ---------------------------------------------------------------- parsing */

    fun parseAlpha(obj: JsonObject): AlphaResult {
        val inSample = obj.obj("is") ?: JsonObject(emptyMap())
        val tags = obj.arr("tags")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return AlphaResult(
            id = obj.str("id").orEmpty(),
            sharpe = inSample.dbl("sharpe"),
            fitness = inSample.dbl("fitness"),
            turnover = inSample.dbl("turnover"),
            returns = inSample.dbl("returns"),
            drawdown = inSample.dbl("drawdown"),
            margin = inSample.dbl("margin"),
            pnl = inSample.dbl("pnl"),
            bookSize = inSample.dbl("bookSize"),
            longCount = inSample.int("longCount"),
            shortCount = inSample.int("shortCount"),
            grade = obj.str("grade"),
            stage = obj.str("stage"),
            status = obj.str("status"),
            name = obj.str("name"),
            favorite = obj.bool("favorite") ?: false,
            tags = tags,
            checks = parseChecks(inSample),
        )
    }

    private fun parseChecks(container: JsonObject): List<CheckResult> =
        container.arr("checks")?.mapNotNull { el ->
            val c = el as? JsonObject ?: return@mapNotNull null
            CheckResult(
                name = c.str("name") ?: return@mapNotNull null,
                result = c.str("result") ?: "UNKNOWN",
                limit = c.dbl("limit"),
                value = c.dbl("value"),
                message = c.str("message"),
            )
        }.orEmpty()

    fun parseObject(body: String): JsonObject? =
        if (body.isBlank()) null else runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    /** Pulls the most useful human readable message out of an error body. */
    fun detail(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return body.take(300)
        val messages = mutableListOf<String>()
        fun walk(e: JsonElement, path: String) {
            when (e) {
                is JsonPrimitive -> e.contentOrNull?.let { messages += if (path.isEmpty() || path == "detail" || path == "message") it else "$path: $it" }
                is JsonArray -> e.forEach { walk(it, path) }
                is JsonObject -> e.forEach { (k, v) -> walk(v, if (path.isEmpty()) k else "$path.$k") }
            }
        }
        if (el is JsonObject) {
            (el["detail"] ?: el["message"] ?: el["error"])?.let { walk(it, "detail") }
            if (messages.isEmpty()) walk(el, "")
        } else walk(el, "")
        return messages.joinToString("; ").take(400).ifBlank { null }
    }

    /* --------------------------------------------------------------- plumbing */

    private data class Raw(
        val code: Int,
        val body: String,
        val retryAfter: Double?,
        val location: String?,
        val wwwAuthenticate: String?,
        val requestUrl: okhttp3.HttpUrl,
    ) {
        fun resolve(location: String): String = requestUrl.resolve(location)?.toString() ?: location
    }

    private fun Response.toRaw() = Raw(
        code = code,
        body = body?.string().orEmpty(),
        retryAfter = header("Retry-After")?.trim()?.toDoubleOrNull(),
        location = header("Location"),
        wwwAuthenticate = header("WWW-Authenticate"),
        requestUrl = request.url,
    )

    private fun Raw.ensureOk() {
        when {
            code in 200..299 -> Unit
            code == 401 -> throw AuthExpiredException("BRAIN session expired — sign in again")
            code == 429 -> throw RateLimitException(
                retryAfter ?: 15.0,
                body.contains("CONCURRENT", ignoreCase = true),
                detail(body) ?: "Rate limited by BRAIN",
            )
            else -> throw ApiException(code, detail(body) ?: "HTTP $code", body)
        }
    }

    /** Executes a request; on 401 it re-authenticates once with the stored credentials and retries. */
    private suspend fun send(builder: Request.Builder): Raw {
        val generation = authGeneration
        val first = client.newCall(builder.build()).await().use { it.toRaw() }
        if (first.code != 401) return first
        reauthenticate(generation)
        return client.newCall(builder.build()).await().use { it.toRaw() }
    }

    private suspend fun reauthenticate(seenGeneration: Int) {
        authMutex.withLock {
            if (authGeneration != seenGeneration) return // someone else already refreshed the session
            val creds = credentials ?: throw AuthExpiredException("Not signed in to BRAIN")
            when (val r = authenticate(creds.first, creds.second)) {
                is AuthResult.Success -> Unit
                is AuthResult.PersonaRequired -> throw AuthExpiredException("BRAIN needs biometric verification — sign in again")
                is AuthResult.Failure -> throw AuthExpiredException(r.message)
            }
        }
    }
}

/** Suspends on an OkHttp call; cancelling the coroutine cancels the request. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
