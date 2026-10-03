# Study Sprint v4.6.0

- New FocusIQ-inspired charcoal and emerald UI, original Figma background/timer artwork and Poppins typography. Home uses real focus progress and tasks; existing light and system theme choices remain available.
- App blocking now checks the current foreground app every second, including when the service reconnects or an app stays open past its daily limit.
- Protected focus includes limited apps and focus-only selections. The blocking switch and permission status are visible on the timer; protected sessions require the accessibility service.
- Focus blocks are immediate without Usage Access. Blocking history cannot prevent enforcement; a silently refused blocking-screen launch falls back to Home after verifying the denied app is still foreground.
- Updated the offline privacy description to explain foreground package lookup. No screen text is traversed, saved or uploaded.
- Daily reset, editable YouTube limits, post-limit timed bypasses and existing study data are retained.

Release checks: unit tests, release lint, real Android accessibility-service enforcement tests, Compose UI/navigation tests and Firebase rules tests must pass before publication.
