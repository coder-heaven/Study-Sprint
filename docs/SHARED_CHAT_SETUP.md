# Shared study chat on Render

Render hosts the Node backend. Firebase Authentication and App Check verify the Android installation; Firestore persists quotas across Render sleeps, restarts and deploys. Cloud Functions and Firebase Secret Manager are not needed for this route. Provider keys and the service account stay only in Render's private environment, never GitHub or the APK. `.gitignore` excludes local private files.

## Phone-friendly owner setup

1. In Render, choose **New > Blueprint**, connect `coder-heaven/Study-Sprint`, select branch `feat/chat-assistant-v490` and use root `render.yaml`. Confirm the web service is **Free**. Alternatively create a Node web service with root directory `functions`, build `npm ci --ignore-scripts --no-audit --no-fund`, start `npm start`, health path `/health`.
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

Default daily quotas: 100 total attempts, 20 per Firebase user and 10 Kimi attempts, plus 5 seconds between user requests. They reset by UTC day and use Firestore transactions. Anonymous identity can be recreated, so global quotas also cap attempts. Existing rules deny client reads/writes to `_chatQuota`. Quota records contain counts, timestamps and hashed IDs, not questions/photos/answers. Old quota records can be removed by an owner; automatic Firestore TTL can incur deletion charges, so consider that before enabling it on a free budget.

The server verifies Firebase ID tokens and App Check tokens for the configured Android app, caps simultaneous requests at three, accepts only bounded JSON and at most four JPEG photos, and never returns raw provider errors or reasoning. Photos go directly to NVIDIA Kimi; text goes to OpenRouter Nemotron with one eligible NVIDIA fallback. Firebase service credentials are distinct from the Android Firebase config. Retain server verification and never put private service credentials in GitHub build secrets that enter an APK.

Render Free sleeps after 15 minutes idle and waking takes roughly a minute. The app allows more time for this first request. Free quotas and external API traffic limits can interrupt service; Render says free instances are for testing/hobby use rather than production. No free-hosting or AI allowance is guaranteed forever. Do not add keep-alive pings to bypass sleep. Free Render local storage is ephemeral, so quotas use existing Firestore instead of a local file or in-memory counter. Existing Firebase features retain their own quotas and any billing settings.

The legacy `functions/index.mjs` Firebase deployment remains available for comparison but the Android build now uses `STUDY_CHAT_URL`; do not deploy Cloud Functions for the Render setup.
