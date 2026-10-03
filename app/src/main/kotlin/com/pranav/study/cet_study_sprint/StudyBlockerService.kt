package com.pranav.study.cet_study_sprint

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.*

class StudyBlockerService : AccessibilityService() {
    companion object { const val PREFS = "study_blocker" }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checker: Job? = null
    private var protected = emptySet<String>()
    private var keyboards = emptySet<String>()
    private var lastPackage = ""
    private var lastBlockAt = 0L
    private var lastDay = Long.MIN_VALUE

    override fun onServiceConnected() {
        super.onServiceConnected()
        protected = Protection.packages(this)
        keyboards = (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .inputMethodList.map { it.packageName }.toSet()
        scope.launch {
            while (isActive) { requestCheck(); delay(1_000L) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) requestCheck()
    }

    /** Only package names are inspected, never children or screen text. Binder reads run off Main. */
    private suspend fun currentPackage(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val active = rootInActiveWindow
            val pkg = active?.packageName?.toString()
            active?.recycle()
            if (pkg != null && pkg !in keyboards) return@runCatching pkg
            // An input-method window must not hide the app using the keyboard.
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
                .sortedByDescending { it.isFocused }.firstNotNullOfOrNull { window ->
                    val root = window.root
                    val name = root?.packageName?.toString()
                    root?.recycle()
                    name?.takeUnless { it in keyboards }
                }
        }.getOrNull()
    }

    private fun interactive(): Boolean = getSystemService(android.os.PowerManager::class.java).isInteractive &&
        !getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked

    private fun requestCheck() {
        if (checker?.isActive == true || !interactive()) return
        checker = scope.launch {
            try { checkForeground() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { android.util.Log.w("StudyBlocker", "Will retry foreground check", failure) }
        }
    }

    private suspend fun checkForeground() {
        val pkg = currentPackage() ?: return
        if (pkg in protected) return
        val now = System.currentTimeMillis()
        val day = LocalDay.start(now)
        StrictLimits.applyPending(this)
        if (day != lastDay) {
            lastDay = day; lastPackage = ""; lastBlockAt = 0L
            StrictLimits.quota(this, now)
        }
        val p = StrictLimits.prefs(this)
        val focus = StrictLimits.focusBlocked(p, pkg, now)
        val limit = StrictLimits.dailyLimit(p, pkg)
        // Focus is immediate and independent of Usage Access / a slow usage query.
        val usage = if (!focus && limit > 0 && StrictLimits.usageAllowed(this)) withContext(Dispatchers.IO) {
            DailyUsage.measurement(this@StudyBlockerService, pkg)
        } else null
        val reason = StrictLimits.blockReason(this, pkg, usage, System.currentTimeMillis()) ?: return
        if (reason == "checking") return
        val elapsed = SystemClock.elapsedRealtime()
        if (pkg == lastPackage && elapsed - lastBlockAt < 1_200L) return
        delay(150L)
        if (!interactive() || currentPackage() != pkg ||
            StrictLimits.blockReason(this, pkg, usage, System.currentTimeMillis()) != reason) return
        lastPackage = pkg; lastBlockAt = elapsed
        runCatching {
            startActivity(if (pkg == YouTubeQuota.PACKAGE) BlockNavigation.studyIntent(this, "limits")
                else BlockNavigation.blockedIntent(this, pkg, reason, limit))
        }
        // Background starts can be silently refused: no exception means no guarantee.
        delay(450L)
        if (interactive() && currentPackage() == pkg &&
            StrictLimits.blockReason(this, pkg, usage, System.currentTimeMillis()) == reason) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        // History failures must never prevent enforcement.
        withContext(Dispatchers.IO) {
            runCatching {
                val history = StudyData.events(this@StudyBlockerService)
                if (reason == "daily" || reason == "youtube_daily") {
                    val reachedDay = LocalDay.start(System.currentTimeMillis()).toString()
                    if (p.getString("last_reached_$pkg", null) != reachedDay) {
                        p.edit().putString("last_reached_$pkg", reachedDay).apply()
                        history.recordLimitEvent("limit_reached", pkg)
                    }
                }
                history.recordLimitEvent("blocked", pkg)
            }
        }
    }
    override fun onInterrupt() = Unit
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
