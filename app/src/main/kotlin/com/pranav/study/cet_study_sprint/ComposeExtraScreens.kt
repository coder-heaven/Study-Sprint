package com.pranav.study.cet_study_sprint

import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.app.usage.UsageStatsManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.content.Context
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
private fun SettingsRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.heightIn(min = 68.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                val icon = when {
                    label.contains("Privacy") || label.contains("Terms") -> R.drawable.figma_privacy
                    label.contains("sound", ignoreCase = true) -> R.drawable.figma_sound
                    label.contains("reminder", ignoreCase = true) || label.contains("date", ignoreCase = true) -> R.drawable.figma_calendar
                    else -> null
                }
                if (icon != null) Image(painterResource(icon), null, Modifier.padding(8.dp).size(20.dp))
                else Text(label.take(1), modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Image(painterResource(R.drawable.figma_chevron), null, Modifier.size(16.dp))
        }
    }
}
@Composable
internal fun SettingsScreen(prefs: android.content.SharedPreferences, go: (String) -> Unit, refresh: () -> Unit, setupMode: Boolean = false, showPermissions: Boolean = true) {
    val context = LocalContext.current
    val appVersion = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.0" }
    var theme by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
    var goal by remember { mutableIntStateOf(prefs.getInt("daily_focus_goal", 120)) }
    var studyReminder by remember { mutableStateOf(StudyReminders.enabled(context, StudyReminders.STUDY)) }
    var planReminder by remember { mutableStateOf(StudyReminders.enabled(context, StudyReminders.PLAN)) }
    var studyTime by remember { mutableStateOf(StudyReminders.timeLabel(context, StudyReminders.STUDY)) }
    var planTime by remember { mutableStateOf(StudyReminders.timeLabel(context, StudyReminders.PLAN)) }
    var pendingReminder by remember { mutableStateOf(StudyReminders.STUDY) }
    var info by remember { mutableStateOf("") }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            StudyReminders.setEnabled(context, pendingReminder, true)
            if (pendingReminder == StudyReminders.STUDY) studyReminder = true else planReminder = true
        }
    }
    fun setReminder(kind: String, enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminder = kind
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            StudyReminders.setEnabled(context, kind, enabled)
            if (kind == StudyReminders.STUDY) studyReminder = enabled else planReminder = enabled
        }
    }
    fun chooseReminderTime(kind: String) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                StudyReminders.setTime(context, kind, hour, minute)
                if (kind == StudyReminders.STUDY) {
                    studyTime = StudyReminders.timeLabel(context, kind)
                } else {
                    planTime = StudyReminders.timeLabel(context, kind)
                }
            },
            StudyReminders.hour(context, kind),
            StudyReminders.minute(context, kind),
            android.text.format.DateFormat.is24HourFormat(context)
        ).show()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        AppHeading(if (setupMode) "Set up Study Sprint" else "Settings",
            if (setupMode) "Step 3 · All key settings in one place. Nothing is required." else "Your preferences, clearly organized.")
        if (setupMode) {
            Text("Review your study goal, reminders, appearance and optional permissions below. Your choices save as you change them. Advanced features open from the relevant button; return with Back.",
                style = MaterialTheme.typography.bodyMedium)
            if (showPermissions) PermissionSetupCard()
        } else {
            SettingsRow("Setup checklist", "Review settings and Android permissions together") { go("setup") }
            SettingsRow("How to use the app", "Replay the step-by-step tutorial") { go("tutorial") }
        }
        SectionLabel("Study")
        SettingsRow("Exam & class", "${prefs.getString("exam", "CET")} • Class ${prefs.getString("grade", "11")}") { go("profile") }
        SettingsRow("Exam date", "Edit in profile") { go("profile") }
        Text("Daily focus goal: $goal min", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 14.dp))
        Slider(goal.toFloat(), onValueChange = { goal = (it / 15).toInt() * 15 },
            onValueChangeFinished = { prefs.edit().putInt("daily_focus_goal", goal).apply(); refresh() },
            valueRange = 15f..480f)
        SettingsRow("Focus preferences", "Custom timing and distraction control") { go("focus") }
        SettingsRow("Student leaderboards", "Live study effort and quiz wins") { go("leaderboard") }
        SectionLabel("Digital wellbeing")
        SettingsRow("App Limits", "Daily limits · YouTube extra time") { go("limits") }
        SettingsRow("App Usage Statistics") { go("app_usage") }
        SectionLabel("Notifications")
        SettingsRow("Timer sound", "Alarm sound and vibration") {
            FocusAlarm.createChannel(context)
            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, FocusAlarm.CHANNEL))
        }
        SettingsRow("Reminder sound", "Notification sound and vibration") {
            StudyReminders.createChannel(context)
            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, StudyReminders.CHANNEL))
        }
        Text("Tap a reminder to open its screen. When Study Sprint is already visible, it opens that screen automatically.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily study reminder")
                TextButton(onClick = { chooseReminderTime(StudyReminders.STUDY) }, contentPadding = PaddingValues(0.dp)) {
                    Text("$studyTime  •  Change time")
                }
            }
            Switch(studyReminder, onCheckedChange = { setReminder(StudyReminders.STUDY, it) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Plan reminder")
                TextButton(onClick = { chooseReminderTime(StudyReminders.PLAN) }, contentPadding = PaddingValues(0.dp)) {
                    Text("$planTime  •  Change time")
                }
            }
            Switch(planReminder, onCheckedChange = { setReminder(StudyReminders.PLAN, it) })
        }

        SectionLabel("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system", "light", "dark").forEach { option ->
                FilterChip(selected = theme == option, onClick = {
                    theme = option; prefs.edit().putString("theme_mode", option).apply(); refresh()
                }, label = { Text(option.replaceFirstChar { it.uppercase() }) })
            }
        }
        SectionLabel("Account")
        SettingsRow("Profile", "Local profile") { go("profile") }
        Text("Google sign-in is available. Study history and settings remain stored on this device.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel("Data & privacy")
        SettingsRow("Privacy Policy", "Data, permissions and public sharing") { go("privacy") }
        SettingsRow("Terms of Use", "Responsible use and optional services") { go("terms") }
        SettingsRow("Leaderboard privacy", "Review or withdraw public sharing") { go("leaderboard") }
        SettingsRow("Updates & saved data", "Normal app updates keep your existing data") { info = "Installing a newer official APK over this app keeps your study history, syllabus progress, notes, MCQs, app limits and settings on this phone. Do not uninstall or clear app storage before updating. Google sign-in is not a full study-data backup. Android backup availability depends on your device and settings; it is not guaranteed. Public leaderboard sharing remains a separate optional choice." }
        SectionLabel("About")
        SettingsRow("About Study Sprint", "Version $appVersion") { info = "Study Sprint helps you plan, focus, practice and track your exam preparation. Version $appVersion." }
        Spacer(Modifier.height(12.dp))
        UpdateSettingsCard()
        Spacer(Modifier.height(24.dp))
    }
    if (info.isNotEmpty()) AlertDialog(onDismissRequest = { info = "" },
        title = { Text("Study Sprint") }, text = { Text(info) },
        confirmButton = { TextButton(onClick = { info = "" }) { Text("OK") } })
}

