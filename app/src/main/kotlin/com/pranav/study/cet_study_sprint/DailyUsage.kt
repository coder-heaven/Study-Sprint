package com.pranav.study.cet_study_sprint

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

internal object DailyUsage {
    fun startOfLocalDay(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun millisUntilNextDay(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 1)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis - now

    fun usedToday(context: Context, packageName: String, now: Long = System.currentTimeMillis()): Long =
        usedByPackageToday(context, now)[packageName] ?: 0L

    fun usedByPackageToday(context: Context, now: Long = System.currentTimeMillis()): Map<String, Long> {
        val start = startOfLocalDay(now)
        val lookBack = start - 24L * 60L * 60L * 1000L
        val events = context.getSystemService(UsageStatsManager::class.java)
            .queryEvents(lookBack, now) ?: return emptyMap()
        val event = UsageEvents.Event()
        val activeSince = mutableMapOf<String, Long>()
        val totals = mutableMapOf<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (activeSince[pkg] == null) activeSince[pkg] = event.timeStamp.coerceAtLeast(start)
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val opened = activeSince.remove(pkg) ?: continue
                    val end = event.timeStamp.coerceAtMost(now)
                    if (end > start) totals[pkg] = (totals[pkg] ?: 0L) +
                        (end - opened.coerceAtLeast(start)).coerceAtLeast(0L)
                }
            }
        }
        activeSince.forEach { (pkg, opened) ->
            totals[pkg] = (totals[pkg] ?: 0L) + (now - opened.coerceAtLeast(start)).coerceAtLeast(0L)
        }
        return totals
    }
}