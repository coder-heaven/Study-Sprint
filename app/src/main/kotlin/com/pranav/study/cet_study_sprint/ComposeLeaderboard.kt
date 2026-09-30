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
import kotlinx.coroutines.launch

@Composable
internal fun LeaderboardScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val repository = remember { Leaderboards.repository(context) }
    val scope = rememberCoroutineScope()
    var joined by remember { mutableStateOf(prefs.getBoolean("leaderboard_enabled", false)) }
    var uid by remember { mutableStateOf(prefs.getString("leaderboard_uid", null)) }
    var quiz by rememberSaveable { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var cached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf(emptyList<LeaderboardStudent>()) }
    var listenerRevision by remember { mutableIntStateOf(0) }
    val syncMessage by repository.syncMessage.collectAsState()
    val metric = if (quiz) "quizWins" else "focusMinutes"
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
                else "Ranked by focus minutes recorded since joining. Breaks and manual study logs do not count.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            StudyCard {
                Text(if (joined) "You’re on the leaderboard" else "Join with your student profile", fontWeight = FontWeight.Bold)
                Text(if (joined) "Your new completed focus time and quizzes sync automatically."
                    else "Google sign-in is optional. Local profiles can participate too. Your name, chosen photo and scores will be visible to participating students.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (joined) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                try { repository.sync(); listenerRevision++ } catch (problem: Exception) { error = repository.message(problem) }
                                finally { busy = false }
                            }
                        }) { Text(if (busy) "Syncing…" else "Refresh") }
                        TextButton(enabled = !busy, onClick = { showLeave = true }) { Text("Leave leaderboard") }
                    }
                } else Button(enabled = !busy, onClick = { showJoin = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Connecting…" else "Join leaderboard")
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
            itemsIndexed(rows, key = { _, student -> student.uid }) { index, student ->
                LeaderboardRow(index + 1, student, quiz, student.uid == uid)
            }
            if (rows.isNotEmpty() && rows.none { it.uid == uid }) item {
                Text("Your profile appears in this list when it reaches the top 100. Results start counting after you join.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("Personal practice rankings, not verified exam results. Retrying a set on the same day still helps you practise, but adds no extra leaderboard attempt or win.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showJoin) AlertDialog(onDismissRequest = { showJoin = false }, title = { Text("Share your student profile?") },
        text = { Text("Your profile name, photo, focus minutes and quiz results will be visible on the leaderboard. Email, notes and PDFs stay private. Only activity after joining counts. You can leave at any time.") },
        confirmButton = { TextButton(onClick = {
            showJoin = false
            scope.launch {
                busy = true
                try { repository.join(); joined = true; uid = prefs.getString("leaderboard_uid", null); error = null }
                catch (problem: Exception) { error = repository.message(problem) }
                finally { busy = false }
            }
        }) { Text("Share and join") } }, dismissButton = { TextButton(onClick = { showJoin = false }) { Text("Cancel") } })
    // Join failures must also be visible before a listener exists.
    if (!joined && error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("Leaderboard connection") },
        text = { Text(error.orEmpty()) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
    if (showLeave) AlertDialog(onDismissRequest = { showLeave = false }, title = { Text("Leave the leaderboard?") },
        text = { Text("Your name and photo will be hidden, and new activity will stay local. Your study history on this phone is kept. Connect to the internet to confirm this change.") },
        confirmButton = { TextButton(onClick = {
            showLeave = false
            scope.launch {
                busy = true
                try { repository.leave(); joined = false; uid = null; error = null }
                catch (problem: Exception) { error = repository.message(problem) }
                finally { busy = false }
            }
        }) { Text("Leave") } }, dismissButton = { TextButton(onClick = { showLeave = false }) { Text("Stay") } })
}

@Composable
private fun LeaderboardRow(rank: Int, student: LeaderboardStudent, quiz: Boolean, own: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = if (own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("#$rank", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
            LeaderboardAvatar(student)
            Column(Modifier.weight(1f)) {
                Text(student.name + if (own) " (you)" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(if (student.accountType == "google") "Google profile" else "Local profile", style = MaterialTheme.typography.labelSmall)
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
private fun LeaderboardAvatar(student: LeaderboardStudent) {
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
    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "${student.name}'s profile photo",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(student.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
    }
}