private fun usageAllowed(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}
private data class AppRecord(val pkg: String, val label: String, val usedMs: Long, val limit: Int, val day: Long)
private fun appRecords(context: Context, days: Int = 1): List<AppRecord> {
    val now = System.currentTimeMillis()
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val today = DailyUsage.startOfLocalDay(now)
    val usage: Map<String, Long> = if (!usageAllowed(context)) {
        emptyMap()
    } else if (days == 1) {
        DailyUsage.usedByPackageToday(context, now)
    } else {
        context.getSystemService(UsageStatsManager::class.java)
            .queryAndAggregateUsageStats(
                Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, -(days - 1)) }.timeInMillis,
                System.currentTimeMillis()
            ).mapValues { it.value.totalTimeInForeground }
    }
    val prefs = context.getSharedPreferences(StudyBlockerService.PREFS, Context.MODE_PRIVATE)
    val protected = Protection.packages(context)
    val launcherLabels = pm.queryIntentActivities(intent, 0)
        .associate { it.activityInfo.packageName to it.loadLabel(pm).toString() }
    // Usage events include apps hidden from the launcher; keep saved limits visible too.
    val savedPackages = prefs.all.keys.mapNotNull { key ->
        when {
            key.startsWith("limit_") && !key.startsWith("limit_days_") -> key.removePrefix("limit_")
            key.startsWith("focus_block_") -> key.removePrefix("focus_block_")
            else -> null
        }
    }
    val validSavedPackages = savedPackages.filter { '.' in it }
    val packages = launcherLabels.keys + usage.filterValues { it > 0 }.keys + validSavedPackages + YouTubeQuota.PACKAGE
    return packages.asSequence().filter { it !in protected && it.isNotBlank() }
        .map { pkg ->
            val label = launcherLabels[pkg] ?: runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(if (pkg == YouTubeQuota.PACKAGE) "YouTube" else pkg)
            AppRecord(pkg, label, usage[pkg] ?: 0L, StrictLimits.dailyLimit(prefs, pkg), today)
        }.distinctBy { it.pkg }
        .sortedWith(compareByDescending<AppRecord> { it.limit > 0 }.thenByDescending { it.usedMs }.thenBy { it.label })
        .toList()
}

