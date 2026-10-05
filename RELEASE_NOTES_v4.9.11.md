# Study Sprint v4.9.11

- Fix online MCQs on exam websites that list chapters or partial snippets rather than complete questions with four options and an answer. When a complete sourced question is available, it is used; otherwise a tutor creates original exam-specific practice questions from bounded study snippets. The app labels these links as study context, not as proof that generated questions appeared in a past exam.
- The selected profile exam (MHT-CET, JEE Main or NEET UG) is read for each request. Search and returned source titles are exam-specific; ExamSIDE links also require the matching exam URL path.
- The backend validates that generated practice has 1–5 numbered questions, four distinct options each and one answer. Incomplete results are rejected. AI-generated answers still need review.
- The backend needs a private `TAVILY_API_KEY` on Render, plus a configured NVIDIA or OpenRouter tutor key to generate original practice if source questions are incomplete. `TAVILY_API_KEY_2` is optional. Install this signed update over the existing app to retain data.

Release checks: Android unit tests, release lint, signed APK build, emulator UI tests, backend tests and Firebase rules tests.
