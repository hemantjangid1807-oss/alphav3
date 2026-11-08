package com.alphasim.brain.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    /** Parallel simulations. Most BRAIN accounts allow 3 at a time. */
    val concurrency: Int = 3,
    /** Send up to [multiBatch] alphas per request (needs the multi-simulation permission). */
    val multiSim: Boolean = false,
    val multiBatch: Int = 10,
    /** Retries for network / server errors before an alpha is marked as failed. */
    val maxRetries: Int = 3,
    /** Floor for the polling interval, in seconds. */
    val minPollSec: Double = 2.0,
    /** Fetch the self-correlation automatically after each completed simulation. */
    val autoSelfCorr: Boolean = false,
    val theme: String = "dark",
    val rememberedEmail: String = "",
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class AppPrefs(context: Context) {
    private val store = context.applicationContext.dataStore

    private object K {
        val concurrency = intPreferencesKey("concurrency")
        val multiSim = booleanPreferencesKey("multi_sim")
        val multiBatch = intPreferencesKey("multi_batch")
        val maxRetries = intPreferencesKey("max_retries")
        val minPoll = doublePreferencesKey("min_poll")
        val autoSelfCorr = booleanPreferencesKey("auto_self_corr")
        val theme = stringPreferencesKey("theme")
        val email = stringPreferencesKey("email")
    }

    val settings: Flow<AppSettings> = store.data.map { read(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val next = transform(read(p))
            p[K.concurrency] = next.concurrency.coerceIn(1, 10)
            p[K.multiSim] = next.multiSim
            p[K.multiBatch] = next.multiBatch.coerceIn(2, 10)
            p[K.maxRetries] = next.maxRetries.coerceIn(0, 10)
            p[K.minPoll] = next.minPollSec.coerceIn(1.0, 30.0)
            p[K.autoSelfCorr] = next.autoSelfCorr
            p[K.theme] = next.theme
            p[K.email] = next.rememberedEmail
        }
    }

    private fun read(p: Preferences) = AppSettings(
        concurrency = p[K.concurrency] ?: 3,
        multiSim = p[K.multiSim] ?: false,
        multiBatch = p[K.multiBatch] ?: 10,
        maxRetries = p[K.maxRetries] ?: 3,
        minPollSec = p[K.minPoll] ?: 2.0,
        autoSelfCorr = p[K.autoSelfCorr] ?: false,
        theme = p[K.theme] ?: "dark",
        rememberedEmail = p[K.email] ?: "",
    )
}
