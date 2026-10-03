package com.pranav.study.cet_study_sprint

import android.app.UiAutomation
import android.app.AppOpsManager
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

class BlockingServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val pkg get() = instrumentation.context.packageName
    private lateinit var automation: UiAutomation
    private var oldServices = ""
    private var oldEnabled = "0"
    private var oldUsageMode = AppOpsManager.MODE_DEFAULT
    private var previous: Map<String, *> = emptyMap<String, Any>()
    private val keys get() = listOf("limit_$pkg", "limit_days_$pkg", "focus_block_$pkg", "bypass_until_$pkg",
        "focus_block_active", "focus_block_end", "pending_at_$pkg", "pending_minutes_$pkg", "pending_days_$pkg", "pending_focus_$pkg")
    private val component get() = "${context.packageName}/${StudyBlockerService::class.java.name}"
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).use { String(it.readBytes()).trim() }
    private fun await(description: String, timeout: Long = 12_000L, predicate: () -> Boolean) {
        val until = android.os.SystemClock.elapsedRealtime() + timeout
        while (android.os.SystemClock.elapsedRealtime() < until) {
            if (predicate()) return
            Thread.sleep(100L)
        }
        fail("Timed out: $description; foreground=${foreground()}")
    }
    private fun foreground(): String? {
        val root = automation.rootInActiveWindow ?: return null
        return root.packageName?.toString().also { root.recycle() }
    }
    private fun enableService() {
        val services = oldServices.split(':').filter { it.isNotBlank() && it != "null" } + component
        shell("settings put secure enabled_accessibility_services ${services.distinct().joinToString(":")}")
        shell("settings put secure accessibility_enabled 1")
        await("accessibility service enabled") { StrictLimits.blockerEnabled(context) }
    }
    private fun openFixture() {
        shell("am start -W -n $pkg/${BlockingFixtureActivity::class.java.name}")
        await("fixture opened") { foreground() == pkg }
    }
    @Before fun setup() {
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        oldServices = shell("settings get secure enabled_accessibility_services")
        oldEnabled = shell("settings get secure accessibility_enabled").ifBlank { "0" }
        oldUsageMode = context.getSystemService(AppOpsManager::class.java)
            .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        val p = StrictLimits.prefs(context)
        previous = p.all.filterKeys { it in keys }
        p.edit().putInt("limit_$pkg", 0).putInt("limit_days_$pkg", 127).putBoolean("focus_block_$pkg", false)
            .putBoolean("focus_block_active", false).remove("bypass_until_$pkg").remove("pending_at_$pkg").commit()
        shell("appops set ${context.packageName} GET_USAGE_STATS allow")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        enableService()
    }
    @After fun restore() {
        val p = StrictLimits.prefs(context)
        p.edit().putBoolean("focus_block_active", false).putInt("limit_$pkg", 0).commit()
        shell("input keyevent KEYCODE_HOME")
        if (oldServices.isBlank() || oldServices == "null") shell("settings delete secure enabled_accessibility_services")
        else shell("settings put secure enabled_accessibility_services $oldServices")
        shell("settings put secure accessibility_enabled $oldEnabled")
        val edit = p.edit(); keys.forEach(edit::remove)
        previous.forEach { (key, value) -> when (value) {
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
        } }
        edit.commit()
        shell("appops set ${context.packageName} GET_USAGE_STATS ${when(oldUsageMode) {
            AppOpsManager.MODE_ALLOWED -> "allow"; AppOpsManager.MODE_IGNORED -> "ignore"
            AppOpsManager.MODE_ERRORED -> "deny"; else -> "default"
        }}")
        automation.destroy()
    }
    @Test fun focusBlocksLimitedAppWithoutSeparateFocusSelectionOrUsagePermission() {
        openFixture()
        shell("appops set ${context.packageName} GET_USAGE_STATS ignore")
        StrictLimits.prefs(context).edit().putInt("limit_$pkg", 5).putBoolean("focus_block_$pkg", false)
            .putBoolean("focus_block_active", true).putLong("focus_block_end", System.currentTimeMillis() + 60_000L).commit()
        await("focus closes real limited foreground app") { foreground() != null && foreground() != pkg }
        assertEquals("focus", StrictLimits.blockReason(context, pkg, null, System.currentTimeMillis()))
    }
    @Test fun dailyLimitClosesAppWhileItRemainsOpenWithoutNewWindowEvents() {
        openFixture()
        val p = StrictLimits.prefs(context)
        val used = DailyUsage.usedToday(context, pkg)
        val minutes = (used / 60_000L).toInt() + 1
        p.edit().putInt("limit_$pkg", minutes).commit()
        await("live daily usage reaches configured limit and closes app", 85_000L) {
            DailyUsage.usedToday(context, pkg) >= minutes * 60_000L && foreground() != null && foreground() != pkg
        }
        assertEquals("daily", StrictLimits.blockReason(context, pkg, DailyUsage.measurement(context, pkg), System.currentTimeMillis()))
    }
    @Test fun reconnectDetectsAlreadyOpenFocusOnlyAppAndAllowsStudyApp() {
        openFixture()
        shell("settings delete secure enabled_accessibility_services")
        shell("settings put secure accessibility_enabled 0")
        await("service disconnected") { !StrictLimits.blockerEnabled(context) }
        StrictLimits.prefs(context).edit().putBoolean("focus_block_$pkg", true)
            .putBoolean("focus_block_active", true).putLong("focus_block_end", System.currentTimeMillis() + 60_000L).commit()
        enableService()
        await("reconnected service closes already open app") { foreground() != null && foreground() != pkg }
        shell("am start -W -n ${context.packageName}/${ComposeStudyActivity::class.java.name}")
        await("Study Sprint remains available during focus") { foreground() == context.packageName }
        Thread.sleep(1_500L)
        assertEquals(context.packageName, foreground())
    }
}
