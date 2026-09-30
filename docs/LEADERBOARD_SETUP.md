# Live leaderboards (Study Sprint 2.4.0)

## One-time Firebase activation

Use the **same Firebase project** as the existing `GOOGLE_SERVICES_BASE64` GitHub secret. This change does not require a replacement JSON file or a new signing key.

1. Open [Firebase Console](https://console.firebase.google.com/) on your phone (Desktop site may help).
2. In **Authentication → Sign-in method**, keep Google enabled and enable **Anonymous**. Anonymous authentication lets local profiles join without a Google account.
3. In **Build → Firestore Database**, create the default database in **production mode** if it does not exist. Pick an appropriate India region before creating it; a database's location cannot be changed later.
4. Open **Firestore → Rules**, paste the contents of [`firebase/firestore.rules`](../firebase/firestore.rules), then **Publish**. These rules deny unrelated collections. If the project already serves other apps or collections, merge the `leaderboardStudents` match and helper functions into the existing rules instead of replacing those rules.
5. In **Firestore → Indexes → Composite**, create both indexes, with collection scope:

   | Collection | First field | Second field | Third field |
   |---|---|---|---|
   | `leaderboardStudents` | `visible`: Ascending | `focusMinutes`: Descending | — |
   | `leaderboardStudents` | `visible`: Ascending | `quizWins`: Descending | `quizAttempts`: Descending |

6. Wait until both indexes show **Enabled**. Install the updated signed APK and finish the app profile setup. Open **Menu → Leaderboards**; your profile connects automatically.
7. With two phones/profiles, complete a focus session and a quiz. Confirm the second phone receives the changed rankings without refreshing. Turn one phone offline, complete another session, reconnect and reopen the app, and confirm the result is counted once.

If you have Firebase CLI access, the rules and indexes can instead be deployed using:

```sh
firebase deploy --only firestore:rules,firestore:indexes --project YOUR_EXISTING_PROJECT_ID
```

**The app cannot enable these console settings or deploy rules by itself.** It shows a connection/setup error if the backend is not ready. The signed APK includes real Firestore listeners; there are no fake student rankings.

## What students see

- **Study effort:** top 100 visible profiles ordered by recorded focus minutes, accumulated since joining. Breaks and manual study logs are excluded. Time actually spent before stopping a focus timer counts.
- **Quiz wins:** top 100 visible profiles ordered by wins, then completed attempts, showing attempts and correct answers. A win means at least 80% correct in a completed in-app set. This is a personal practice result, not a head-to-head match.
- The first completion of a particular question set per India calendar day counts. Replays remain in local study history but do not increase leaderboard totals.
- Google and local profiles appear on the same boards. The app uploads a small thumbnail of the chosen local photo, or the Google photo when no local photo is set. Missing photos use initials.
- Completed app profiles automatically share their name, app photo and totals; students can hide their profile at any time. Emails, notes, PDFs and question content are never uploaded by this feature.
- Leaving hides the public row and clears its name/photo after the server confirms the change. Google and local users can rejoin the same UID and retain their cloud totals while their Firebase identity is retained. Uninstalling or clearing app data can lose an anonymous identity; link to Google for recovery across devices.
- Linking a new Google account to an anonymous profile preserves its UID and results. If that Google account already belongs to another Firebase user, the app asks the student to leave the local board before switching. It does not claim to merge two established accounts.
- Signing out of Google reconnects through a local app profile. Hide your profile first if you want to remove the previous public ranking.

## Storage and reliability

Local SQLite schema 4 adds an outbox and quiz completion IDs without deleting existing history, notes or PDFs. Completion recording and outbox insertion occur in one local transaction. Cloud transactions create an immutable event and add its delta to a student's totals together. Retrying an acknowledged event cannot add the score again. Multiple devices signed into the same Firebase UID contribute to the same totals.

Sync runs while the app is visible, checks pending events every 15 seconds, and resumes on the next launch. Offline results are durable; they are not uploaded until a connection returns. Rankings mark cached data as saved/reconnecting instead of calling it live. Activity earned before an authenticated leaderboard identity is available is not retroactively published.

Rules restrict students to their own records, validate score deltas and the 80% win threshold, and require atomic event/totals writes. These are **self-reported practice rankings**: a modified client could still submit invented focus sessions or answers. Do not use them for prizes or official exams without adding server-issued quiz/session verification, moderation and anti-abuse controls.

The feature uses Firebase Auth and Firestore, with inline thumbnails instead of Cloud Storage. Review Firebase usage quotas as participation grows. Nothing in this change deploys Firebase settings or enables a paid plan.

## Verification

The GitHub build runs Android unit tests, builds a signed release APK, and tests the rules in a Firestore emulator. Rules tests cover public/private reads, ownership, forbidden private fields, event duplication, threshold validation, inflated totals and leaving.

```sh
cd firebase
npm install
npx firebase emulators:exec --project demo-study-sprint --only firestore 'npm test' --config ../firebase.json
```

## Automatic app profiles

Completed app profiles now connect automatically using Firebase Anonymous authentication when Google is not used. There is no Share and join dialog or approval request. The board uses the same profile photo loader as the app avatar; a chosen app photo always takes priority. Users can hide their public profile; this choice is preserved on restart. Existing scores and the one-set-per-day scoring rule are retained. New activity queues after an authenticated identity is available, including during temporary Firestore outages. First-time offline users connect when internet access returns.
