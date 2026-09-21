package com.pranav.study.cet_study_sprint

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("package") ?: run { finish(); return }
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { "this app" }
        val focus = intent.getBooleanExtra("focus_block", false)
        val limit = intent.getIntExtra("limit_minutes", 0)
        setContent {
            MaterialTheme {
                BlockedContent(label,
                    if (focus) "Blocked during your focus session"
                    else "You've used $label for $limit minutes today.",
                    onBack = {
                        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                    onBypass = {
                        getSharedPreferences(StudyBlockerService.PREFS, Context.MODE_PRIVATE).edit()
                            .putLong("bypass_until_$pkg", System.currentTimeMillis() + 300_000L).apply()
                        StudyData.events(this).recordLimitEvent("bypass", pkg)
                        packageManager.getLaunchIntentForPackage(pkg)?.let(::startActivity)
                        finish()
                    })
            }
        }
    }
}
@Composable
private fun BlockedContent(label: String, detail: String, onBack: () -> Unit, onBypass: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Time's up", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(detail, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Go back") }
        TextButton(onClick = onBypass, modifier = Modifier.fillMaxWidth()) {
            Text("Use $label for 5 more minutes")
        }
    }
}
