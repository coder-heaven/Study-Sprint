# Study Sprint v4.9.10

- Online MCQ search uses a second Tavily key (`TAVILY_API_KEY_2`) when the primary key is rejected or its allowance is exhausted. Keys remain private on Render; if they belong to the same account, their credits may be shared.
- Search results are limited to ExamSIDE, SelfStudys and Prepizo for MHT-CET, JEE Main and NEET UG, plus ExamGOAL for JEE/NEET. The server checks returned HTTPS link domains as well as search filters and exam-specific titles. Incomplete or unsourced questions fail closed.
- Set both keys in the Render `study-sprint-chat` Environment and deploy the latest main branch; no keys are included in the APK. Install the signed update over the existing app to retain local data.

Release checks: Android unit tests, release lint, signed APK, emulator UI tests, backend tests and Firebase rules tests.
