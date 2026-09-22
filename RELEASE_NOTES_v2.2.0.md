# Study Sprint v2.2.0

This release fixes the imported-PDF quiz flow, adds exam-specific marking, and improves update discovery.

## Fixed

- Imported 10-MCQ PDFs can now be saved and opened directly with **Save & start imported quiz**.
- The MCQ editor brings each new question into view when **Next** is tapped.
- The quiz returns to the top when moving to the next question, preventing the new question from appearing off-screen.
- Correct-answer statistics are recorded separately from exam marks.

## Exam marking

- **MHT-CET:** +1 for a correct answer and 0 for a wrong answer.
- **JEE:** +4 for a correct answer and -1 for a wrong answer.
- **NEET:** +4 for a correct answer and -1 for a wrong answer.
- The selected course in the user's profile automatically controls the marking scheme.

## Updates

- Study Sprint now checks the latest published GitHub Release on every cold app start.
- When a newer semantic version is available, an update dialog appears with a direct APK download button.
- Updates can also be checked manually from **Settings → App updates**.

## Installation

Download `Study-Sprint-v2.2.0.apk` from this release and install it over the existing Study Sprint app. Local study data is retained when the APK uses the same application ID and signing key.

## Verification

- Unit tests passed.
- Android release build passed.
- Android lint completed with no errors.
- APK SHA-256: `F6F73371E85D4881E5065A45D9460F51AD9821C9C2D98FE55D9FF84AB312E0D3`
