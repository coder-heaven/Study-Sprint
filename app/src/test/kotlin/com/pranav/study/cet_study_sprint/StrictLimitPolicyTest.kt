package com.pranav.study.cet_study_sprint

import org.junit.Assert.*
import org.junit.Test

class StrictLimitPolicyTest {
    @Test fun weakeningAnExistingLimitWaitsUntilTomorrow() {
        assertTrue(StrictLimitPolicy.mustDefer(15, 127, 30, 127, false, false))
        assertTrue(StrictLimitPolicy.mustDefer(15, 127, 0, 127, false, false))
        assertTrue(StrictLimitPolicy.mustDefer(15, 127, 15, 126, false, false))
    }
    @Test fun newAndTighterLimitsApplyImmediately() {
        assertFalse(StrictLimitPolicy.mustDefer(0, 127, 15, 127, false, false))
        assertFalse(StrictLimitPolicy.mustDefer(15, 127, 5, 127, false, false))
        assertFalse(StrictLimitPolicy.mustDefer(15, 31, 15, 127, false, false))
    }
    @Test fun focusBlockCannotBeRemovedWhileActive() {
        assertTrue(StrictLimitPolicy.mustDefer(0, 127, 0, 127, true, false))
        assertFalse(StrictLimitPolicy.mustDefer(15, 127, 15, 127, true, true))
    }
}
