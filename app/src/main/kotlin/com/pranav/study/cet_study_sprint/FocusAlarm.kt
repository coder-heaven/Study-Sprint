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
import android.net.Uri
import android.provider.Settings

/** An alarm survives the UI leaving the foreground and a normal process death. */
internal object FocusAlarm {
    const val CHANNEL = "focus_finished_sound_v2"
    const val NOTIFICATION_ID = 2201
    const val DISMISS_ACTION = "com.pranav.study.cet_study_sprint.DISMISS_TIMER"
    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 2201, Intent(context, FocusAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(context: Context, endAt: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pending(context))
        if (endAt <= System.currentTimeMillis()) return
        // Without exact-alarm access Android may delay the notification in idle mode.
        val open = AlertNavigation.pending(context, "focus", NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms())
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, open), pending(context))
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pending(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Focus timer alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 160, 300)
            })
    }

    fun canOpenFullScreen(context: Context): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun fullScreenSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}"))
        else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun notifyFinished(context: Context, isBreak: Boolean) {
        AlertNavigation.foreground(context, "focus")
        createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = AlertNavigation.pending(context, "focus", NOTIFICATION_ID)
        val stop = PendingIntent.getBroadcast(context, 2204,
            Intent(context, FocusAlarmReceiver::class.java).setAction(DISMISS_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentTitle(if (isBreak) "Break finished" else "Focus timer finished")
            .setContentText(if (isBreak) "Ready to study again?" else "Your study session is complete.")
            .setContentIntent(open).setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .addAction(Notification.Action.Builder(null, "Dismiss", stop).build())
        if (canOpenFullScreen(context)) {
            val fullScreen = PendingIntent.getActivity(context, 2205,
                Intent(context, TimerFinishedActivity::class.java).putExtra("is_break", isBreak)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setFullScreenIntent(fullScreen, true)
        }
        // The alarm repeats until dismissed/opened, with a one-minute unattended limit.
        val notification = builder.build().apply { flags = flags or Notification.FLAG_INSISTENT }
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

}

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == FocusAlarm.DISMISS_ACTION) {
            FocusAlarm.dismiss(context)
            return
        }
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
