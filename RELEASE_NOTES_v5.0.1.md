# Study Sprint v5.0.1

- Original practice requests such as “Give me 5 MCQs based on some basic concepts of chemistry for practice” use the profile-selected exam and named chapter directly. They no longer spend Tavily search credits or pull in unrelated redox/MBA chapter results.
- Online sourced-question searches use a focused topic rather than the entire chat request and difficulty instructions; unrelated chapter results are discarded before they can be shown or passed to a tutor.
- Basic-concepts-of-chemistry practice prompts focus on mole concept, stoichiometry, molar mass and related concepts. Redox or MBA/management questions from generated results are rejected and tried with a different tutor.
- Generated practice now requests inline `$...$` LaTeX equations. Quiz questions and options already use the math-capable renderer; new regression tests cover chemistry equation preservation. Verify AI-generated answers before relying on them.
- These changes do not increase exam fees or change exam marking. Original practice skips a Tavily search; NVIDIA/OpenRouter tutor quotas still apply. The app owner must deploy the latest Render backend for this fix to work. Install the signed APK over the previous version to keep local data.

Release workflow checks: unit tests, release lint, signed APK build, emulator UI tests, backend tests and Firebase rules tests.
