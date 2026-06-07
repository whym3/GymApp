package com.example.gymapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the workout alive in the background and shows an
 * ongoing notification with the live elapsed time plus Pause/Resume and Finish
 * controls. State lives in [WorkoutTimer]; this service mirrors it to the
 * notification and forwards the notification actions back to it.
 */
class WorkoutService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> WorkoutTimer.pause()
            ACTION_RESUME -> WorkoutTimer.resume()
            ACTION_FINISH -> WorkoutTimer.requestFinish()
        }

        ensureChannel()
        startInForeground(buildNotification(WorkoutTimer.state.value))

        if (!observing) {
            observing = true
            scope.launch {
                WorkoutTimer.state.collect { s ->
                    if (!s.active) {
                        // Workout ended — tear the ongoing notification down for good.
                        stopForegroundCompat()
                        runCatching {
                            NotificationManagerCompat.from(this@WorkoutService).cancel(NOTIF_ID)
                        }
                        stopSelf()
                    } else {
                        runCatching {
                            NotificationManagerCompat.from(this@WorkoutService)
                                .notify(NOTIF_ID, buildNotification(s))
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        observing = false
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active workout",
                NotificationManager.IMPORTANCE_DEFAULT,   // audible alert when the workout starts
            ).apply {
                description = "Shows your running workout timer"
                setShowBadge(true)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(s: WorkoutTimer.State): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PI_FLAGS,
        )
        val toggle = if (s.running) action("Pause", ACTION_PAUSE) else action("Resume", ACTION_RESUME)
        val finish = action("Finish", ACTION_FINISH)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(if (s.running) "Workout in progress" else "Workout paused")
            .setContentText(formatClock(s.elapsedSec))
            .setOngoing(true)
            .setOnlyAlertOnce(true)   // alert on start, but don't buzz on every tick
            .setContentIntent(openIntent)
            .addAction(toggle)
            .addAction(finish)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun action(title: String, act: String): NotificationCompat.Action {
        val pi = PendingIntent.getService(
            this, act.hashCode(),
            Intent(this, WorkoutService::class.java).setAction(act),
            PI_FLAGS,
        )
        return NotificationCompat.Action(0, title, pi)
    }

    companion object {
        const val CHANNEL_ID = "workout_timer_v2"
        const val NOTIF_ID = 1001
        const val ACTION_PAUSE = "com.example.gymapp.action.PAUSE"
        const val ACTION_RESUME = "com.example.gymapp.action.RESUME"
        const val ACTION_FINISH = "com.example.gymapp.action.FINISH"

        private val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }
}
