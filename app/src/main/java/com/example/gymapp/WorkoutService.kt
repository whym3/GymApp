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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the workout alive in the background and shows an
 * ongoing notification with the live elapsed time, exercise/set progress, and
 * Pause/Resume + Finish controls. State lives in [WorkoutTimer] (timing) and
 * [WorkoutProgress] (exercise/set breakdown); this service mirrors both to the
 * notification and forwards notification actions back to [WorkoutTimer].
 *
 * On Android 16+ the notification is built as a Live Update — a [NotificationCompat.ProgressStyle]
 * with one segment per exercise, promoted to a status-bar chip — falling back to a
 * plain ongoing notification on older OS versions.
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
        startInForeground(buildNotification(WorkoutTimer.state.value, WorkoutProgress.state.value))

        if (!observing) {
            observing = true
            scope.launch {
                combine(WorkoutTimer.state, WorkoutProgress.state) { timer, progress -> timer to progress }
                    .collect { (s, progress) ->
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
                                    .notify(NOTIF_ID, buildNotification(s, progress))
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

    private fun buildNotification(s: WorkoutTimer.State, progress: WorkoutProgress.State): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PI_FLAGS,
        )
        val toggle = if (s.running) action("Pause", ACTION_PAUSE) else action("Resume", ACTION_RESUME)
        val finish = action("Finish", ACTION_FINISH)

        val totalSets = progress.totalSets
        val setLabel = when {
            totalSets == 0 -> null
            progress.doneSets >= totalSets -> "All sets complete"
            else -> "Set ${progress.doneSets + 1} of $totalSets"
        }
        val title = progress.currentExerciseName
            ?: if (s.running) "Workout in progress" else "Workout paused"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)   // alert on start, but don't buzz on every tick
            .setContentIntent(openIntent)
            .addAction(toggle)
            .addAction(finish)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Live Update: progress bar segmented per exercise, status-bar chip,
            // and a chronometer so the elapsed time ticks without per-second notify().
            if (totalSets > 0) {
                builder.setStyle(progressStyle(progress, totalSets))
                builder.setShortCriticalText("${progress.doneSets}/$totalSets")
            }
            builder.setContentText(
                if (s.running) (setLabel ?: formatClock(s.elapsedSec))
                else listOfNotNull(setLabel, formatClock(s.elapsedSec)).joinToString(" · ")
            )
            builder.setRequestPromotedOngoing(true)
            builder.setShowWhen(s.running)
            builder.setUsesChronometer(s.running)
            if (s.running) builder.setWhen(System.currentTimeMillis() - s.elapsedSec * 1000L)
        } else {
            builder.setContentText(listOfNotNull(setLabel, formatClock(s.elapsedSec)).joinToString(" · "))
        }

        return builder.build()
    }

    /** One [NotificationCompat.ProgressStyle.Segment] per exercise (sized by its set count), with
     *  boundary [NotificationCompat.ProgressStyle.Point]s marking where each exercise starts. */
    private fun progressStyle(progress: WorkoutProgress.State, totalSets: Int): NotificationCompat.ProgressStyle {
        val style = NotificationCompat.ProgressStyle()
            .setProgress(progress.doneSets.coerceAtMost(totalSets))
        progress.exercises.forEachIndexed { i, ex ->
            style.addProgressSegment(
                NotificationCompat.ProgressStyle.Segment(ex.totalSets.coerceAtLeast(1)).setId(i)
            )
        }
        var cumulative = 0
        progress.exercises.dropLast(1).forEach { ex ->
            cumulative += ex.totalSets
            style.addProgressPoint(NotificationCompat.ProgressStyle.Point(cumulative))
        }
        return style
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
