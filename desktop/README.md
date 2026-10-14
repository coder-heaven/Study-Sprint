# Study Sprint PC Windows desktop

This directory packages the existing `pc/` PWA as a Windows desktop application. It does not add PC UI behavior or copy any Android runtime code into the desktop app.

## How it works

`npm run build:pc --prefix desktop` directly invokes the existing PC build with the current Node executable and stages its output in `desktop/pc-dist/`. The staging step moves onboarding outside the shell that is hidden for new profiles and rewrites only the staged HTML to load the pinned local MathJax bundle. That staging directory and all build output are ignored by Git.

Electron serves those static files through the privileged, secure `study-sprint://bundle` protocol. The packaged renderer uses an asar archive, `nodeIntegration: false`, context isolation, sandboxing and web security; it never loads the app from an arbitrary `file://` URL. Renderer navigation, frames, new windows and permission requests are restricted or denied. MathJax 3.2.2 and its license are staged locally, and the desktop CSP does not allow remote scripts.

## Local commands

From the repository root, with Node.js 22 or newer:

```sh
npm ci --prefix pc
npm test --prefix pc
npm ci --prefix desktop
npm run start --prefix desktop
```

To create both Windows artifacts on Windows:

```sh
npm run dist:win --prefix desktop -- --publish never
```

The installer and portable executable are written to `desktop/release/` as `Study-Sprint-PC-*-Setup-x64.exe` and `Study-Sprint-PC-*-Portable-x64.exe`. The app version is kept aligned with the current PC version (`5.1.0`).

## CI

`.github/workflows/build-desktop.yml` runs the existing PC tests, stages and tests the desktop bundle, builds the x64 NSIS installer plus x64 portable executable on `windows-2022`, runs the unpacked executable smoke test, and uploads only the executables and SHA-256 files. It is a separate workflow from the GitHub Pages deployment workflow.

The workflow has an opt-in `workflow_dispatch` boolean named `publish_release`. When run on `main` with that input enabled, it creates the non-latest `desktop-v5.1.0` release from `desktop/RELEASE_NOTES_v5.1.0.md`. Code signing, auto-update publishing and ARM64 Windows artifacts are not configured.
