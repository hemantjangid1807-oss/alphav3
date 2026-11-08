package com.alphasim.brain.sim

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.alphasim.brain.MainActivity
import com.alphasim.brain.R

object Notifications {
    const val CHANNEL_RUN = "simulation_progress"
    const val CHANNEL_DONE = "simulation_done"
    const val ID_RUN = 1001
    const val ID_DONE = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUN, "Simulation progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while alphas are being simulated"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Run finished", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Posted when a simulation run ends"
            }
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(
        context: Context,
        state: SimulationEngine.State,
        queued: Int,
        pause: PendingIntent,
        stop: PendingIntent,
    ): android.app.Notification {
        val finished = state.completed + state.failed
        val total = finished + queued + state.active
        val text = buildString {
            append("${state.active} running · $queued queued · ${state.completed} done")
            if (state.failed > 0) append(" · ${state.failed} failed")
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RUN)
            .setSmallIcon(R.drawable.ic_stat_sim)
            .setContentTitle(if (state.pausing) "Pausing simulations…" else "Simulating alphas on BRAIN")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(text, state.message).joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .setProgress(total.coerceAtLeast(1), finished, total == 0)
        if (!state.pausing) builder.addAction(0, "Pause", pause)
        builder.addAction(0, "Stop", stop)
        return builder.build()
    }

    fun done(context: Context, title: String, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_sim)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(ID_DONE, n)
    }
}
