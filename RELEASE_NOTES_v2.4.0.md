# Study Sprint 2.4.0

- New live leaderboards for recorded focus time and completed quiz wins.
- Local and Google profiles share the same boards, with profile photos or initials.
- Optional participation: choose whether to publish your profile and results.
- Quiz attempts and correct answers are shown; 80% or more earns a practice win.
- Offline result queue and duplicate protection; one eligible attempt per set per India day.
- Google account linking preserves an existing anonymous leaderboard identity.
- Quiz completion survives rotation and avoids duplicate history entries.
- Existing study history, notes and PDFs are retained.

Owner activation is required: enable Firebase Anonymous authentication, publish the included Firestore rules, and create the two indexes. See [setup instructions](docs/LEADERBOARD_SETUP.md).
