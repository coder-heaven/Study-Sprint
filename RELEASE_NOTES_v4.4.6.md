# Study Sprint v4.4.6

## Changes

- YouTube's row opens the daily-limit editor, including custom minutes, Off and selected weekdays. The existing 10-minute default remains until changed.
- YouTube runs normally before its configured daily limit. After reaching that limit, users can explicitly start two five-minute extra-time bypasses per local calendar day.
- Reopening YouTube does not restart a bypass. Windows keep running when leaving the app, expire at midnight, and cannot override an active focus lock.
- Old pre-limit session counters do not consume the new extra-time bypasses on upgrade.
- The focus countdown ring is larger (up to 300 dp, previously 160 dp), with larger text that scales to the available width.

## Verification / device smoke checks

1. Enable Usage Access and Study Sprint accessibility blocking. Search for YouTube in App Limits, select 15 minutes and save. Reopen the editor and confirm the saved value.
2. Open YouTube below 15 minutes: it must work without consuming a bypass. Its extra-time button must remain disabled.
3. Reach 15 minutes: YouTube must be blocked. Start a five-minute bypass and confirm YouTube opens. Leave and return: the same window continues without resetting.
4. Let the window expire, use the second bypass, and let it expire. A third bypass must be unavailable.
5. Start a focus session with blocking enabled: YouTube must remain blocked even if a bypass was active. At local midnight, daily usage and both bypasses reset.
6. Test Off, a non-selected weekday, increasing/decreasing the limit, and missing Usage Access. Check that focus locks still take priority.
7. Check Focus in portrait/landscape and with larger system fonts; countdown and controls must remain accessible by scrolling.

Unit regression tests cover post-limit eligibility, exact thresholds, disabled days, two-window exhaustion, reopening, clock changes and midnight resets. Instrumented tests cover editing YouTube's limit and enforcement, plus existing navigation checks. GitHub Actions completed the signed build, unit tests, release lint, Android instrumented tests on the API 29 emulator, and Firebase rules tests successfully. The manual real-device smoke checks above remain recommended.

## Published release verification

[Release workflow 37020988560](https://github.com/coder-heaven/Study-Sprint/actions/runs/37020988560) passed all four jobs: build, leaderboard-rules, navigation-ui and publish. GitHub confirms v4.4.6 as latest. The downloaded release APK matches its published SHA-256:

`5ee8cd34cce542430db1e601fa39cee7fbe3cd0d36b3da4574194eaf3f54f429`

Release lint exposed a pre-existing internal alert receiver registration issue. The release includes a fix using `ContextCompat.registerReceiver` with `RECEIVER_NOT_EXPORTED` across Android versions.

## Initial local verification (before GitHub authentication)

- Passed: 15 JVM tests (`YouTubeQuotaTest` and `DailyLimitsTest`), compiled with the standalone Kotlin compiler and run with JUnit 4.13.2. This is not a full Android/Gradle test run.
- Passed: `git diff --check`; workflow YAML parsing and embedded Bash syntax checks.
- Blocked: `bash ./gradlew --no-daemon testDebugUnitTest assembleRelease` could not reserve its configured 3 GB heap. Retrying with `-Dorg.gradle.jvmargs=-Xmx768m -Dorg.gradle.workers.max=1` and `lintRelease` reached Android configuration but failed with `SDK location not found`.
- Not run: Android instrumented tests and full application compilation/lint. No signed APK has been produced locally.
- The initial push check failed because GitHub credentials were unavailable. Authentication was subsequently configured; both code commits were pushed and the signed APK was built and published by GitHub Actions.

## Release process

Run **Build signed APK** on `main` with **publish_release** enabled. Publishing waits for the signed build, unit tests, release lint, Firestore rules tests and Android instrumented tests, then attaches `Study-Sprint-v4.4.6.apk` and its SHA-256 checksum and marks the release latest. It uses the existing GitHub signing/Firebase secrets and does not replace the signing key. An existing tag/release is not overwritten.
