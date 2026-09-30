# Leaderboard verification — 30 September 2026

- Android `testDebugUnitTest`: **14 tests passed, 0 failures, 0 errors** (includes four leaderboard scoring/date tests and the existing app tests).
- Android `assembleRelease`: **BUILD SUCCESSFUL**, including release Kotlin compilation and vital lint.
- Firestore emulator: **7 security rules tests passed**. Checked profile ownership, authenticated reads, hidden profiles, prohibited private fields, atomic score deltas, duplicate events, the win threshold and leaving.
- Configuration JSON parsing, SQLite table creation/outbox uniqueness, and `git diff --check`: passed.

The local compile used the checked-in example Firebase configuration with a synthetic OAuth client ID added only to the ignored local `app/google-services.json`. The release APK produced here is unsigned and uses that test configuration; it is not a production update for students. No private configuration or signing key is included in the source package.

The existing GitHub workflow is prepared to run the same app tests and produce a signed APK using the repository's existing private secrets. Its new rules job uses an isolated `demo-study-sprint` emulator project and does not deploy production settings.

GitHub's connected integration rejected both branch creation and blob creation with HTTP 403, “Resource not accessible by integration.” The code has therefore been committed locally and packaged, but has not been uploaded to GitHub or released.

Production Firebase activation and a two-device live/offline check remain required; see [setup instructions](LEADERBOARD_SETUP.md). No device UI test or production leaderboard connection was claimed by these checks.
