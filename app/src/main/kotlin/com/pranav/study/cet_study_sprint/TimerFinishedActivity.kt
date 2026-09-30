package com.pranav.study.cet_study_sprint

import android.os.Bundle
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A dedicated alarm screen exposes no study notes or profile data on the lock screen. */
class TimerFinishedActivity : ComponentActivity() {
    private var breakFinished by mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())
    private val expire = Runnable { FocusAlarm.dismiss(this); finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        breakFinished = intent.getBooleanExtra("is_break", false)
        handler.postDelayed(expire, 60_000)
        setContent {
            val prefs = getSharedPreferences("study_sprint", MODE_PRIVATE)
            val dark = when (prefs.getString("theme_mode", "system")) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                BackHandler { dismiss() }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (breakFinished) "Break finished" else "Focus complete",
                            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Text("Your timer has finished.")
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = {
                            FocusAlarm.dismiss(this@TimerFinishedActivity)
                            startActivity(AlertNavigation.intent(this@TimerFinishedActivity, "focus", FocusAlarm.NOTIFICATION_ID))
                            finish()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Open timer") }
                        OutlinedButton(onClick = { dismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Dismiss") }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        breakFinished = intent.getBooleanExtra("is_break", false)
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, 60_000)
    }

    private fun dismiss() { FocusAlarm.dismiss(this); finish() }
    override fun onDestroy() { handler.removeCallbacks(expire); super.onDestroy() }
}
