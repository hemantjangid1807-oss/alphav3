package com.alphasim.brain

import android.app.Application
import android.content.Context
import com.alphasim.brain.data.AlphaRepository
import com.alphasim.brain.data.SessionManager
import com.alphasim.brain.data.api.BrainApi
import com.alphasim.brain.data.api.PersistentCookieJar
import com.alphasim.brain.data.db.AppDatabase
import com.alphasim.brain.data.prefs.AppPrefs
import com.alphasim.brain.data.prefs.CredentialStore
import com.alphasim.brain.sim.Notifications
import com.alphasim.brain.sim.SimulationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AlphaSimApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}

/** Manual dependency container — one instance per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    /** For work that must outlive a screen (DB writes, API calls started from the UI). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db = AppDatabase.build(appContext)
    val prefs = AppPrefs(appContext)
    val credentials = CredentialStore(appContext)
    val cookieJar = PersistentCookieJar(appContext)
    val api = BrainApi(cookieJar)
    val repo = AlphaRepository(db)
    val session = SessionManager(api, credentials, prefs, appScope)
    val engine = SimulationEngine(repo, api, prefs) { message -> session.markExpired(message) }
}

val Context.container: AppContainer get() = (applicationContext as AlphaSimApp).container
