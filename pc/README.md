# Study Sprint PC

Study Sprint PC is the installable browser/PWA companion for Windows, macOS and Linux. It is intentionally local-first: the guest profile, syllabus progress, tasks, notes, focus sessions and practice history stay on the device unless the user exports a backup.

## Included in the first PC version

- MHT-CET, JEE Main and NEET UG profiles with Class 11/Class 12 chapter plans.
- First-run local login/profile page with guest mode, exam/class selection and local-data consent.
- Syllabus progress, editable planner tasks, dated notes, a persistent focus timer with breaks, progress history and original chemistry practice MCQs.
- LaTeX-style chemistry notation, backup export/import and offline loading after the first visit.
- Install from the browser as a standalone app. The service worker shows a reload banner when a future PC release is ready.

## Deliberate differences from Android

This is not a claim of Android feature parity. The PC guest profile is local, not Google/Firebase authentication. Android Accessibility app blocking, device usage access, Firebase leaderboard identity, protected App Check chat and Android PDF/file APIs are not enabled in the PC companion. PC Focus has an explicitly labelled soft mode; a browser or desktop app cannot block or force-close other Windows programs. Do not put provider keys in this app.

## Local development

From the repository root:

```sh
npm ci --prefix pc
npm test --prefix pc
npm run sync --prefix pc -- --check
npm run build --prefix pc
npm start --prefix pc
```

Open `http://127.0.0.1:4173`. Browser install prompts and service workers require HTTPS in deployment, but work on localhost.

`pc/data.mjs` is generated from the Android `versionName`, `SyllabusData.kt` and `QuizScoring.kt`. Run `npm run sync --prefix pc` after Android syllabus or scoring changes; the PC test fails if the generated data is stale.

The Windows desktop wrapper lives in `desktop/`. It packages the same tested PC bundle as an x64 NSIS installer and portable executable. See `desktop/README.md` for local packaging and the Windows CI workflow.

## Deployment

`.github/workflows/deploy-pc.yml` builds/tests the PWA and publishes `pc/dist` to GitHub Pages on every `main` change that touches `pc/**` or the Android syllabus/version sources. Enable **Settings → Pages → Source: GitHub Actions** once in the repository. The resulting URL is:

`https://coder-heaven.github.io/Study-Sprint/`

Future PC changes automatically replace the deployed PWA. A user-installed PWA receives the new service worker and displays a **Reload** update banner. This deployment is separate from the Android APK release workflow.
