# Shared study chat on Render

Render hosts the Node backend. Firebase Authentication and App Check verify the Android installation; Firestore persists quotas across Render sleeps, restarts and deploys. Cloud Functions and Firebase Secret Manager are not needed for this route. Provider keys and the service account stay only in Render's private environment, never GitHub or the APK. `.gitignore` excludes local private files.

## Phone-friendly owner setup

1. In Render, choose **New > Blueprint**, connect `coder-heaven/Study-Sprint`, select branch `main` and use root `render.yaml`. Confirm the web service is **Free**. Alternatively create a Node web service with root directory `functions`, build `npm ci --ignore-scripts --no-audit --no-fund`, start `npm start`, health path `/health`.
2. Set these private Render environment variables (never send their values in chat):
   - `OPENROUTER_API_KEY`: your Nemotron/OpenRouter key.
   - `NVIDIA_API_KEY`: your direct NVIDIA Kimi key.
   - `FIREBASE_APP_ID`: the existing Android Firebase app ID from Project settings.
   - `FIREBASE_SERVICE_ACCOUNT_JSON`: the JSON for a dedicated backend service account in the same Firebase project. Grant only Firestore data read/write (Cloud Datastore User), Firebase Authentication Viewer, and Firebase App Check Token Verifier permissions. This credential grants server access, so keep it private and revoke it if exposed. Paste it into Render's private environment editor, never commit it or upload it as app assets. The SDK supports certificate credentials outside Google hosting.
3. Enable Firebase Authentication's **Anonymous** provider for guest chat. Keep Google sign-in optional. Register App Check with Play Integrity and the official release signing SHA-256. For exclusively GitHub sideloaded APKs, don't require Play recognition/licensing; use device integrity. Keep backend verification enabled. Enable the Play Integrity API for the linked Cloud project.
4. Deploy and confirm `/health` returns `{"ok":true}`. This only verifies the HTTP service is running, not the private providers or Firebase permissions. Copy the Render HTTPS service URL and append `/studyBuddy`.
5. In GitHub repository **Settings > Secrets and variables > Actions > Variables**, create `STUDY_CHAT_URL` with that public HTTPS `/studyBuddy` URL. It is a URL, not a provider secret. Rebuild the APK through the existing workflow after saving it. Blank URL builds show a setup message, and publishing is blocked without this variable. A previously downloaded preview APK cannot acquire the URL automatically.
6. Test the rebuilt, official signed APK with a guest text question and a photo question. Check Nemotron fallback to NVIDIA Kimi using a controlled failure. Publish only after these live tests work.

## Limits and privacy

Default daily quotas: 1,000 total attempts, 100 per Firebase user and 100 NVIDIA attempts, plus 5 seconds between user requests. They reset by UTC day and use Firestore transactions. Anonymous identity can be recreated, so global quotas also cap attempts. Existing rules deny client reads/writes to `_chatQuota`. Quota records contain counts, timestamps and hashed IDs, not questions/photos/answers. Old quota records can be removed by an owner; automatic Firestore TTL can incur deletion charges, so consider that before enabling it on a free budget.

The server verifies Firebase ID tokens and App Check tokens for the configured Android app, caps simultaneous requests at three, accepts only bounded JSON and at most four JPEG photos, and never returns raw provider errors or reasoning. Photos go directly to NVIDIA Kimi; text uses the selected model or eligible automatic NVIDIA fallbacks. Firebase service credentials are distinct from the Android Firebase config. Retain server verification and never put private service credentials in GitHub build secrets that enter an APK.

Render Free sleeps after 15 minutes idle and waking takes roughly a minute. The app allows more time for this first request. Free quotas and external API traffic limits can interrupt service; Render says free instances are for testing/hobby use rather than production. No free-hosting or AI allowance is guaranteed forever. Do not add keep-alive pings to bypass sleep. Free Render local storage is ephemeral, so quotas use existing Firestore instead of a local file or in-memory counter. Existing Firebase features retain their own quotas and any billing settings.

The legacy `functions/index.mjs` Firebase deployment remains available for comparison but the Android build now uses `STUDY_CHAT_URL`; do not deploy Cloud Functions for the Render setup.

## Photo MCQ PDFs
The app generates up to two successful PDFs per device per local calendar day. Attach photos, optionally enable automatic practice import, then tap Generate 10-MCQ PDF. The AI response must contain exactly 10 complete numbered MCQs and answers before a text-based PDF is written. Failed/cancelled provider requests do not consume a PDF slot. Successful generation is stored locally with the daily count and latest file. This device limit is separate from authenticated server/provider quotas and can reset if app storage is cleared. Exporting or opening a saved PDF does not spend a slot. Review AI-generated answers.

## v4.9.2 models and existing Render services

Set the existing service branch to `main` and deploy the latest commit. Existing environment overrides are retained by Render: set `CHAT_DAILY_LIMIT=1000`, `CHAT_USER_DAILY_LIMIT=100`, and `NVIDIA_DAILY_LIMIT=100`. All three NVIDIA models share one allowance; retain server-side identity/App Check verification. The old `KIMI_DAILY_LIMIT` setting is no longer used.

Auto tries Nemotron, GPT-OSS 20B, GLM 5.3, then Kimi K3 on retryable errors. Students can explicitly select a model for text. Photo requests always use Kimi vision; GPT-OSS 20B and GLM 5.3 are text-only. Model IDs are `openai/gpt-oss-20b`, `z-ai/glm-5.3`, and `moonshotai/kimi-k3`. Kimi uses low reasoning effort and an 8,192-token budget. NVIDIA/provider account credits and rate limits remain provider-controlled; these settings raise only Study Sprint's own daily allowance.

## Google image understanding and exam web MCQs (v4.9.3)

Add `GEMINI_API_KEY` (or the supported alias `GOOGLE_API_KEY`) in the Render service's Environment page and choose Save, rebuild and deploy. Use a key from Google AI Studio. Never paste it into chat, source code, APK settings, or a tracked `.env` file. Existing NVIDIA/OpenRouter secrets stay unchanged. Google quotas and search pricing still apply; no paid subscription is enabled by this change.

Gemini 2.5 Flash reads photos when configured, using a ten-question JSON schema with exactly four options for photo PDFs. Retryable Google failures fall back to Kimi; authorization errors ask the owner to fix setup. PDF bytes are still generated locally.

Online MCQ mode retrieves 1–5 single-correct questions for the selected MHT-CET, JEE Main or NEET UG exam. It requires actual Google search queries, supporting citations and exam-specific source titles. Missing, mixed-exam or malformed results fail closed. The selected tutor may format the retrieved questions but cannot change their stems, choices or answers. Source links and Google's search suggestions accompany the result. Start MCQ test is optional and does not spend the photo-PDF allowance. Regular explanations do not require web search; direct MCQ requests also use retrieval.

Live verification after supplying the private key: send one topic for each exam in online MCQ mode; check sources and all four options; start one optional test; generate one PDF from a clear photo; resend after reopening the app. Automated tests use mock provider responses and do not verify a live Google account's access or credits.
