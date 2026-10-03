# Study Sprint v4.8.0

- New emerald 3D study artwork on the welcome/profile setup page replaces the old flat logo panel. Google sign-in and guest setup use the app theme.
- New emerald book-and-graduation-cap app mark, adaptive launcher icon and themed-icon artwork. Navigation, brand screens and the splash screen use the same mark.
- Study buddy adds Nemotron 3 Ultra chat through OpenRouter, reachable from Home, the drawer and Search.
- Each user connects their own API key. Android Keystore encrypts the saved key in storage excluded from backups; no shared credential is bundled in the APK. API key settings can replace or remove it.
- Chat supports follow-up questions, retry, stop, new chat and selectable answers. Only the question and bounded recent conversation are sent; study notes, photos and app usage are not attached automatically. Chat history is held only for the current session.
- Privacy and Terms explain optional AI use and third-party processing. Existing study data and the clean focus circle are retained when updating normally.

Release checks: unit tests, release lint, signed APK build, Android navigation/onboarding/chat/key-storage tests and Firebase rules tests.
