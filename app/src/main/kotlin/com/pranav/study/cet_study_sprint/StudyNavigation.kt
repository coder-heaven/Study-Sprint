package com.pranav.study.cet_study_sprint

internal data class StudyTab(val route: String, val label: String)

internal object StudyNavigation {
    val tabs = listOf(StudyTab("home", "Today"), StudyTab("study", "Study"),
        StudyTab("focus", "Focus"), StudyTab("plan", "Plan"), StudyTab("statistics", "Progress"))

    fun tabFor(route: String): String? = when (route) {
        "home" -> "home"
        "study", "syllabus", "practice", "arihant", "mcq_editor", "my_quiz", "notes" -> "study"
        "focus", "limits" -> "focus"
        "plan" -> "plan"
        "statistics", "leaderboard", "study_history", "focus_history", "app_usage" -> "statistics"
        else -> null
    }
}