private fun blockerEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java)
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == StudyBlockerService::class.java.name }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppLimitsScreen(go: (String) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var revision by remember { mutableIntStateOf(0) }
    var showBlockingGuide by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    var day by remember { mutableLongStateOf(LocalDay.start(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            delay(1_000L)
            val currentDay = LocalDay.start(System.currentTimeMillis())
            if (day != currentDay) { day = currentDay; revision++; ticks = 0 }
            else if (++ticks >= 5) { revision++; ticks = 0 }
        }
    }
    var search by remember { mutableStateOf("") }
    var onlyLimited by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<AppRecord?>(null) }
    var minutes by remember(chosen) { mutableIntStateOf(chosen?.limit ?: 0) }
    var daysMask by remember(chosen) { mutableIntStateOf(chosen?.let { context.getSharedPreferences(StudyBlockerService.PREFS, Context.MODE_PRIVATE).getInt("limit_days_${it.pkg}", 127) } ?: 127) }
    var focusBlocked by remember(chosen) { mutableStateOf(chosen?.let { context.getSharedPreferences(StudyBlockerService.PREFS, Context.MODE_PRIVATE).getBoolean("focus_block_${it.pkg}", false) } ?: false) }
    val allowed = remember(revision) { StrictLimits.applyPending(context); usageAllowed(context) }
    val blockEvents by produceState<Map<String, Int>>(emptyMap(), revision) {
        value = withContext(Dispatchers.IO) { StudyData.events(context).limitCounts(1) }
    }
    val records by produceState<List<AppRecord>>(emptyList(), revision, day) {
        value = withContext(Dispatchers.IO) { appRecords(context) }
    }
    val apps = records.filter { it.day == day }
    var saveMessage by remember { mutableStateOf("") }
    LaunchedEffect(day) { chosen = null; saveMessage = "" }
    LazyColumn(Modifier.fillMaxSize().testTag("app_limits_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AppHeading("App Limits", "Protect your time. Keep your commitments.") }
        item { YouTubeAllowanceCard() }
        item {
            StudyCard {
                Text("Protection status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Usage Access: ${if (allowed) "Ready" else "Needed"} · Blocking: ${if (blockerEnabled(context)) "Ready" else "Needed"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!allowed) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Enable Usage Access") }
                if (!blockerEnabled(context)) OutlinedButton(onClick = { showBlockingGuide = true }, modifier = Modifier.fillMaxWidth()) { Text("Set up app blocking") }
                Text("Daily usage and YouTube sessions reset at 12:00 AM in your phone's time zone.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Text("Limited apps also block during protected focus. Turn on Block distracting apps on the timer. Tap any app, including YouTube, to edit its daily limit. YouTube offers two five-minute bypasses after its daily limit is reached. Active focus locks cannot be bypassed.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCardLocal("${apps.sumOf { it.usedMs } / 60000}m", "App use", Modifier.weight(1f))
                MetricCardLocal("${apps.count { it.limit > 0 }}", "Protected", Modifier.weight(1f))
            }
        }
        item {
            Text("${blockEvents["blocked"] ?: 0} blocks today", style = MaterialTheme.typography.labelMedium)
            if (saveMessage.isNotBlank()) Text(saveMessage, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(search, { search = it }, label = { Text("Search apps") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("app_limits_search"), singleLine = true, shape = MaterialTheme.shapes.large)
            Row(Modifier.testTag("app_limits_filters"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyLimited, onClick = { onlyLimited = false }, label = { Text("All") })
                FilterChip(selected = onlyLimited, onClick = { onlyLimited = true }, label = { Text("Limited") })
                TextButton(onClick = { revision++ }) { Text("Refresh") }
            }
        }
        val visible = apps.filter {
            (it.label.contains(search, ignoreCase = true) || it.pkg.contains(search, ignoreCase = true)) &&
                (!onlyLimited || it.limit > 0)
        }
        items(visible, key = { it.pkg }) { app ->
            StudyCard(Modifier.testTag("limit_app_${app.pkg}").clickable {
                focusManager.clearFocus()
                chosen = app
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AndroidView(factory = { ImageView(it).apply {
                        setImageDrawable(runCatching { context.packageManager.getApplicationIcon(app.pkg) }.getOrNull())
                    } }, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold)
                        Text(if (app.limit > 0) "${app.usedMs / 60000}m of ${app.limit}m · Daily limit"
                             else "${app.usedMs / 60000}m today · No limit",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (StrictLimits.prefs(context).contains("pending_at_${app.pkg}"))
                            Text("Change queued until focus ends", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
                if (app.limit > 0) LinearProgressIndicator(progress = { (app.usedMs / (app.limit * 60000f)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp))
            }
        }
        if (visible.isEmpty()) item { StudyCard { Text(if (apps.isEmpty()) "Loading apps…" else "No apps match this filter.") } }
        item { Text("Hidden apps appear after use. Separate private profiles and browser versions of YouTube are outside this app's allowance.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (showBlockingGuide) ModalBottomSheet(onDismissRequest = { showBlockingGuide = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
            Text("Enable app blocking", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Android 13 and newer restrict sensitive permissions for apps installed from GitHub. This is an Android safety requirement; Study Sprint cannot switch it on for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
            )
            Text("1  Allow restricted settings", fontWeight = FontWeight.Bold)
            Text("Open App info, tap the three-dot menu (⋮), then tap Allow restricted settings.",
                style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Open Study Sprint app info") }
            Spacer(Modifier.height(18.dp))
            Text("2  Turn on Study Sprint app limits", fontWeight = FontWeight.Bold)
            Text("Return here, open Accessibility, select Study Sprint app limits, and turn it on.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Open Accessibility settings") }
            Text(
                "Only enable this if you trust this copy of Study Sprint. The service reads only the active window’s app package identifier, never screen text, to enforce your limits. After updating, switch this service off and on here if blocking needs reconnecting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
    if (chosen != null) ModalBottomSheet(onDismissRequest = { chosen = null }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
            Text(chosen!!.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Used today: ${chosen!!.usedMs / 60000} min",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (chosen!!.pkg == YouTubeQuota.PACKAGE) Text(
                "Use YouTube normally until this limit is reached, then choose up to two five-minute bypasses per day.",
                style = MaterialTheme.typography.bodySmall)
            SectionLabel("Daily limit")
            listOf(0, 15, 30, 45, 60, 120).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { value ->
                        FilterChip(selected = minutes == value, onClick = { minutes = value },
                            label = { Text(if (value == 0) "Off" else "$value min") })
                    }
                }
            }
            Slider(value = minutes.toFloat(), onValueChange = {
                minutes = ((it / 5).toInt() * 5).coerceIn(0, 240)
            }, valueRange = 0f..240f)
            Text("Custom: $minutes min")
            SectionLabel("Apply on")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, day ->
                    FilterChip(selected = daysMask and (1 shl index) != 0, onClick = {
                        daysMask = daysMask xor (1 shl index)
                    }, label = { Text(day) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (minutes > 0 || chosen?.pkg == YouTubeQuota.PACKAGE) "Included during protected focus" else "Block during focus", modifier = Modifier.weight(1f))
                Switch(focusBlocked || minutes > 0 || chosen?.pkg == YouTubeQuota.PACKAGE, onCheckedChange = { focusBlocked = it },
                    enabled = minutes == 0 && chosen?.pkg != YouTubeQuota.PACKAGE)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val deferred = StrictLimits.save(context, chosen!!.pkg, minutes, daysMask, focusBlocked)
                saveMessage = if (deferred) "Change saved for when your focus session ends." else "Daily limit saved. Changes apply now."
                chosen = null; revision++
            }, modifier = Modifier.fillMaxWidth()) { Text("Save limit") }
        }
    }
}
@Composable
private fun MetricCardLocal(value: String, label: String, modifier: Modifier) {
    StudyCard(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun ProgressMetricCard(value: String, label: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.colorScheme
    val cardColor = if (palette.background == MintBackground) tint else palette.surfaceVariant
    Surface(modifier = modifier.heightIn(min = 106.dp), shape = RoundedCornerShape(24.dp), color = cardColor) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = palette.onSurface)
        }
    }
}

@Composable
internal fun StatisticsScreen(
    prefs: android.content.SharedPreferences,
    initialTab: Int = 0,
    title: String = "Statistics",
    subtitle: String = "Your real progress, over time."
) {
    val context = LocalContext.current
    val model: StatisticsViewModel = viewModel()
    val study by model.state.collectAsStateWithLifecycle()
    val days = study.days
    val totals = study.totals
    val subjects = study.subjects
    val streak = study.streak
    val limitEvents = study.limitEvents
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var usageRevision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { usageRevision++ }
    val apps by produceState<List<AppRecord>>(emptyList(), tab, days, usageRevision) {
        value = withContext(Dispatchers.IO) { appRecords(context, days) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        AppHeading(title, subtitle)
        Spacer(Modifier.height(10.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Study") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("App usage") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "Today", 7 to "7 days", 30 to "30 days").forEach { (count, label) ->
                FilterChip(selected = days == count, onClick = { model.selectDays(count) }, label = { Text(label) })
            }
        }
        if (tab == 0) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel("Learning at a glance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressMetricCard("${totals.focusedMinutes}m", "Focused", androidx.compose.ui.graphics.Color(0xFFDCEEFF), Modifier.weight(1f))
                ProgressMetricCard("${totals.sessions}", "Sessions", androidx.compose.ui.graphics.Color(0xFFE6E3FF), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressMetricCard("${totals.tasks}", "Tasks done", androidx.compose.ui.graphics.Color(0xFFDDF4EB), Modifier.weight(1f))
                ProgressMetricCard("${totals.questions}", "Questions", androidx.compose.ui.graphics.Color(0xFFFFECCA), Modifier.weight(1f))
            }
            SectionLabel("Daily focus")
            Surface(shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("$streak-day study streak", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Last 7 days · selected period", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    WeeklyStudyChart(totals.dailyMinutes)
                }
            }
            SectionLabel("Subject focus")
            StudyCard {
                if (subjects.isEmpty()) Text("No recorded subject time yet.")
                subjects.forEach { (subject, minutes) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(subject, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("$minutes min", color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(progress = { minutes.toFloat() / subjects.values.sum().coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(6.dp))
                }
            }
            SectionLabel("Practice accuracy")
            StudyCard {
                Text(if (totals.questions == 0) "No recorded practice yet."
                     else "${totals.correct} correct of ${totals.questions} • ${totals.correct * 100 / totals.questions}%")
            }
            SectionLabel("Study history")
            StudyCard {
                Text("${prefs.getInt("focus_sessions", 0)} completed sessions including earlier history",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Earlier focus counts have no recorded duration and are excluded from minutes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!usageAllowed(context)) StudyCard {
                Text("Usage access is needed for real app statistics.")
                val ctx = LocalContext.current
                TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Text("Enable usage access")
                }
            } else {
                MetricCardLocal("${apps.sumOf { it.usedMs } / 60000}m", "App use in period", Modifier.fillMaxWidth())
                SectionLabel("Limit activity")
                StudyCard {
                    Text("${limitEvents["limit_reached"] ?: 0} limits reached")
                    Text("${limitEvents["blocked"] ?: 0} blocks prevented distractions")
                }
                SectionLabel("Most used apps")
                apps.filter { it.usedMs > 0 }.sortedByDescending { it.usedMs }.take(10).forEach { app ->
                    SettingsRow(app.label, "${app.usedMs / 60000} min") {}
                }
            }
        }
    }
}
