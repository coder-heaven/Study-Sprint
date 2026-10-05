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

## Tavily exam web MCQs and NVIDIA photo questions

Create Tavily Search API keys at https://tavily.com/ and set `TAVILY_API_KEY` and optionally `TAVILY_API_KEY_2` privately in the Render service's Environment page. The first key is tried normally; the second is used only if the first key is rejected or its allowance is exhausted. If both keys share a Tavily account, they may share credits. Save and redeploy the latest main branch. Remove the obsolete `GEMINI_API_KEY`, `GEMINI_API_KEY_2`, and `GOOGLE_API_KEY` variables from Render after confirming the new deployment. Do not put provider keys in GitHub secrets that enter the APK or in committed files. Tavily's free credits and any limits are controlled by Tavily.

Online MCQ mode searches the selected MHT-CET, JEE Main or NEET UG exam using Tavily's basic search. Search is restricted to ExamSIDE (`examside.com`), SelfStudys (`selfstudys.com`) and Prepizo (`prepizo.com`); JEE/NEET also allow ExamGOAL (`examgoal.com`). The backend rejects returned links outside the selected exam's domain list even if Tavily ignores its filter, requires the source title to identify the selected exam, and checks ExamSIDE's exam-specific URL segment (its MHT-CET pages sit under `/jee/`). It returns up to five **existing complete MCQs with answers** where available. Otherwise, bounded search snippets are sent to an NVIDIA tutor (or OpenRouter backup) to generate **original practice questions**, labelled with their study context rather than claiming they were in a past paper. Incomplete generated questions fail closed. Generation spends the relevant tutor allowance. Ordinary study explanations do not require Tavily; direct MCQ requests do. Photo questions and ten-question photo PDFs use NVIDIA Kimi/Muse vision and require the existing `NVIDIA_API_KEY`. Photos never go to Tavily.

Live verification after adding the private key: try one online MCQ topic for each exam and check all options, answers and sources; generate a PDF from a clear study photo. The automated tests mock Tavily and NVIDIA; they cannot prove a live key, search results or account quota.


### Additional automatic backups (v4.9.6)

NVIDIA Muse Glimmer 30B uses `meta/muse-glimmer-30b` with the existing `NVIDIA_API_KEY`. Text auto routing tries Nemotron, GPT-OSS 20B, GLM 5.3, Muse, optional Mistral Small, then Kimi. Photo questions try Kimi then Muse on retryable failures. All NVIDIA routes share the existing quota.

To enable the optional Mistral text backup, create a key in Mistral Studio and set `MISTRAL_API_KEY` privately in Render Environment, then Save, rebuild and deploy. Paste the key alone, without `Bearer` or quotes. Never send it in chat or commit it to GitHub. The server uses `mistral-small-latest` through `https://api.mistral.ai/v1/chat/completions`; no provider key enters the APK. An absent key skips this backup. Provider credits, plan and rate limits apply; no purchase is enabled. The app continues to show only Study buddy.

The routing loop has one shared 260-second deadline, including external search, so adding backups does not make retries grow without a bound. Authentication/permission/credit errors retain the existing fail-closed behavior.
