# Study Sprint

Study Sprint is a native Android study companion built with Kotlin and Jetpack Compose for MHT-CET, JEE, NEET and HSC/NCERT preparation.

## Features

- Class 11 and Class 12 profiles with CET, JEE and NEET paths
- Syllabus tracking and progress selection
- Concept, critical-thinking and competency MCQ practice
- Personal 10-MCQ editor and Arihant practice logging
- Daily planner, reminders and a "What I learned today" journal
- Custom Pomodoro/focus timer and focus history
- App usage statistics, daily limits and focus blocking
- Editable profile and exam date
- Google sign-in backed by Firebase Authentication

## Requirements

- Android Studio with JDK 17
- Android SDK 35
- A Firebase Android app using package name `com.pranav.study.cet_study_sprint`

## Firebase setup

1. In Firebase Console, create or select an Android app with package name `com.pranav.study.cet_study_sprint`.
2. Add the SHA-1 fingerprint for the signing key you use.
3. Enable **Authentication > Sign-in method > Google**.
4. Download the updated `google-services.json`.
5. Place it at `app/google-services.json`.

The real Firebase configuration is intentionally ignored by Git. `app/google-services.example.json` documents the expected location and shape.

## Build

From the repository root on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

For the current test release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

Build outputs are written under `build/` and are not committed.

## Permissions

App limits and focus blocking require the user to explicitly enable Android Usage Access and Accessibility permissions. Study data is stored locally on the device.

## Security note

The release build currently uses the debug signing key for local testing. Configure a private release keystore before publishing to Google Play, and never commit that keystore or its passwords.