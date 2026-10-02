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

@Composable
internal fun InitialSetupScreen(prefs: SharedPreferences, go: (String) -> Unit, refresh: () -> Unit) {
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().testTag("setup_screen")) {
        Box(Modifier.weight(1f)) { SettingsScreen(prefs, go, refresh, setupMode = true) }
        Surface(shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    if (OnboardingStore.finishSetup(prefs)) { refresh(); go("tutorial") }
                    else error = "Could not save setup. Please try again."
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("setup_continue")) {
                    Text("Continue to app tutorial")
                }
                Text("Permissions are optional. Keep the defaults or change settings later.", style = MaterialTheme.typography.bodySmall)
            }
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
