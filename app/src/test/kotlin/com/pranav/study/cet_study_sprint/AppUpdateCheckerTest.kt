package com.pranav.study.cet_study_sprint

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test fun comparesSemanticVersions() {
        assertTrue(AppUpdateChecker.isNewer("1.1.1", "1.1.0"))
        assertTrue(AppUpdateChecker.isNewer("v1.10.0", "1.9.9"))
        assertFalse(AppUpdateChecker.isNewer("1.1.0", "1.1.0"))
        assertFalse(AppUpdateChecker.isNewer("1.0.9", "1.1.0"))
    }
}
