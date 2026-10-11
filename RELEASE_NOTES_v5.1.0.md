# Study Sprint v5.1.0

## Safer in-app updates

- Declining **Allow from this source** no longer leaves the update flow in an error state.
- The verified APK remains available, and the Settings card explains how to enable the permission and retry.
- Installer cancellation, unavailable installer apps and Android security restrictions now receive a recoverable status message.

## Home-screen widgets

- Added a Focus widget with the current timer, focus task and a shortcut back to the focus screen.
- Added a Progress widget with today’s focused minutes, daily goal, next task and active app-limit count.
- Widgets use the existing on-device study data and refresh when the app, timer or widget is opened.

## Protected-app recovery

- Reworked the limit screen shown over a blocked app with clear daily-limit and focus-protection states.
- Focus blocks show the remaining protected time and a direct **Open focus timer** action.
- Daily limits provide a clear **Regain 5 minutes** action where the existing allowance policy permits it.
- Protected focus still cannot be bypassed; finishing or ending the session in the focus timer restores access.
