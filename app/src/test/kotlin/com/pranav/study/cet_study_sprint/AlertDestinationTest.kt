package com.pranav.study.cet_study_sprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertDestinationTest {
    @Test fun eachReminderOpensItsOwnScreen() {
        assertEquals("focus", AlertDestination.reminder("study"))
        assertEquals("plan", AlertDestination.reminder("plan"))
        assertNull(AlertDestination.reminder("unknown"))
    }
    @Test fun onlyAlertDestinationsAreAccepted() {
        assertEquals("focus", AlertDestination.valid("focus"))
        assertEquals("plan", AlertDestination.valid("plan"))
        assertEquals("limits", AlertDestination.valid("limits"))
        assertNull(AlertDestination.valid("profile"))
        assertNull(AlertDestination.valid(null))
    }
}
