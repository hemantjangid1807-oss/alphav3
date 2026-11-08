package com.alphasim.brain.data

import com.alphasim.brain.data.api.BrainApi
import com.alphasim.brain.data.api.BrainApi.AuthResult
import com.alphasim.brain.data.prefs.AppPrefs
import com.alphasim.brain.data.prefs.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class SessionManager(
    private val api: BrainApi,
    private val credentials: CredentialStore,
    private val prefs: AppPrefs,
    private val scope: CoroutineScope,
) {
    data class State(
        val checking: Boolean = false,
        val busy: Boolean = false,
        val loggedIn: Boolean = false,
        val userId: String? = null,
        val permissions: List<String> = emptyList(),
        val expiresAt: Long? = null,
        val personaUrl: String? = null,
        val error: String? = null,
        val rememberMe: Boolean = true,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var restored = false

    /** Called once at start-up: reuse the cookie if still valid, otherwise sign in silently with saved credentials. */
    fun restore() {
        if (restored) return
        restored = true
        scope.launch {
            _state.update { it.copy(checking = true) }
            credentials.load()?.let { api.credentials = it }
            val info = if (api.hasSessionCookie()) runCatching { api.authInfo() }.getOrNull() else null
            if (info != null) {
                onSignedIn(info)
            } else {
                val saved = api.credentials
                if (saved != null) handle(safeAuth { api.authenticate(saved.first, saved.second) }, silent = true)
            }
            _state.update { it.copy(checking = false) }
        }
    }

    fun login(email: String, password: String, remember: Boolean) {
        scope.launch {
            _state.update { it.copy(busy = true, error = null, personaUrl = null, rememberMe = remember) }
            prefs.update { it.copy(rememberedEmail = email.trim()) }
            if (remember) credentials.save(email.trim(), password) else credentials.clear()
            handle(safeAuth { api.authenticate(email.trim(), password) }, silent = false)
            _state.update { it.copy(busy = false) }
        }
    }

    fun completePersona() {
        val url = _state.value.personaUrl ?: return
        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            handle(safeAuth { api.completePersona(url) }, silent = false)
            _state.update { it.copy(busy = false) }
        }
    }

    fun refreshInfo() {
        scope.launch {
            val info = runCatching { api.authInfo() }.getOrNull()
            if (info != null) onSignedIn(info) else _state.update { it.copy(loggedIn = false) }
        }
    }

    fun logout() {
        scope.launch {
            api.logout()
            credentials.clear()
            _state.value = State(rememberMe = _state.value.rememberMe)
        }
    }

    /** The engine calls this when it can no longer renew the session on its own. */
    fun markExpired(message: String?) {
        _state.update { it.copy(loggedIn = false, error = message) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun onSignedIn(info: BrainApi.AuthInfo?) {
        _state.update {
            it.copy(
                loggedIn = true,
                userId = info?.userId ?: it.userId,
                permissions = info?.permissions ?: it.permissions,
                expiresAt = info?.expirySec?.let { s -> System.currentTimeMillis() + (s * 1000).toLong() },
                personaUrl = null,
                error = null,
            )
        }
    }

    private fun handle(result: AuthResult, silent: Boolean) {
        when (result) {
            is AuthResult.Success -> onSignedIn(result.info)
            is AuthResult.PersonaRequired -> _state.update { it.copy(loggedIn = false, personaUrl = result.url) }
            is AuthResult.Failure -> _state.update { it.copy(loggedIn = false, error = if (silent) null else result.message) }
        }
    }

    private suspend fun safeAuth(block: suspend () -> AuthResult): AuthResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        AuthResult.Failure("Can't reach BRAIN: ${e.message ?: "network error"}")
    } catch (e: Exception) {
        AuthResult.Failure(e.message ?: e.javaClass.simpleName)
    }
}
