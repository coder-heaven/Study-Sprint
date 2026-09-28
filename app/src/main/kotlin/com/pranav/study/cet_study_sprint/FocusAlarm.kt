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

/** An alarm survives the UI leaving the foreground and a normal process death. */
internal object FocusAlarm {
    private const val CHANNEL = "focus_finished_v1"
    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 2201, Intent(context, FocusAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(context: Context, endAt: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pending(context))
        if (endAt <= System.currentTimeMillis()) return
        // Without exact-alarm access Android may delay the notification in idle mode.
        val open = PendingIntent.getActivity(context, 2202,
            Intent(context, ComposeStudyActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms())
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, open), pending(context))
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pending(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    fun notifyFinished(context: Context, isBreak: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Focus timer finished",
            NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 160, 300)
        })
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, 2203,
            Intent(context, ComposeStudyActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(2201, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (isBreak) "Break finished" else "Focus timer finished")
            .setContentText(if (isBreak) "Ready to study again?" else "Your study session is complete.")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (prefs.getBoolean("focus_active", false) && prefs.getBoolean("focus_running", false)) {
                val end = prefs.getLong("focus_end_at", 0)
                if (end > System.currentTimeMillis()) FocusAlarm.schedule(context, end)
                else fire(context)
            }
            return
        }
        fire(context)
    }

    private fun fire(context: Context) {
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("focus_active", false) || !prefs.getBoolean("focus_running", false)) return
        if (System.currentTimeMillis() < prefs.getLong("focus_end_at", 0)) return
        // Claim this completion once, even if a foreground ViewModel is also ticking.
        val isBreak = prefs.getBoolean("focus_is_break", false)
        if (prefs.getBoolean("focus_alarm_delivered", false)) return
        prefs.edit().putBoolean("focus_alarm_delivered", true).commit()
        FocusAlarm.notifyFinished(context, isBreak)
    }
}
