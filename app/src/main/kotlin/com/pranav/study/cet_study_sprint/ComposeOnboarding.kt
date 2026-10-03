package com.pranav.study.cet_study_sprint

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
internal fun OnboardingGate(prefs: SharedPreferences, refresh: () -> Unit, content: @Composable () -> Unit) {
    var stage by remember { mutableStateOf(OnboardingStore.stage(prefs)) }
    var error by remember { mutableStateOf("") }
    fun saved(ok: Boolean) {
        if (ok) { stage = OnboardingStore.stage(prefs); error = ""; refresh() }
        else error = "Could not save setup. Please try again; your existing data has not been reset."
    }
    Column(Modifier.fillMaxSize()) {
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        Box(Modifier.weight(1f)) {
            when (stage) {
                StartupStage.TERMS -> FirstRunTermsScreen { saved(OnboardingStore.acceptTerms(prefs)) }
                StartupStage.PROFILE -> WelcomeScreen(prefs) { name, course, grade ->
                    saved(OnboardingStore.saveProfile(prefs, name, course, grade))
                }
                else -> content()
            }
        }
    }
}

@Composable
internal fun FirstRunTermsScreen(onAccept: () -> Unit) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    var document by rememberSaveable { mutableStateOf<String?>(null) }
    if (document != null) PrivacyDocumentDialog(document == "terms") { document = null }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
        .padding(24.dp).testTag("first_run_terms"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(painterResource(R.drawable.art_login_3d),
            contentDescription = "Illustration of a book and graduation cap",
            modifier = Modifier.fillMaxWidth().height(120.dp))
        AppHeading("Welcome to Study Sprint", "Step 1 · Terms & privacy")
        Text("Before setting up your study profile, please review the Terms of Use and Privacy Policy.")
        OutlinedButton(onClick = { document = "terms" }, modifier = Modifier.fillMaxWidth()) { Text("Read Terms of Use") }
        OutlinedButton(onClick = { document = "privacy" }, modifier = Modifier.fillMaxWidth()) { Text("Read Privacy Policy") }
        Text("Your study history is stored on this device. Google sign-in, notifications, app blocking and public leaderboard sharing are optional. Accepting these terms does not enable public sharing or grant Android permissions.")
        Row(Modifier.fillMaxWidth().testTag("terms_accept_checkbox")
            .toggleable(accepted, role = Role.Checkbox, onValueChange = { accepted = it }),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(accepted, onCheckedChange = null)
            Text("I agree to the Terms of Use and acknowledge the Privacy Policy. I have any parent or guardian permission required.",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onAccept, enabled = accepted, modifier = Modifier.fillMaxWidth()
            .heightIn(min = 52.dp).testTag("terms_continue")) { Text("Agree and continue") }
        Text("If you do not agree, close the app. You can read these documents again in Settings.",
            style = MaterialTheme.typography.bodySmall)
    }
}

private data class SetupTask(val title: String, val detail: String, val ready: (android.content.Context) -> Boolean, val open: (android.content.Context) -> Unit)

@Composable
internal fun InitialSetupScreen(prefs: SharedPreferences, go: (String) -> Unit, refresh: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(OnboardingStore.setupStep(prefs)) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val notificationReady = remember(revision) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val usageReady = remember(revision) { StrictLimits.usageAllowed(context) }
    val blockingReady = remember(revision) { StrictLimits.blockerEnabled(context) }
    val alarmsReady = remember(revision) {
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    val tasks = listOf(
            SetupTask("Notifications", "Allow reminders and focus alerts.", { notificationReady }) { c ->
                c.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName))
            },
            SetupTask("Usage access", "Measure app time for daily limits.", { usageReady }) { c -> c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            SetupTask("App blocking", "Optional Accessibility access for distraction blocking.", { blockingReady }) { c -> c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            SetupTask("Precise alarms", "Keep reminders and timers on schedule.", { alarmsReady }) { c -> c.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}"))) }
    )
    fun saveStep(next: Int) {
        if (OnboardingStore.saveSetupStep(prefs, next)) { step = next; error = ""; refresh() }
        else error = "Could not save this step. Your existing data is unchanged."
    }
    Column(Modifier.fillMaxSize().testTag("setup_screen")) {
        if (step == 0) {
            Box(Modifier.weight(1f)) { SettingsScreen(prefs, go, refresh, setupMode = true, showPermissions = false) }
            SetupFooter("Step 1 of 5", "Save preferences and continue", { saveStep(1) }, "setup_continue")
        } else if (step in 1..4) {
            val task = tasks[step - 1]
            val ready = task.ready(context)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppHeading("Set up your phone", "Step $step of 5")
                LinearProgressIndicator({ step / 5f }, Modifier.fillMaxWidth())
                StudyCard {
                    Text(task.title, style = MaterialTheme.typography.headlineSmall)
                    Text(task.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (ready) "Ready" else "Not enabled", color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { runCatching { task.open(context) }.onFailure { error = "Android Settings could not be opened." } }, Modifier.fillMaxWidth().testTag("setup_open_${step}")) { Text("Open Android settings") }
                    Text("When you return, this screen stays open and refreshes automatically.", style = MaterialTheme.typography.bodySmall)
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { saveStep(if (step == 4) 5 else step + 1) },
                        Modifier.weight(1f).heightIn(min = 48.dp).testTag("setup_skip_${step}")) { Text("Skip") }
                    Button(onClick = { saveStep(if (step == 4) 5 else step + 1) },
                        Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (step == 4) "Finish settings" else "Next") }
                }
            }
        } else {
            LaunchedEffect(Unit) { if (OnboardingStore.finishSetup(prefs)) { refresh(); go("tutorial") } }
            Box(Modifier.fillMaxSize().testTag("setup_finished"))
        }
    }
}

@Composable
private fun SetupFooter(label: String, action: String, onClick: () -> Unit, tag: String) {
    Surface(shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Button(onClick = onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)) { Text(action) }
            Text("You can change these choices later in Settings.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** One place to review permission state, never requests several permissions automatically. */
@Composable
internal fun PermissionSetupCard() {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    val notifications = remember(revision) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val usage = remember(revision) { StrictLimits.usageAllowed(context) }
    val blocker = remember(revision) { StrictLimits.blockerEnabled(context) }
    val exact = remember(revision) { Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure { error = "This settings page is unavailable. Open Android Settings → Apps → Study Sprint manually." }
    }
    StudyCard {
        Text("Optional Android permissions", style = MaterialTheme.typography.titleMedium)
        Text("Enable only what you need. Returning from Android Settings refreshes the status below.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, modifier = Modifier.fillMaxWidth()) { Text("Notifications · ${if (notifications) "Ready" else "Optional"}") }
        OutlinedButton(onClick = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
            Text("Usage Access · ${if (usage) "Ready" else "Needed for app limits"}")
        }
        Text("App blocking uses Accessibility to detect the foreground app and enforce the limits you choose. It is optional; only enable it for a copy of Study Sprint you trust. On Android 13+, a GitHub APK may require App info → ⋮ → Allow restricted settings first.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Open app info / restricted settings") }
        OutlinedButton(onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
            Text("App blocking · ${if (blocker) "Ready" else "Optional"}")
        }
        if (Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = {
            open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }, modifier = Modifier.fillMaxWidth()) { Text("Precise timer alarms · ${if (exact) "Ready" else "Optional"}") }
        Text("No permissions are needed to start planning or studying locally.", style = MaterialTheme.typography.bodySmall)
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}
