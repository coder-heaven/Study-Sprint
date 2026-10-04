# Study Sprint v4.9.3

- Enforces exactly four options A-D for single-correct MCQs. Five-option responses are rejected rather than silently truncated.
- Adds optional online MCQ mode for MHT-CET, JEE and NEET, with exam-matched source citations and a Start MCQ test action. Google search needs a private GEMINI_API_KEY in Render; unavailable search does not produce invented questions.
- Photos prefer Gemini Flash when configured, with structured ten-question/four-option PDF output and Kimi backup for retryable failures. PDF writing remains local and the two-PDF daily allowance stays unchanged.
- Resend repeats the last question with its original photos, model and request mode, including after reopening the app. New chat clears the recovery data.

Install over the current version to keep study data. Provider quotas and Render wake-up delays still apply.
