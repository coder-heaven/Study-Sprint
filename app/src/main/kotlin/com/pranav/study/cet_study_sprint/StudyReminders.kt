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
import java.util.Calendar

internal object StudyReminders {
    private const val CHANNEL = "study_reminders"
    const val STUDY = "study"
    const val PLAN = "plan"
    fun enabled(context: Context, kind: String): Boolean =
        context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE).getBoolean("reminder_$kind", false)
    fun setEnabled(context: Context, kind: String, enabled: Boolean) {
        context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE).edit()
            .putBoolean("reminder_$kind", enabled).apply()
        schedule(context, kind)
    }
    fun schedule(context: Context, kind: String) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, StudyReminderReceiver::class.java).putExtra("kind", kind)
        val requestCode = if (kind == STUDY) 100 else 101
        val pending = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pending)
        if (!enabled(context, kind)) return
        val time = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, if (kind == STUDY) 18 else 20)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.timeInMillis, pending)
    }
    fun notify(context: Context, kind: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Study reminders",
            NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 1000,
            Intent(context, ComposeStudyActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val (title, message) = if (kind == STUDY)
            "A little focus today?" to "Open Study Sprint and begin one study session."
        else "Plan your next step" to "Check today's tasks or capture what you learned."
        manager.notify(if (kind == STUDY) 100 else 101,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(message)
                .setContentIntent(open).setAutoCancel(true).build())
    }
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
