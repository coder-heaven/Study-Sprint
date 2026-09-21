package com.pranav.study.cet_study_sprint

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun WeeklyStudyChart(minutes: List<Long>) {
    val values = List(7) { minutes.getOrElse(it) { 0L } }
    val peak = values.maxOrNull()?.coerceAtLeast(1) ?: 1L
    Row(Modifier.fillMaxWidth().height(116.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { index, value ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth(0.56f).height((6 + 78 * value / peak).toInt().dp)
                        .background(if (value > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)))
                }
                Spacer(Modifier.height(5.dp))
                Text(LocalDate.now().minusDays((6 - index).toLong()).dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
