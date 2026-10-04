# Privacy Policy

Study Sprint · Pranav / coder-heaven · Policy version 3 · Effective 4 October 2026

## Your choice

You can use local study features without joining a public leaderboard or signing in with Google. Public profile sharing is off until you explicitly permit it. Accepting optional leaderboard sharing does not give permission to advertise with your photo or sell your information.

## Data on your device

Study Sprint stores your profile, chosen photo, exam preferences, notes, tasks, reminders, imported questions, answers, study history, app-usage totals, limit settings and YouTube session counts on your device. Android backup or device-transfer services may copy app data according to your device settings. Uninstalling may remove local data; restore behavior depends on Android backup. This app does not provide full cloud backup of study history.

## Optional public leaderboard

After you opt in, Firebase Authentication supplies an account identifier, including an anonymous identifier for guest users. Cloud Firestore stores that identifier, your app profile name, a small version of your chosen profile photo, study/quiz scores, scoring events and timestamps. Your name, photo and scores are shown to other leaderboard users. Choose a nickname and avoid school names, addresses, contact details or photos of other people. Study notes, imported documents and device app-usage history are not leaderboard uploads.

## Withdraw sharing

Use Hide my profile in Student leaderboards. This revokes consent on this device and stops new leaderboard uploads. The app then asks the server to hide your profile and replace its public name/photo. Internet access and the original signed-in identity are required; offline removal stays pending and retries when you reconnect. Older app versions shared automatically: this version stops that behavior and requests removal of an existing profile until you opt in again. Already downloaded copies or screenshots held by other people cannot be recalled.

## Retention and deletion limits

Hidden leaderboard records may retain account identifiers, aggregate scores and scoring-event records. Hiding is not full Firebase account or server-record deletion. Local pending scoring events are discarded after the server confirms hiding. Clear app storage in Android Settings to remove local app data; this does not delete server data and may lose the identity needed to manage an anonymous public profile. Hide the profile online first. This release has no in-app full account-deletion feature or fixed server-retention schedule.

## Google and other services

Google sign-in is optional. Google/Firebase process sign-in information and provide account identity and profile details; signing in alone does not permit public leaderboard sharing. GitHub receives requests when the app checks or downloads updates. Network providers may receive ordinary connection information such as an IP address. Their own policies apply. See https://firebase.google.com/support/privacy and https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement.

## Device permissions

Usage Access reads app-usage timing for local limits and statistics. Accessibility observes app window changes and reads only the foreground window’s app package identifier to enforce focus and daily limits. Android grants window-content access for this lookup; Study Sprint does not traverse, read, store or upload screen text. It can open the block/limits screen and return to Home if Android prevents that screen from opening. Camera/photo/document access is used for actions you choose, such as profile photos or importing questions. Notification, vibration and alarm access supports reminders and focus alerts. These permissions are optional and controlled in Android Settings; blocking requires the relevant permissions.

## Optional Study buddy chatbot

Study buddy sends your question and up to 20 recent text turns through the Render backend with Firebase verification. Text questions can use OpenRouter's Nemotron or NVIDIA's GPT-OSS 20B, GLM 5.3 and Kimi K3; Auto tries eligible alternatives after temporary failures. When the owner configures Google AI Studio, photo questions prefer Gemini Flash and online MCQ searches use Google Search grounding. Retryable photo errors can use Kimi backup. Exam and class choices are sent to tailor responses; online MCQ mode sends your topic and chosen exam to Google and retrieved questions to the selected tutor. You may attach up to four photos per question. Selected photos are resized and re-encoded as JPEG without original EXIF metadata. Only the photos you explicitly send are uploaded; your name, profile picture, notes, other gallery photos and app-usage history are not added automatically.

The app owner's provider keys are kept in Render's private server environment and never bundled in the app or repository. Firebase Authentication and App Check verify requests, including anonymous guest identities. The server stores daily request counts and timestamps with a hashed user identifier to enforce allowances. Counters have an expiry timestamp; automatic Firestore TTL is an owner-controlled option. The function does not store questions, photos or answers or deliberately log their content. Cloud services may retain operational records under their policies.

Chat and unfinished requests are saved privately on this device, outside Android cloud backup, so reopening the app restores them. New chat clears the saved conversation and pending photos. If Android stops the process, tap Retry to resume the saved request. Photos are sent with the current question and are not resent on later turns; Retry resends the pending question and its photos. Resend repeats the last request with its original photos and model; these are retained locally until New chat. Source links and Google Search suggestions may load Google content when displayed. Stop ends the app's wait; the server may still finish processing. Google, OpenRouter, NVIDIA and model providers process submitted content under their own policies. Clearing a local chat does not delete provider records. See https://policies.google.com/privacy, https://ai.google.dev/gemini-api/terms, https://openrouter.ai/privacy, https://www.nvidia.com/en-us/about-nvidia/privacy-policy/ and https://firebase.google.com/support/privacy. The obsolete personal-key file from v4.8 is removed on opening Study buddy.

The Report a bug button opens an editable GitHub issue draft with the description you enter, app version and Android API version. It does not automatically submit a report or include your photos, notes, API keys or usage history. GitHub issues are public; review your draft before submitting it.

## Sharing with AI or other apps

When you choose to share a prompt or file with an AI or other app, that selected app receives it under its own policy. Prompts may also be copied to the clipboard. Review the content first and do not include another person's private information. Study Sprint does not silently send your notes or documents to an AI service.

## Students and permission

Only share information you have permission to use. Ask a parent or guardian before using optional online features if you are under 18, and obtain any guardian permission required where you live. The consent checkbox is a user declaration; this app does not verify age or guardian identity. Do not publish another student's information without their appropriate permission.

## Contact and changes

Maintainer: Pranav, GitHub account coder-heaven. Use https://github.com/coder-heaven/Study-Sprint/issues to request a private contact method for privacy questions or deletion requests; the issue tracker is public, so do not post personal data there. The policy is available offline in Settings. A new version of the sharing notice requires renewed consent before further public uploads. This policy describes the app's behavior and does not claim certification under any particular privacy law.

The shared chatbot server is hosted on Render. It receives questions and selected photos to forward them to the AI provider. It does not store conversation content or raw request logs; persistent Firebase quota records contain counts and hashed identity. Render processes network requests as the hosting provider.
