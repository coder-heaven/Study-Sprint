package com.pranav.study.cet_study_sprint

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun StudyStreakCard(streak: Int, modifier: Modifier = Modifier) {
    val count = streak.coerceAtLeast(0)
    val completed = count.coerceAtMost(7)
    StudyCard(modifier.testTag("study_streak_card")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.art_streak_flame_3d), null, Modifier.size(56.dp).alpha(if (count > 0) 1f else .45f))
            Column(Modifier.weight(1f)) {
                Text("$count-day study streak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (count >= 7) "7-day milestone reached! Keep your flame alive."
                     else "Build your 7-day streak · $completed/7 days", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).forEach { day ->
                val active = day <= completed
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                        Image(painterResource(R.drawable.art_streak_flame_3d),
                            "Streak day $day: ${if (active) "complete" else "not reached"}",
                            Modifier.padding(5.dp).size(24.dp).alpha(if (active) 1f else .18f).testTag("streak_day_$day"))
                    }
                    Text("$day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Study daily: focus for 5+ minutes, finish a task, or practise questions.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
