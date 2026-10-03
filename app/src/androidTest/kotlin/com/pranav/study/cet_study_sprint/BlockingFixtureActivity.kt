package com.pranav.study.cet_study_sprint

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Separate test APK package: exercises the real Android app-switch/usage pipeline. */
class BlockingFixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Distraction fixture"; textSize = 28f })
    }
}
