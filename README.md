# 📚 Study Sprint v2.2.0

Study Sprint v2.2.0 improves PDF-imported MCQ practice, introduces course-specific examination marking, fixes question navigation, and adds automatic update notifications at app startup.

## ⬇️ Download

[Download Study Sprint v2.2.0 APK](https://github.com/coder-heaven/Study-Sprint/releases/download/v2.2.0/Study-Sprint-v2.2.0.apk)

## ✨ What’s new

- 📄 Fixed navigation after importing questions from a PDF.
- ▶️ Added **Save & start imported quiz** for complete 10-question PDFs.
- 🔝 Automatically scrolls to the new question after pressing **Next**.
- 🎯 Marking automatically follows the course selected in the user profile.
- 🟢 MHT-CET marking: **+1 correct, 0 wrong**.
- 🔵 JEE marking: **+4 correct, −1 wrong**.
- 🟣 NEET marking: **+4 correct, −1 wrong**.
- 📊 Exam marks and correct-answer statistics are recorded separately.
- 🔔 Checks GitHub for new releases whenever the app starts.
- 🔄 Displays an update popup when a newer version is available.
- ⬇️ Added a direct **Download & update** button.
- 🎨 Retains the blue-indigo Material 3 design and improved navy dark mode.
- 🔐 Supports Google sign-in and local guest mode.
- 📚 Includes CET, JEE and NEET study paths for Classes 11 and 12.
- ⏱️ Includes focus sessions, custom Pomodoro timing and study reminders.
- 📱 Includes app-usage tracking and configurable daily app limits.

## 📝 Examination marking

| Course | Correct answer | Wrong answer |
|:------:|:--------------:|:------------:|
| MHT-CET | +1 | 0 |
| JEE | +4 | −1 |
| NEET | +4 | −1 |

The marking scheme is selected automatically using the course saved in the student profile.

## 🤖 Create MCQs using any AI app

1. Open **Practice → Your own 10 MCQs**.
2. Tap **Open an AI App**, or choose up to five files using **Choose files and open AI**.
3. Select a compatible AI app installed on your phone.
4. Attach any additional notes or textbook files.
5. Ask the AI app to follow the copied Study Sprint prompt.
6. Export the generated questions as a text-based PDF.
7. Return to Study Sprint and tap **Import questions from PDF**.
8. Review the imported answers and tap **Save & start imported quiz**.

> PDF import works best with numbered questions, four options labelled A–D, and either inline answers or a final answer key.

## 📱 Screenshots

<table>
  <tr>
    <td align="center">
      <strong>Login</strong><br>
      <img src="https://raw.githubusercontent.com/coder-heaven/Study-Sprint/main/docs/screenshots/v1.2.0/login-dark.jpeg" width="230" alt="Study Sprint login">
    </td>
    <td align="center">
      <strong>Home</strong><br>
      <img src="https://raw.githubusercontent.com/coder-heaven/Study-Sprint/main/docs/screenshots/v1.2.0/home-dark.jpeg" width="230" alt="Study Sprint home">
    </td>
    <td align="center">
      <strong>Practice</strong><br>
      <img src="https://raw.githubusercontent.com/coder-heaven/Study-Sprint/main/docs/screenshots/v1.2.0/practice-dark.jpeg" width="230" alt="Study Sprint practice">
    </td>
  </tr>
  <tr>
    <td align="center">
      <strong>AI and PDF MCQs</strong><br>
      <img src="https://raw.githubusercontent.com/coder-heaven/Study-Sprint/main/docs/screenshots/v1.2.0/ai-mcq-dark.jpeg" width="230" alt="AI and PDF MCQs">
    </td>
    <td align="center">
      <strong>Profile</strong><br>
      <img src="https://raw.githubusercontent.com/coder-heaven/Study-Sprint/main/docs/screenshots/v1.2.0/profile-dark.jpeg" width="230" alt="Study Sprint profile">
    </td>
    <td align="center">
      <strong>Settings</strong><br>
      <img src="https://raw.githubusercontent.com/coder-heaven/Study-Sprint/main/docs/screenshots/v1.2.0/settings-dark.jpeg" width="230" alt="Study Sprint settings">
    </td>
  </tr>
</table>

## 🔄 Application updates

Study Sprint checks the latest published GitHub Release whenever the app starts.

When a newer version is available:

1. An **Update available** popup appears.
2. Tap **Download & update**.
3. Download the new APK from the official GitHub release.
4. Confirm the Android installation prompt.

Android does not permit silent APK installation, so final installation confirmation is still required.

## 🔐 Permissions and privacy

- Study data is stored locally on the device.
- Google sign-in is optional.
- Notification permission is used for study and planner reminders.
- Usage Access is required to calculate application usage and daily limits.
- Accessibility access is optional and used only when app blocking is enabled.
- Android may require **Allow restricted settings** before Accessibility can be enabled for a sideloaded APK.
- No advertising SDK is included.

## 📦 Installation

1. Download `Study-Sprint-v2.2.0.apk` from the release assets.
2. Open the APK on your Android phone.
3. Allow installation from your browser or file manager if Android requests it.
4. Install the update over your existing Study Sprint installation.

The APK uses the same package name and signing certificate as v1.2.0, so existing local study data is retained.

## ✅ Build verification

- Release APK built successfully.
- 10 unit tests passed.
- Android lint completed with no errors.
- Package: `com.pranav.study.cet_study_sprint`
- Version name: `2.2.0`
- Version code: `5`
- APK SHA-256: `F6F73371E85D4881E5065A45D9460F51AD9821C9C2D98FE55D9FF84AB312E0D3`

## 📲 Requirements

- Android 8.0 or newer.
- Internet access is required only for Google sign-in and update checking.
- A compatible AI app is required only for the optional AI-assisted MCQ workflow.
- Text-based PDFs are recommended; scanned image-only PDFs may not import correctly.

Thanks for using Study Sprint. Keep focusing, practising, and tracking your progress! 🚀
