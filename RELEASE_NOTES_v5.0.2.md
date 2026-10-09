# Study Sprint v5.0.2

## In-app updates

- Tap **Update now** and Study Sprint downloads the official update inside the app, shows progress, verifies it and opens Android’s installation screen. No browser download or file manager is needed.
- Android may ask you to enable **Allow from this source** for Study Sprint once. After returning, the installer opens automatically. You still need to confirm **Install**; a normal GitHub-distributed app cannot silently install updates like Google Play.
- The updater checks the official release checksum, app package, expected newer version and matching signing certificate before offering installation. Partial, corrupted or wrong-app files cannot be offered as updates.
- Downloads can be cancelled and retried. A completely downloaded verified candidate can be reused, including after reopening the app. Keep Study Sprint open during download; interrupted partial downloads restart safely.
- Updates install over the existing app so local study data is retained. Do not uninstall or clear storage.

## Complete, scrollable update features

- Removed the old 500-character release-note cutoff. The complete published feature list is now retained.
- The required update screen, update dialog and Settings update card have bounded scroll areas so long notes do not hide the update buttons. Installation messages are scrollable too.
- Failed update checks no longer report that the installed version is definitely the latest.

Install v5.0.2 once using the existing update method to enable this new flow for future releases. This release is distributed through GitHub, not Google Play; Android installation approval is still required.
