package com.pranav.study.cet_study_sprint

internal data class MarkingScheme(
    val correct: Int,
    val incorrect: Int
) {
    fun score(isCorrect: Boolean): Int = if (isCorrect) correct else incorrect

    fun label(): String = "+$correct correct, ${if (incorrect > 0) "+" else ""}$incorrect wrong"
}

internal fun markingSchemeFor(course: String): MarkingScheme =
    if (course.trim().uppercase() == "CET") {
        MarkingScheme(correct = 1, incorrect = 0)
    } else {
        MarkingScheme(correct = 4, incorrect = -1)
    }
