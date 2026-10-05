# Study Sprint v5.0.0

- Fix online practice when Tavily returns no matching study sources. Requests such as “Give me 5 MCQs based on some basic concepts of chemistry for practice” with Medium difficulty can produce **five original AI practice questions**, clearly labelled as original rather than falsely cited past-paper questions. Each must have four distinct options and one answer; incomplete output is rejected.
- Profile exam is read for every request. MHT-CET, JEE Main and NEET UG searches and generated practice use only the selected exam. Unrelated search results are never passed to the tutor or cited. Complete source questions still show their actual source links; valid chapter snippets may be linked as study context only.
- The app owner must deploy the updated Render backend with `TAVILY_API_KEY` and an NVIDIA or OpenRouter tutor key. The optional `TAVILY_API_KEY_2` still handles Tavily auth/quota errors. A missing Tavily key, provider outage or exhausted quotas cannot be fixed by installing the APK.
- Install the signed APK over the existing app to retain local study data.

Release workflow checks: Android unit tests, release lint, signed APK build, emulator UI tests, backend tests and Firebase rules tests.
