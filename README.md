# 📚 Study Sprint

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Authentication-FFCA28?logo=firebase&logoColor=black)

> 🎯 A native Android study companion for MHT-CET, JEE, NEET and HSC/NCERT preparation.

## ✨ Features

- 🎓 Class 11 and Class 12 profiles with CET, JEE and NEET paths
- 📖 Syllabus tracking with selectable chapter progress
- 🧠 Concept, critical-thinking and competency MCQ practice
- 📷 Personal 10-MCQ editor and 📘 Arihant practice logging
- 🗓️ Daily planner, reminders and a ✍️ “What I learned today” journal
- ⏱️ Custom Pomodoro timer and focus history
- 📊 Study statistics and app-usage insights
- 🔒 Daily app limits and focus blocking
- 👤 Editable student profile and exam date
- 🔐 Google sign-in backed by Firebase Authentication

## 🛠️ Requirements

- 💻 Android Studio with JDK 17
- 🤖 Android SDK 35
- 🔥 A Firebase Android app using package name `com.pranav.study.cet_study_sprint`

## 🔥 Firebase setup

1. Create or select an Android app in Firebase Console using package name `com.pranav.study.cet_study_sprint`.
2. Add the SHA-1 fingerprint for your signing key.
3. Enable **Authentication → Sign-in method → Google**.
4. Download the updated `google-services.json`.
5. Place it at `app/google-services.json`.

> 🛡️ The real Firebase configuration is intentionally ignored by Git. `app/google-services.example.json` documents the expected location and structure.

## 🚀 Build

From the repository root on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

Build the current test release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

📦 Build outputs are written under `build/` and are not committed.

## 🔑 Permissions

App limits and focus blocking require the user to explicitly enable Android **Usage Access** and **Accessibility** permissions. Study data is stored locally on the device.

## 🔒 Security note

The release build currently uses the debug signing key for local testing. Configure a private release keystore before publishing to Google Play, and never commit the keystore or its passwords.

## 📄 License

Released under the [MIT License](LICENSE).