package com.pranav.study.cet_study_sprint

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager

object Protection {
    private val fixed = setOf(
        "android", "com.android.settings", "com.android.systemui", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.phone", "com.android.server.telecom",
        "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui",
        "com.android.emergency", "com.google.android.emergency"
    )

    fun packages(context: Context): Set<String> {
        val result = fixed.toMutableSet()
        result += context.packageName
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName?.let(result::add)
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(result::add)
        return result
    }
}

