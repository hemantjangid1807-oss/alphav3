package com.alphasim.brain.sim

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.alphasim.brain.AlphaSimApp
import com.alphasim.brain.data.model.AlphaStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Foreground service that keeps the process alive while the engine works through the queue. */
class SimulationService : LifecycleService() {

    companion object {
        private const val ACTION_START = "com.alphasim.brain.action.START"
        private const val ACTION_PAUSE = "com.alphasim.brain.action.PAUSE"
        private const val ACTION_STOP = "com.alphasim.brain.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, SimulationService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        private fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
            context, action.hashCode(),
            Intent(context, SimulationService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private val container get() = (application as AlphaSimApp).container
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foreground = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val engine = container.engine
        when (intent?.action) {
            ACTION_PAUSE -> {
                engine.pause()
                if (!foreground) stopSelf()
            }
            ACTION_STOP -> {
                engine.stop()
                if (!foreground) stopSelf()
            }
            else -> {
                if (!goForeground()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                engine.start()
                acquireWakeLock()
                observe()
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground(): Boolean = try {
        val n = Notifications.progress(this, container.engine.state.value, 0, actionIntent(this, ACTION_PAUSE), actionIntent(this, ACTION_STOP))
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, Notifications.ID_RUN, n, type)
        foreground = true
        true
    } catch (e: Exception) {
        false
    }

    private fun observe() {
        if (observer?.isActive == true) return
        val engine = container.engine
        val queuedFlow = container.repo.observeCount(AlphaStatus.QUEUED)
        observer = lifecycleScope.launch {
            combine(engine.state, queuedFlow) { s, q -> s to q }.collect { (s, q) ->
                if (!s.running) {
                    finishRun(s)
                } else if (Notifications.canPost(this@SimulationService)) {
                    val n = Notifications.progress(
                        this@SimulationService, s, q,
                        actionIntent(this@SimulationService, ACTION_PAUSE),
                        actionIntent(this@SimulationService, ACTION_STOP),
                    )
                    getSystemService(NotificationManager::class.java)?.notify(Notifications.ID_RUN, n)
                }
            }
        }
    }

    private fun finishRun(s: SimulationEngine.State) {
        observer?.cancel()
        if (s.completed + s.failed > 0 || s.message != null) {
            val text = buildString {
                append("${s.completed} completed · ${s.failed} failed")
                s.message?.let { append(" — ").append(it) }
            }
            Notifications.done(this, "Simulation run finished", text)
        }
        stopEverything()
    }

    private fun stopEverything() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    /** Android 15+: dataSync services get ~6h per day. Stop cleanly; the queue resumes when the user returns. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        container.engine.stop()
        observer?.cancel()
        Notifications.done(this, "Simulation run paused", "Android's background time limit was reached. Open AlphaSim and tap Resume.")
        stopEverything()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlphaSim:simulation").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
