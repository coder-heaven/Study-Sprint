# Study Sprint v4.9.2

- Chat continues when leaving the screen or reopening the app. Conversations, selected model and unfinished photo requests are saved privately on the device. If Android stops the app, reopen Study buddy and tap Retry. New chat clears the saved conversation.
- Photo MCQ generation uses a smaller, low-reasoning vision request. Completed AI output is retained while creating the PDF, so a file-generation retry does not request the same answer again. Common Unicode math is converted before PDF writing; invalid MCQs are regenerated on Retry. The two-PDF daily limit and optional Practice import remain.
- Adds model introductions and exam-specific CET, JEE and NEET tutoring, with occasional relevant exam tips.
- Adds NVIDIA GPT-OSS 20B and GLM 5.3 text models, a model selector and automatic fallback. Photos continue to use Kimi K3 vision. Provider secrets remain on the server.
- Raises the backend's default app allowances to 1,000 daily requests, 100 per user, and 100 NVIDIA requests. Provider quotas still apply; existing Render services must update their environment overrides and deploy main.
- Refreshes the launcher icon with the original Study Sprint artwork in the emerald theme.

Install over your current version to retain study data. Render's free service can take time to wake up; background process termination requires a manual Retry.
