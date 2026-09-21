package com.pranav.study.cet_study_sprint

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

internal data class StatisticsState(
    val days: Int = 7,
    val totals: StudyTotals = StudyTotals(),
    val subjects: Map<String, Long> = emptyMap(),
    val streak: Int = 0,
    val limitEvents: Map<String, Int> = emptyMap()
)

internal class StatisticsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StudyData.events(app)
    private val selectedDays = MutableStateFlow(7)
    val state = combine(selectedDays, store.revision) { days, _ -> days }
        .map { days ->
            withContext(Dispatchers.IO) {
                StatisticsState(days, store.totals(days), store.subjectMinutes(days),
                    store.streak(), store.limitCounts(days))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsState())
    fun selectDays(days: Int) {
        if (days in setOf(1, 7, 30)) selectedDays.value = days
    }
}
