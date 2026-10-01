package com.pranav.study.cet_study_sprint

import android.content.Context
import android.content.Intent

internal object BlockNavigation {
    fun studyIntent(context: Context, route: String): Intent =
        Intent(context, ComposeStudyActivity::class.java)
            .setAction(AlertNavigation.ACTION)
            .putExtra(AlertNavigation.ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun blockedIntent(context: Context, pkg: String, reason: String, limit: Int): Intent =
        Intent(context, BlockedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("package", pkg).putExtra("reason", reason).putExtra("limit_minutes", limit)
}
