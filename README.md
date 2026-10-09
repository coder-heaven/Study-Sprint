# Study Sprint

Native Android study companion for CET, JEE and NEET students, built with Kotlin and Jetpack Compose.

## v4.9.0 — Study buddy and floating navigation

Charcoal surfaces, emerald accents, Poppins typography and original artwork from the supplied [FocusIQ Figma reference](https://www.figma.com/design/WvokxNRgdRmYLZmKF5mNvl/). Coordinated transparent 3D timer, notebook, shield and trophy artwork brings focus, tasks, protection and progress into one visual system. The original Study Sprint logo remains in the navigation and brand screens. Home shows real focus progress and study tasks. The focus timer exposes distraction blocking and protection status directly.

App blocking checks the foreground window's package every second, including after service reconnection and while an app stays open past its limit. Protected focus covers limited apps and focus-only selections without waiting for usage queries. Blocking history failures cannot stop enforcement. If Android silently refuses the block screen, the service verifies the denied app is still foreground before returning to Home.

[Download the latest official APK](https://github.com/coder-heaven/Study-Sprint/releases/latest) · [Release notes](RELEASE_NOTES_v5.0.2.md)

Install v5.0.2 once over the existing app to enable in-app updates. After that, tap **Update now** when prompted: Study Sprint downloads and verifies the official APK, then opens Android’s installer. No browser download or file manager is needed. Android may ask you to allow updates from Study Sprint once, and always requires installation confirmation; silent automatic installation is not available to a normal GitHub-distributed app. Downloads show progress and can be cancelled/retried; full release notes scroll without the old 500-character cutoff. Do not uninstall or clear storage. After updating, reconnect **Study Sprint app limits** in Android Accessibility if necessary; **Usage Access** is required for daily limits. The app shows these settings in App Limits and on the focus screen.

## Study tools

- Syllabus progress, imported PDF MCQs, course-specific marking and practice history.
- Tasks, class notes, focus sessions, custom Pomodoro durations, study reminders and statistics.
- Editable daily app limits, selected weekdays and automatic reset at local midnight.
- YouTube's editable daily limit and two five-minute post-limit bypasses. Protected focus cannot be bypassed.
- Optional public leaderboards using the app's profile name and photo. Public sharing needs explicit permission; Google sign-in and join requests are unnecessary for guest participation after consent.
- First-run terms, setup checklist, reusable tutorial and startup update checks.

| Course | Correct answer | Wrong answer |
| --- | --- | --- |
| CET | +1 | 0 |
| JEE | +4 | −1 |
| NEET | +4 | −1 |

## Privacy

Study data stays on the device. The blocker looks up app package names; it does not traverse, store or upload screen text. Read the bundled [Privacy Policy](app/src/main/assets/privacy.md) and [Terms of Use](app/src/main/assets/terms.md). Optional leaderboard upload/removal requires internet access. [Leaderboard owner setup](docs/LEADERBOARD_SETUP.md) covers Firebase configuration; the APK does not deploy server rules.

## Verification and releases

The release workflow runs unit tests, release lint, Android instrumented UI/navigation tests, real accessibility-service blocking tests and Firebase rules tests. Actual service tests launch a separate test app and verify focus blocking without Usage Access, live daily-limit expiry, reconnection to an already open app, and access to Study Sprint during focus. Midnight accounting, pending changes, YouTube return navigation and leaderboard consent remain covered.

Publish from the main-only release workflow after all checks pass. Android 8.0 or later is required. Original Figma vector sources and the Poppins OFL license are bundled alongside the native rendering assets.

## Study buddy (v4.9.0)

Open the floating avatar at the bottom right. Text questions use Nemotron via OpenRouter, with Kimi K3 via NVIDIA as the backup. Up to four selected photos use Kimi vision directly. Render hosts the backend with Firebase identity/App Check verification and persistent quotas. Provider keys are server secrets, never APK contents or committed files. See [shared chat deployment](docs/SHARED_CHAT_SETUP.md). The backend must be activated before shared chat works. Online MCQs use Tavily Search with a private `TAVILY_API_KEY` and optional `TAVILY_API_KEY_2` on Render, restricted to selected exam websites. Incomplete source pages are used as context for clearly labelled original practice questions; photos use NVIDIA vision. No Gemini keys are used by the current backend.

The floating pill navigation raises Focus in the center. A bug icon beside Settings opens a report draft; the decorative bell has been removed. Branding and login artwork are documented in [LOGIN_ARTWORK_v4.8.0.md](docs/LOGIN_ARTWORK_v4.8.0.md).
