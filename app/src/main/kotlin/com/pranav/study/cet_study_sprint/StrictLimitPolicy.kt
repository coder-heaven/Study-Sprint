package com.pranav.study.cet_study_sprint

internal object StrictLimitPolicy {
    fun mustDefer(oldMinutes: Int, oldDays: Int, newMinutes: Int, newDays: Int,
                  focusLocked: Boolean, newFocus: Boolean): Boolean =
        (oldMinutes > 0 && (newMinutes == 0 || newMinutes > oldMinutes || (newDays and oldDays) != oldDays)) ||
            (focusLocked && !newFocus)
}
