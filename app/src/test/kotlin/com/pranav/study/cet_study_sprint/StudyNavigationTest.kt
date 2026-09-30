package com.pranav.study.cet_study_sprint

import org.junit.Assert.*
import org.junit.Test

class StudyNavigationTest {
    @Test fun fiveTabsKeepDailyActionsVisible() {
        assertEquals(listOf("Today", "Study", "Focus", "Plan", "Progress"), StudyNavigation.tabs.map { it.label })
        assertEquals(5, StudyNavigation.tabs.map { it.route }.distinct().size)
    }
    @Test fun subScreensKeepTheirParentTabSelected() {
        assertEquals("study", StudyNavigation.tabFor("mcq_editor"))
        assertEquals("study", StudyNavigation.tabFor("syllabus"))
        assertEquals("focus", StudyNavigation.tabFor("limits"))
        assertEquals("statistics", StudyNavigation.tabFor("leaderboard"))
        assertNull(StudyNavigation.tabFor("settings"))
    }
}
