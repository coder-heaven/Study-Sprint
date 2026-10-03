# Study Sprint v4.5.1

## Sequential setup and cleaner Home

- Added a focused setup flow before the app tutorial.
- App preferences are completed first, followed by one Android permission task at a time.
- Notifications, Usage Access, Accessibility app blocking and precise alarms each open their own Android Settings page.
- Returning from Android Settings brings the user back to the same setup task and refreshes its status.
- Every optional setup task has both **Skip** and **Next** actions.
- Setup progress survives closing and reopening the app.
- Added **Skip** and **Finish** actions to the app tutorial.
- Reduced Home quick actions to six primary destinations: Focus, Plan, Study, Practice, App limits and Progress.
- Existing settings and advanced features remain available from Settings, profile and the tutorial.

Storage access is not requested globally. Notes and PDFs use Android's file picker, which grants access only to files the user chooses.
