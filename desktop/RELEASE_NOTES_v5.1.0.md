# Study Sprint PC v5.1.0

## Windows desktop build

- Added a reproducible x64 Windows installer and portable executable.
- The packaged PC study space loads from the bundled `study-sprint://bundle` origin with an isolated, Node-disabled renderer.
- Offline MathJax resources are bundled so equation rendering does not execute CDN JavaScript after installation.
- Guest onboarding, focus, progress, and JSON backup downloads are covered by the packaged Electron smoke test.
