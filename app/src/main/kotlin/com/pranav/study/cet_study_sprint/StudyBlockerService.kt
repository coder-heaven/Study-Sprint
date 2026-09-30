package com.pranav.study.cet_study_sprint

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import java.util.Calendar

class StudyBlockerService : AccessibilityService() {
    companion object { const val PREFS = "study_blocker" }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checker: Job? = null
    private var foregroundPackage: String? = null
    private var protected = emptySet<String>()
    private var keyboards = emptySet<String>()
    private var lastPackage = ""
    private var lastBlockAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        protected = Protection.packages(this)
        keyboards = (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .inputMethodList.map { it.packageName }.toSet()
        StrictLimits.applyPending(this)
        scope.launch {
            while (isActive) { requestCheck(); delay(1_000L) }
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Keyboard popups are not app switches; keep monitoring the underlying app.
        if (pkg in keyboards) return
        foregroundPackage = pkg
        requestCheck()
    }
    private fun requestCheck() {
        if (checker?.isActive == true) return
        val pkg = foregroundPackage ?: return
        if (pkg in protected) return
        checker = scope.launch {
            StrictLimits.applyPending(this@StudyBlockerService)
            val p = StrictLimits.prefs(this@StudyBlockerService)
            val limit = p.getInt("limit_$pkg", 0)
            val weekday = StudyTimeMath.weekdayIndex(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
            val dailyApplies = pkg != YouTubeQuota.PACKAGE && limit > 0 && p.getInt("limit_days_$pkg", 127) and (1 shl weekday) != 0
            val youtube = pkg == YouTubeQuota.PACKAGE
            val used = if (dailyApplies || youtube) withContext(Dispatchers.IO) {
                DailyUsage.usedToday(this@StudyBlockerService, pkg)
            } else 0L
            if (foregroundPackage != pkg) return@launch
            val now = System.currentTimeMillis()
            val focus = StrictLimits.focusBlocked(p, pkg, now)
            val reason = when {
                focus -> "focus"
                youtube && !StrictLimits.usageAllowed(this@StudyBlockerService) -> "permission"
                youtube && used >= 600_000L -> "youtube_daily"
                dailyApplies && used >= limit * 60_000L -> "daily"
                youtube && StrictLimits.remainingYouTube(this@StudyBlockerService, now) == 0L -> "youtube_session"
                else -> return@launch
            }
            val elapsed = SystemClock.elapsedRealtime()
            if (pkg == lastPackage && elapsed - lastBlockAt < 750L) return@launch
            lastPackage = pkg; lastBlockAt = elapsed
            val history = StudyData.events(this@StudyBlockerService)
            withContext(Dispatchers.IO) {
                if (reason == "daily" || reason == "youtube_daily") {
                    val day = DailyUsage.startOfLocalDay(now).toString()
                    if (p.getString("last_reached_$pkg", null) != day) {
                        p.edit().putString("last_reached_$pkg", day).apply()
                        history.recordLimitEvent("limit_reached", pkg)
                    }
                }
                history.recordLimitEvent("blocked", pkg)
            }
            if (foregroundPackage != pkg) return@launch
            // Close the distracting app first, so Back/recents cannot dismiss the block into it.
            performGlobalAction(GLOBAL_ACTION_HOME)
            runCatching {
                startActivity(Intent(this@StudyBlockerService, BlockedActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("package", pkg).putExtra("reason", reason).putExtra("limit_minutes", limit))
            }
        }
    }
    override fun onInterrupt() = Unit
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
