# Study Sprint v4.9.8

- Signed update built from the latest main branch. Online MCQs and photo questions support two private Gemini API keys on the shared Render backend: the second key is used when the first encounters a Google key permission or quota error.
- The backend accepts new Google AI Studio `AQ.` authorization keys as well as older keys. This is a **server-side** change; APK v4.9.7 can also use it once the server is updated.
- App owner setup is required: set `GEMINI_API_KEY` and optionally `GEMINI_API_KEY_2` privately on the Render chat service, then deploy the latest main branch. GitHub build secrets and installing this APK do not configure Render. Keys in the same Google Cloud project may share a quota.
- Install this APK over the existing app to preserve local data; do not uninstall or clear storage.

Release workflow checks: Android unit tests, release lint, signed APK build, instrumented UI tests, Firebase rules tests and backend tests.
