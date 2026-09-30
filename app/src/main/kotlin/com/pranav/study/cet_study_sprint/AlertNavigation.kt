package com.pranav.study.cet_study_sprint

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal object AlertNavigation {
    const val ACTION = "com.pranav.study.cet_study_sprint.OPEN_ALERT"
    const val ROUTE = "alert_route"
    const val NOTIFICATION_ID = "alert_notification_id"

    fun intent(context: Context, route: String, notificationId: Int): Intent =
        Intent(context, ComposeStudyActivity::class.java).setAction(ACTION)
            .putExtra(ROUTE, route).putExtra(NOTIFICATION_ID, notificationId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun pending(context: Context, route: String, notificationId: Int): PendingIntent =
        PendingIntent.getActivity(context, notificationId, intent(context, route, notificationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    // Only an already-visible activity receives this; never launch a background activity.
    fun foreground(context: Context, route: String) {
        context.sendBroadcast(Intent(ACTION).setPackage(context.packageName).putExtra(ROUTE, route))
    }
}
