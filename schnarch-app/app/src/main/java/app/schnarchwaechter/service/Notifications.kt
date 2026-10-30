package app.schnarchwaechter.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.schnarchwaechter.R
import app.schnarchwaechter.data.NightEntity
import app.schnarchwaechter.ui.MainActivity
import app.schnarchwaechter.util.Fmt
import app.schnarchwaechter.util.Permissions

object Notifications {
    private const val CHANNEL_NIGHT = "night"
    private const val CHANNEL_SUMMARY = "summary"
    const val NIGHT_ID = 1
    private const val SUMMARY_ID = 2
    const val EXTRA_NIGHT_ID = "nightId"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NIGHT, "Laufende Messung", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Zeigt an, dass die Nachtmessung läuft (von Android vorgeschrieben)."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Morgenzusammenfassung", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Kurze Zusammenfassung nach dem Beenden der Nacht."
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    private fun openApp(context: Context, nightId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        nightId?.let { intent.putExtra(EXTRA_NIGHT_ID, it) }
        return PendingIntent.getActivity(
            context, (nightId ?: 0L).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** The (required) notification of the foreground service. Posted once, never updated at night. */
    fun night(context: Context): Notification {
        val stop = PendingIntent.getService(
            context, 1, NightService.stopIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_NIGHT)
            .setSmallIcon(R.drawable.ic_stat_night)
            .setContentTitle("Schnarchmessung läuft")
            .setContentText("Es wird nur gemessen. Audio wird nur bei auffälligen Momenten kurz gespeichert.")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context, null))
            .addAction(0, "Nacht beenden", stop)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showSummary(context: Context, night: NightEntity) {
        if (!Permissions.hasNotifications(context)) return
        val duration = (night.endMs ?: night.startMs) - night.startMs
        val text = if (night.episodeCount == 0) {
            "Kein Schnarchen erkannt (${Fmt.duration(duration)} gemessen)."
        } else {
            "${night.episodeCount} Schnarch-Episoden, zusammen ${Fmt.minutes(night.snoreMs)} " +
                "(${Fmt.percent(night.snoreMs.toDouble() / duration.coerceAtLeast(1))} der Nacht)."
        }
        val n = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_stat_night)
            .setContentTitle("Deine Nacht")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, night.id))
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, n)
    }
}
