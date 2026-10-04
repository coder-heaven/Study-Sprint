# Shared study chat deployment

The Android app calls `studyBuddy` in its existing Firebase project's `us-central1` region. Never commit provider keys, an encrypted key with its decryption key, service-account JSON or local secret files. `.gitignore` and function deployment exclusions cover private environment files. Firebase Secret Manager encrypts the provider keys at rest; Firebase handles access to the deployed function.

## Owner setup

1. Sign into the existing Firebase project. Confirm Cloud Functions deployment and billing are already enabled; enable billing yourself if needed.
2. Enable Firebase Authentication's Anonymous provider for guest requests, retaining Google sign-in.
3. Register the Android app with Firebase App Check's Play Integrity provider. Configure the existing official signing certificate. For GitHub sideloaded releases, select verdict requirements supported by your distribution; do not require Play licensing unless distributing through Play. Verify an official APK can obtain an App Check token before rollout. Keep App Check enforcement enabled on the function.
4. With the official Firebase CLI authenticated, run `firebase functions:secrets:set OPENROUTER_API_KEY --project YOUR_PROJECT_ID` and `firebase functions:secrets:set NVIDIA_API_KEY --project YOUR_PROJECT_ID`. Enter each key at its secure prompt. Do not paste keys into source code, command arguments, GitHub issues or build secrets that end up in an APK.
5. Run `npm ci --prefix functions` and `npm test --prefix functions`, then `firebase deploy --only functions:study-chat --project YOUR_PROJECT_ID`. Defaults: 100 total requests, 20 per user and 10 Kimi attempts per UTC day; adjust function parameters for your account allowance. Quotas count attempts and do not guarantee provider capacity. Kimi uses NVIDIA, never paid OpenRouter Kimi.
6. Enable Firestore TTL on `_chatQuota.expiresAt` for seven-day cleanup. Existing security rules deny client access to this collection.
7. On an official signed APK, verify guest text chat, Google-user chat and a photo question. Test a controlled Nemotron failure to verify NVIDIA fallback. Confirm the final answer is shown without reasoning content. Only publish after these live checks pass.

## Protocol and limits

The authenticated/App Check verified callable accepts only 1–20 user/assistant messages (24,000 characters total) and at most four JPEG data URIs, each at most 350,000 characters. Clients cannot select a provider, system prompt or API key. Text uses `nvidia/nemotron-3-ultra-550b-a55b:free` at OpenRouter; eligible rate limits, network failures, server errors or unreadable answers switch once to `moonshotai/kimi-k3` at `https://integrate.api.nvidia.com/v1/chat/completions`. Authentication and account errors require owner attention. Photos use Kimi directly. The owner should monitor NVIDIA's current trial allowance; free access is not an unlimited service guarantee.

Questions, photos and answers are not stored by this function. Quota documents contain counts, timestamps and hashed identity only. No provider exception or raw error is logged or returned. The app's old personal-key vault is removed when opening chat. Daily server counters use UTC; this is separate from local app limits, which reset at local midnight.
