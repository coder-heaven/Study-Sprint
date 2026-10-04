package com.pranav.study.cet_study_sprint

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class StudyApplication : Application() {
    internal val studyChat by lazy { StudyChatViewModel(this) }
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)?.let {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }
}
