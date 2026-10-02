# Study Sprint v4.5.0

## Home and help

- Home now has direct buttons for App limits, Focus timer, Study plan, Study & syllabus, Practice quizzes, Create/import MCQs, Notes & PDFs, Progress, Leaderboard, App usage, Profile, Settings, Setup and Tutorial.
- A reusable eight-step tutorial explains the main features, YouTube's two post-limit five-minute bypasses, optional permissions, leaderboard privacy and safe app updates.
- Setup and Tutorial can be reopened from Home, Settings, the navigation drawer and Search.

## First-time setup

- New users review Terms of Use and Privacy Policy and explicitly check an unchecked acceptance box before continuing to profile setup. Acceptance is recorded locally.
- Google sign-in stays optional. Public leaderboard sharing remains a separate explicit consent; accepting Terms never enables it.
- New profiles proceed to one settings checklist covering profile links, focus goals/preferences, reminders, theme, privacy and Android permissions.
- Usage Access, accessibility blocking, notifications and precise alarms are user-controlled and optional. The app cannot silently enable Android permissions.
- Unfinished setup/tutorial state survives restarting the app. Settings save when changed, and users can keep defaults and change them later.

## Existing data and updates

- Existing v2/v3 profiles go directly to the app instead of repeating first-time setup.
- The package name, signing configuration, preferences, database name/version and file locations are unchanged. No startup clear/reset or destructive migration was added.
- Install the newer official APK over the existing app to retain study progress, notes, plans, history, questions and settings. Do not uninstall or clear app data. Google sign-in is not a complete study-data backup; Android backup is device-dependent.

## Checks

New tests cover first-run terms acceptance, optional setup, tutorial completion and replay, Home navigation, interrupted onboarding, existing-profile preservation and legacy-profile handling. GitHub Actions runs unit tests, release lint, signed APK assembly, Firebase rules tests and Android instrumented UI tests before release publication.

Manual checks recommended on a physical phone: inspect large-font Home buttons; deny optional permissions and continue; grant permissions in Settings and verify refreshed status; install over v4.4.6 without uninstalling and confirm existing personal study data remains visible.
