package com.pranav.study.cet_study_sprint

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

internal object DailyUsage {
    fun startOfLocalDay(now: Long = System.currentTimeMillis()): Long =
        LocalDay.start(now)

    fun millisUntilNextDay(now: Long = System.currentTimeMillis()): Long =
        LocalDay.next(now) - now

    data class Measurement(val day: Long, val millis: Long, val packageName: String)
    fun measurement(context: Context, pkg: String, now: Long = System.currentTimeMillis()) =
        Measurement(startOfLocalDay(now), usedToday(context, pkg, now), pkg)

    fun usedToday(context: Context, packageName: String, now: Long = System.currentTimeMillis()): Long =
        usedByPackageToday(context, now)[packageName] ?: 0L

    fun usedByPackageToday(context: Context, now: Long = System.currentTimeMillis()): Map<String, Long> {
        val start = startOfLocalDay(now)
        val lookBack = startOfLocalDay(start - 1L)
        val events = context.getSystemService(UsageStatsManager::class.java)
            .queryEvents(lookBack, now) ?: return emptyMap()
        val event = UsageEvents.Event()
        val timeline = UsageTimeline(start, now)
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageTimeline.Kind.RESUME
                UsageEvents.Event.MOVE_TO_BACKGROUND, UsageEvents.Event.ACTIVITY_STOPPED -> UsageTimeline.Kind.PAUSE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageTimeline.Kind.SCREEN_OFF
                UsageEvents.Event.SCREEN_INTERACTIVE -> UsageTimeline.Kind.SCREEN_ON
                UsageEvents.Event.KEYGUARD_SHOWN -> UsageTimeline.Kind.LOCK
                UsageEvents.Event.KEYGUARD_HIDDEN -> UsageTimeline.Kind.UNLOCK
                UsageEvents.Event.DEVICE_SHUTDOWN -> UsageTimeline.Kind.SHUTDOWN
                UsageEvents.Event.DEVICE_STARTUP -> UsageTimeline.Kind.STARTUP
                else -> continue
            }
            timeline.accept(kind, event.timeStamp, event.packageName, event.className)
        }
        return timeline.result()
    }
}
