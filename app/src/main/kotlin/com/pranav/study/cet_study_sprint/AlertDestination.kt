package com.pranav.study.cet_study_sprint

internal object AlertDestination {
    fun reminder(kind: String): String? = when (kind) {
        "study" -> "focus"
        "plan" -> "plan"
        else -> null
    }
    fun valid(route: String?): String? = route?.takeIf { it == "focus" || it == "plan" || it == "limits" }
}
