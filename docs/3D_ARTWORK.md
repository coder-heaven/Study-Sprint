# 3D artwork

Generated with the built-in image-generation tool for Study Sprint, October 2026.
The original app logo and original Figma timer/vector assets are retained.
All artwork is local transparent PNG, fitted without cropping, decorative (no duplicate accessibility descriptions), and separate from tappable controls.

Shared prompt direction: premium rounded 3D illustration, emerald #07AE88, mint, ivory and small warm gold accents; soft clay/satin glass, refined studio lighting, subtle contact shadow, complete centered subject with clear margins and genuinely transparent background; no text, numbers, logos or watermark.

- `art_focus_3d.png`: emerald analog focus timer, ivory face, simple hands and small gold star.
- `art_study_3d.png`: open ivory study notebook with emerald cover, mint tabs and gold-accented pencil.
- `art_protection_3d.png`: rounded emerald shield with ivory inset, raised mint checkmark and small gold sparkle.
- `art_progress_3d.png`: rounded gold trophy on emerald base beside mint rising progress steps.

Source assets live in `app/src/main/res/drawable-nodpi/`. `FeatureArtwork` uses explicit dp bounds and `ContentScale.Fit`. Functional progress, timer state, tasks and permissions remain native Compose data and controls.
