# Study Sprint v4.9.9

- Online MCQ mode uses Tavily Search instead of Gemini/Google grounding. It returns only complete four-option exam questions with explicit answers present in exam-specific source results, and links to the source. If results are incomplete, the app asks for another topic rather than inventing questions.
- Photo chat and ten-question photo PDFs now use the existing NVIDIA Kimi/Muse vision routes; Gemini keys are no longer used. Photo PDFs require ten valid questions before saving.
- To activate online MCQs, the app owner must set `TAVILY_API_KEY` privately on the Render `study-sprint-chat` service and deploy the latest main branch. Remove old Gemini variables after deployment. The APK does not contain provider keys; installing it alone cannot configure Render. Tavily free credits and NVIDIA limits are provider-controlled.
- Install this signed update over the previous APK to keep local chats and study data.

Release checks: Android unit tests, release lint, signed APK build, Android UI tests, backend tests and Firebase rules tests.
