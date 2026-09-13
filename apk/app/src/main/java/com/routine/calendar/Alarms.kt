package com.routine.calendar

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray

/** Sveglie esatte per gli eventi + canale notifiche. */
object EventScheduler {
    const val CHANNEL_ID = "routine_eventi"
    private const val PREFS = "routine_alarms"
    private const val KEY_JSON = "events_json"
    private const val REQ_BASE = 5000

    fun schedule(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_JSON, json).apply()
        cancelAll(ctx, keepStored = true)

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < 31 ||
            (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms())
        var items: JSONArray
        try {
            items = JSONArray(json)
        } catch (e: Exception) {
            return
        }
        val now = System.currentTimeMillis()
        var req = REQ_BASE
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val at = o.optLong("at", 0)
            if (at <= now) continue
            val intent = Intent(ctx, EventAlarmReceiver::class.java).apply {
                putExtra("title", o.optString("title", "Routine"))
                putExtra("body", o.optString("body", ""))
                putExtra("tag", o.optString("tag", "ev-$at"))
            }
            val pi = PendingIntent.getBroadcast(
                ctx, req++, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (canExact) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } catch (e: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    fun rescheduleStored(ctx: Context) {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return
        schedule(ctx, json)
    }

    fun cancelAll(ctx: Context, keepStored: Boolean = false) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancella l'intervallo di requestCode usato da schedule()
        for (req in REQ_BASE until REQ_BASE + 200) {
            val pi = PendingIntent.getBroadcast(
                ctx, req,
                Intent(ctx, EventAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
        if (!keepStored) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_JSON).apply()
        }
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Cambi evento",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Avvisi a ogni cambio evento della routine" }
            )
        }
    }
}

/** Scatta alla data/ora dell'evento e mostra la notifica. */
class EventAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        EventScheduler.ensureChannel(ctx)
        val title = intent.getStringExtra("title") ?: "Routine"
        val body = intent.getStringExtra("body") ?: ""
        val tag = intent.getStringExtra("tag") ?: ("ev-" + System.currentTimeMillis())

        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            ctx, tag.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(ctx, EventScheduler.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(ctx)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .build()
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(tag, tag.hashCode(), notif)
    }
}

/** Dopo il riavvio del telefono riprogramma le sveglie salvate. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            EventScheduler.rescheduleStored(ctx)
        }
    }
}
