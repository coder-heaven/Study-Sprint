package com.pranav.study.cet_study_sprint

import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.app.usage.UsageStatsManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
private fun SettingsRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp)
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}
@Composable
internal fun SettingsScreen(prefs: android.content.SharedPreferences, go: (String) -> Unit, refresh: () -> Unit) {
    val context = LocalContext.current
    val appVersion = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.2.0" }
    var theme by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
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
        AppHeading("Settings", "Make your study space yours.")
        SectionLabel("Study")
        SettingsRow("Exam & class", "${prefs.getString("exam", "CET")} • Class ${prefs.getString("grade", "11")}") { go("profile") }
        SettingsRow("Exam date", "Edit in profile") { go("profile") }
        Text("Daily focus goal: $goal min", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 14.dp))
        Slider(goal.toFloat(), onValueChange = { goal = (it / 15).toInt() * 15 },
            onValueChangeFinished = { prefs.edit().putInt("daily_focus_goal", goal).apply(); refresh() },
            valueRange = 15f..480f)
        SettingsRow("Focus preferences", "Custom timing and distraction control") { go("focus") }
        SectionLabel("Digital wellbeing")
        SettingsRow("App Limits") { go("limits") }
        SettingsRow("App Usage Statistics") { go("statistics") }
        SectionLabel("Notifications")
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
        SettingsRow("Local data", "Study history stays on this phone") { info = "Data is stored locally on this device. Account sync and export are not configured yet." }
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
private data class AppRecord(val pkg: String, val label: String, val usedMs: Long, val limit: Int)
private fun appRecords(context: Context, days: Int = 1): List<AppRecord> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val today = DailyUsage.startOfLocalDay()
    val usage: Map<String, Long> = if (!usageAllowed(context)) {
        emptyMap()
    } else if (days == 1) {
        DailyUsage.usedByPackageToday(context)
    } else {
        context.getSystemService(UsageStatsManager::class.java)
            .queryAndAggregateUsageStats(
                Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, -(days - 1)) }.timeInMillis,
                System.currentTimeMillis()
            ).mapValues { it.value.totalTimeInForeground }
    }
    val prefs = context.getSharedPreferences(StudyBlockerService.PREFS, Context.MODE_PRIVATE)
    val protected = Protection.packages(context)
    return pm.queryIntentActivities(intent, 0).mapNotNull { info ->
        val pkg = info.activityInfo.packageName
        if (pkg in protected) null else AppRecord(pkg, info.loadLabel(pm).toString(),
            usage[pkg] ?: 0L, prefs.getInt("limit_$pkg", 0))
    }.distinctBy { it.pkg }.sortedWith(compareByDescending<AppRecord> { it.limit > 0 }.thenByDescending { it.usedMs }.thenBy { it.label })
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppLimitsScreen(go: (String) -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var showBlockingGuide by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    LaunchedEffect(Unit) {
        while (true) {
            delay(DailyUsage.millisUntilNextDay().coerceAtLeast(1_000L))
            revision++
        }
    }
    var search by remember { mutableStateOf("") }
    var onlyLimited by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<AppRecord?>(null) }
    var minutes by remember(chosen) { mutableIntStateOf(chosen?.limit ?: 0) }
    var daysMask by remember(chosen) { mutableIntStateOf(chosen?.let { context.getSharedPreferences(StudyBlockerService.PREFS, Context.MODE_PRIVATE).getInt("limit_days_${it.pkg}", 127) } ?: 127) }
    var focusBlocked by remember(chosen) { mutableStateOf(chosen?.let { context.getSharedPreferences(StudyBlockerService.PREFS, Context.MODE_PRIVATE).getBoolean("focus_block_${it.pkg}", false) } ?: false) }
    val allowed = remember(revision) { usageAllowed(context) }
    val blockEvents by produceState<Map<String, Int>>(emptyMap(), revision) {
        value = withContext(Dispatchers.IO) { StudyData.events(context).limitCounts(1) }
    }
    val apps by produceState<List<AppRecord>>(emptyList(), revision) {
        value = withContext(Dispatchers.IO) { appRecords(context) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        AppHeading("App Limits", "Choose healthy daily limits.")
        Spacer(Modifier.height(12.dp))
        if (!allowed) StudyCard {
            Text("Usage access", fontWeight = FontWeight.Bold)
            Text("Enable usage access so Study Sprint can show your app time and enforce limits.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }) { Text("Enable usage access") }
        }
        val accessibility = remember(revision) {
            try {
                val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                enabled.contains(context.packageName, ignoreCase = true)
            } catch (_: Exception) { false }
        }
        if (!accessibility) {
            Spacer(Modifier.height(8.dp))
            StudyCard {
                Text("App blocking is optional", fontWeight = FontWeight.Bold)
                Text(
                    "You can set and track limits now. To automatically close an app at its limit, finish Android's protected Accessibility setup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showBlockingGuide = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) { Text("Guided setup") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCardLocal("${apps.sumOf { it.usedMs } / 60000}m", "App use", Modifier.weight(1f))
            MetricCardLocal("${apps.count { it.limit > 0 }}", "Limited", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text("${blockEvents["blocked"] ?: 0} blocks today", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(search, { search = it }, label = { Text("Search apps") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !onlyLimited, onClick = { onlyLimited = false }, label = { Text("All") })
            FilterChip(selected = onlyLimited, onClick = { onlyLimited = true }, label = { Text("Limited") })
            TextButton(onClick = { revision++ }) { Text("Refresh") }
        }
        val visible = apps.filter { it.label.contains(search, ignoreCase = true) && (!onlyLimited || it.limit > 0) }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.pkg }) { app ->
                StudyCard(Modifier.clickable { chosen = app }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AndroidView(factory = { ImageView(it).apply {
                            setImageDrawable(runCatching { context.packageManager.getApplicationIcon(app.pkg) }.getOrNull())
                        } }, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(if (app.limit > 0) "${app.usedMs / 60000}m / ${app.limit}m"
                                 else "${app.usedMs / 60000}m today • no limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Edit", color = MaterialTheme.colorScheme.primary)
                    }
                    if (app.limit > 0) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { ((app.usedMs / 60000f) / app.limit).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (showBlockingGuide) ModalBottomSheet(onDismissRequest = { showBlockingGuide = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
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
                "Only enable this if you trust this copy of Study Sprint. The service observes which app opens so it can enforce the limits you set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
    if (chosen != null) ModalBottomSheet(onDismissRequest = { chosen = null }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
            Text(chosen!!.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Used today: ${chosen!!.usedMs / 60000} min",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("Block during focus", modifier = Modifier.weight(1f))
                Switch(focusBlocked, onCheckedChange = { focusBlocked = it })
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                context.getSharedPreferences(StudyBlockerService.PREFS, Context.MODE_PRIVATE)
                    .edit().putInt("limit_${chosen!!.pkg}", minutes)
                    .putBoolean("focus_block_${chosen!!.pkg}", focusBlocked)
                    .putInt("limit_days_${chosen!!.pkg}", daysMask).apply()
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
        if (tab == 0) Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCardLocal("${totals.focusedMinutes}m", "Focused", Modifier.weight(1f))
                MetricCardLocal("${totals.sessions}", "Sessions", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCardLocal("${totals.tasks}", "Tasks done", Modifier.weight(1f))
                MetricCardLocal("${totals.questions}", "Questions", Modifier.weight(1f))
            }
            SectionLabel("Study streak")
            StudyCard { Text("$streak days with meaningful study activity") }
            SectionLabel("Daily focus")
            StudyCard { WeeklyStudyChart(totals.dailyMinutes) }
            SectionLabel("Subject focus")
            StudyCard {
                if (subjects.isEmpty()) Text("No recorded subject time yet.")
                subjects.forEach { (subject, minutes) -> Text("$subject • $minutes min") }
            }
            SectionLabel("Practice accuracy")
            StudyCard {
                Text(if (totals.questions == 0) "No recorded practice yet."
                     else "${totals.correct} correct of ${totals.questions} • ${totals.correct * 100 / totals.questions}%")
            }
            SectionLabel("Study history")
            StudyCard {
                Text("New focus sessions, completed tasks and quizzes are recorded here. Earlier focus counts have no duration, so they are not included in minutes.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Completed sessions including earlier history: ${prefs.getInt("focus_sessions", 0)}",
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
        } else Column(Modifier.verticalScroll(rememberScrollState())) {
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
                    Text("${limitEvents["blocked"] ?: 0} blocks • ${limitEvents["bypass"] ?: 0} five-minute bypasses")
                }
                SectionLabel("Most used apps")
                apps.filter { it.usedMs > 0 }.sortedByDescending { it.usedMs }.take(10).forEach { app ->
                    SettingsRow(app.label, "${app.usedMs / 60000} min") {}
                }
            }
        }
    }
}
