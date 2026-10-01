package com.pranav.study.cet_study_sprint

/** Union of resumed activity intervals per app, clipped to one local calendar day. */
internal class UsageTimeline(private val start: Long, private val now: Long) {
    enum class Kind { RESUME, PAUSE, SCREEN_ON, SCREEN_OFF, UNLOCK, LOCK, SHUTDOWN, STARTUP }
    private val activities = mutableMapOf<String, MutableSet<String>>()
    private val opened = mutableMapOf<String, Long>()
    private val totals = mutableMapOf<String, Long>()
    private var interactive = true
    private var unlocked = true

    private fun close(pkg: String, at: Long) {
        val from = opened.remove(pkg) ?: return
        val duration = (minOf(at, now) - maxOf(from, start)).coerceAtLeast(0L)
        if (duration > 0) totals[pkg] = (totals[pkg] ?: 0L) + duration
    }
    private fun resumeVisible(at: Long) {
        if (interactive && unlocked) activities.filterValues { it.isNotEmpty() }.keys.forEach {
            opened.putIfAbsent(it, at)
        }
    }
    fun accept(kind: Kind, at: Long, pkg: String? = null, activity: String? = null) {
        if (at > now) return
        when (kind) {
            Kind.RESUME -> if (!pkg.isNullOrBlank()) {
                activities.getOrPut(pkg) { mutableSetOf() }.add(activity.orEmpty())
                if (interactive && unlocked) opened.putIfAbsent(pkg, at)
            }
            Kind.PAUSE -> if (!pkg.isNullOrBlank()) {
                val active = activities[pkg] ?: return
                if (activity == null) active.clear() else active.remove(activity)
                if (active.isEmpty()) { close(pkg, at); activities.remove(pkg) }
            }
            Kind.SCREEN_OFF, Kind.LOCK -> {
                opened.keys.toList().forEach { close(it, at) }
                if (kind == Kind.SCREEN_OFF) interactive = false else unlocked = false
            }
            Kind.SCREEN_ON, Kind.UNLOCK -> {
                if (kind == Kind.SCREEN_ON) interactive = true else unlocked = true
                resumeVisible(at)
            }
            Kind.SHUTDOWN, Kind.STARTUP -> {
                opened.keys.toList().forEach { close(it, at) }
                activities.clear()
                interactive = true; unlocked = true
            }
        }
    }
    fun result(): Map<String, Long> {
        opened.keys.toList().forEach { close(it, now) }
        return totals.toMap()
    }
}
