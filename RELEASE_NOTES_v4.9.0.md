# Study Sprint v4.9.0

- Floating Study buddy avatar at the bottom right, with shared server-managed chat.
- Nemotron text answers, NVIDIA Kimi K3 backup, and up to four photos for Kimi vision questions.
- Floating rounded navigation with raised Focus, bug-report icon beside Settings, and decorative bell removed.
- Provider keys kept in private Render environment variables, excluded from source and APKs.
- Updated privacy details for selected photos, providers and request allowances.

Shared chat requires the Render backend, Firebase Authentication and App Check to be activated and verified before release.

- Generate up to two text-based PDFs per device per local day from 1–4 study photos, each with 10 answered MCQs. Optional automatic practice import preserves existing sets. Save/open the PDF and start the imported quiz. Re-exporting an existing PDF does not consume another generation.
