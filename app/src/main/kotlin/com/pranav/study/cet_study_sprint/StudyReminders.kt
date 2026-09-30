package com.pranav.study.cet_study_sprint

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioAttributes
import android.media.RingtoneManager
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

internal object StudyReminders {
    const val CHANNEL = "study_reminders_sound_v3"
    const val STUDY = "study"
    const val PLAN = "plan"

    fun enabled(context: Context, kind: String): Boolean =
        prefs(context).getBoolean("reminder_$kind", false)

    fun hour(context: Context, kind: String): Int =
        prefs(context).getInt("reminder_${kind}_hour", if (kind == STUDY) 18 else 20)

    fun minute(context: Context, kind: String): Int =
        prefs(context).getInt("reminder_${kind}_minute", 0)

    fun timeLabel(context: Context, kind: String): String {
        val time = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour(context, kind))
            set(Calendar.MINUTE, minute(context, kind))
        }
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time.timeInMillis))
    }

    fun setTime(context: Context, kind: String, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt("reminder_${kind}_hour", hour.coerceIn(0, 23))
            .putInt("reminder_${kind}_minute", minute.coerceIn(0, 59))
            .apply()
        if (enabled(context, kind)) schedule(context, kind)
    }

    fun setEnabled(context: Context, kind: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("reminder_$kind", enabled).apply()
        schedule(context, kind)
    }

    fun schedule(context: Context, kind: String) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, StudyReminderReceiver::class.java).putExtra("kind", kind)
        val requestCode = if (kind == STUDY) 100 else 101
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(pending)
        if (!enabled(context, kind)) return
        val time = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour(context, kind))
            set(Calendar.MINUTE, minute(context, kind))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.timeInMillis, pending)
    }

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Study reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 130, 250)
            })
    }

    fun notify(context: Context, kind: String) {
        val route = AlertDestination.reminder(kind) ?: return
        AlertNavigation.foreground(context, route)
        createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = if (kind == STUDY) 100 else 101
        val open = AlertNavigation.pending(context, route, id)
        val (title, message) = if (kind == STUDY) {
            "A little focus today?" to "Open Study Sprint and begin one study session."
        } else {
            "Plan your next step" to "Check today's tasks or capture what you learned."
        }
        manager.notify(
            if (kind == STUDY) 100 else 101,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
}

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            StudyReminders.schedule(context, StudyReminders.STUDY)
            StudyReminders.schedule(context, StudyReminders.PLAN)
            return
        }
        val kind = intent.getStringExtra("kind") ?: return
        if (StudyReminders.enabled(context, kind)) {
            StudyReminders.notify(context, kind)
            StudyReminders.schedule(context, kind)
        }
    }
}
