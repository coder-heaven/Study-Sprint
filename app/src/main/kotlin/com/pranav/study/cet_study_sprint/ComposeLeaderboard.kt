package com.pranav.study.cet_study_sprint

import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

@Composable
internal fun LeaderboardScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val repository = remember { Leaderboards.repository(context) }
    val scope = rememberCoroutineScope()
    var joined by remember { mutableStateOf(prefs.getBoolean("leaderboard_enabled", false)) }
    var uid by remember { mutableStateOf(prefs.getString("leaderboard_uid", null)) }
    var quiz by rememberSaveable { mutableStateOf(false) }
    var optedOut by remember { mutableStateOf(prefs.getBoolean("leaderboard_opted_out", false)) }
    var showLeave by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var cached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf(emptyList<LeaderboardStudent>()) }
    var listenerRevision by remember { mutableIntStateOf(0) }
    val syncMessage by repository.syncMessage.collectAsState()
    val metric = if (quiz) "quizWins" else "focusMinutes"
    DisposableEffect(prefs) {
        val observer = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf("leaderboard_enabled", "leaderboard_uid", "leaderboard_opted_out")) {
                joined = prefs.getBoolean("leaderboard_enabled", false)
                uid = prefs.getString("leaderboard_uid", null)
                optedOut = prefs.getBoolean("leaderboard_opted_out", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(observer) }
    }
    LaunchedEffect(Unit) {
        try { repository.sync() }
        catch (problem: Exception) {
            if (problem is kotlinx.coroutines.CancellationException) throw problem
            error = repository.message(problem)
        }
    }
    DisposableEffect(joined, uid, metric, listenerRevision) {
        rows = emptyList(); error = null; loading = joined
        val listener = if (joined) repository.listen(metric) { result, fromCache, problem ->
            rows = result; cached = fromCache; error = problem; loading = false
        } else null
        onDispose { listener?.remove() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AppHeading("Student leaderboards", "Build a habit. Celebrate your progress.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !quiz, onClick = { quiz = false }, label = { Text("Study effort") })
                FilterChip(selected = quiz, onClick = { quiz = true }, label = { Text("Quiz wins") })
            }
            Text(if (quiz) "Ranked by quiz wins. A completed set with at least 80% correct is a win. Each set counts once per day."
                else "Ranked by completed focus minutes recorded in the app. Breaks and manual study logs do not count.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            StudyCard {
                Text(if (optedOut) "Your profile is hidden" else "Your student profile joins automatically", fontWeight = FontWeight.Bold)
                Text("Your app profile name, photo and scores appear here. No Google sign-in or join request is needed.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                if (optedOut) repository.join()
                                repository.sync(); listenerRevision++; error = null
                            } catch (problem: Exception) {
                                if (problem is kotlinx.coroutines.CancellationException) throw problem
                                error = repository.message(problem)
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) "Connecting…" else if (optedOut) "Show my profile" else "Refresh") }
                    if (joined) TextButton(enabled = !busy, onClick = { showLeave = true }) { Text("Hide my profile") }
                }
                if (!joined && !optedOut && error == null && syncMessage == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Connecting your app profile…", style = MaterialTheme.typography.bodySmall)
                }
                if (!joined && (error != null || syncMessage != null)) {
                    Text(error ?: syncMessage.orEmpty(), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (joined) {
            item {
                Text(if (cached) "Saved rankings • reconnecting" else "Live • top 100 students",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error != null || syncMessage != null) item {
                Text(error ?: syncMessage.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (!loading && error == null && rows.isEmpty()) item {
                StudyCard {
                    Text("The first place is waiting", fontWeight = FontWeight.Bold)
                    Text(if (quiz) "Finish a quiz to get on this board." else "Complete some focus time to get on this board.")
                }
            }
            if (rows.isNotEmpty()) item { LeaderboardPodium(rows.take(3), quiz, uid) }
            itemsIndexed(rows.drop(3), key = { _, student -> student.uid }) { index, student ->
                LeaderboardRow(index + 4, student, quiz, student.uid == uid)
            }
            if (rows.isNotEmpty() && rows.none { it.uid == uid }) item {
                Text("Your profile appears in this list when it reaches the top 100. Complete focus sessions and quizzes to improve your rank.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("Personal practice rankings, not verified exam results. Retrying a set on the same day still helps you practise, but adds no extra leaderboard attempt or win.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showLeave) AlertDialog(onDismissRequest = { showLeave = false }, title = { Text("Hide your leaderboard profile?") },
        text = { Text("Your name and photo will be hidden, and new activity will stay local. Your study history on this phone is kept. Connect to the internet to confirm this change.") },
        confirmButton = { TextButton(onClick = {
            showLeave = false
            scope.launch {
                busy = true
                try { repository.leave(); joined = false; uid = null; error = null }
                catch (problem: Exception) { error = repository.message(problem) }
                finally { busy = false }
            }
        }) { Text("Hide profile") } }, dismissButton = { TextButton(onClick = { showLeave = false }) { Text("Stay") } })
}

@Composable
private fun LeaderboardPodium(students: List<LeaderboardStudent>, quiz: Boolean, uid: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Top students", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Second, first, third: the winner stands at the centre of the podium.
                listOf(1, 0, 2).forEach { index ->
                    val student = students.getOrNull(index)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (student != null) {
                            if (index == 0) Text("★", color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.headlineMedium)
                            LeaderboardAvatar(student, if (index == 0) 68.dp else 56.dp)
                            Text(student.name + if (student.uid == uid) " (you)" else "",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Surface(Modifier.fillMaxWidth().height(when (index) { 0 -> 116.dp; 1 -> 88.dp; else -> 72.dp }),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center) {
                                    Text("#${index + 1}", style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold)
                                    Text(if (quiz) "${student.quizWins} wins"
                                        else "${student.focusMinutes / 60}h ${student.focusMinutes % 60}m",
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, student: LeaderboardStudent, quiz: Boolean, own: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = if (own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("#$rank", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
            LeaderboardAvatar(student)
            Column(Modifier.weight(1f)) {
                Text(student.name + if (own) " (you)" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                if (quiz) Text("${student.quizAttempts} attempts • ${student.quizCorrect}/${student.quizQuestions} correct",
                    style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (quiz) student.quizWins.toString() else "${student.focusMinutes / 60}h ${student.focusMinutes % 60}m", fontWeight = FontWeight.Bold)
                Text(if (quiz) "wins" else "focus", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LeaderboardAvatar(student: LeaderboardStudent, size: Dp = 44.dp) {
    val bitmap = remember(student.photo) {
        runCatching {
            if (student.photo.length > 12000) null else Base64.decode(student.photo, Base64.NO_WRAP).let { bytes ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..128 || bounds.outHeight !in 1..128) null
                else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "${student.name}'s profile photo",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(student.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
    }
}
