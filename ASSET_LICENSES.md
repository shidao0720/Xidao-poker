# Asset licenses

This file covers distributable assets committed under `frontend/public`. Source
code remains governed by the root `LICENSE` file.

## Public-release assets

The following assets were created specifically for Xidao Poker and are
distributed under the repository MIT license, copyright 2026 shidao0720:

- `frontend/public/favicon.svg`
- `frontend/public/assets/images/ui/xidao-poker-logo.svg`
- every SVG in `frontend/public/assets/images/avatars`
- `frontend/public/assets/audio/music/xidao-signal-loop.wav`

The ambient loop is generated entirely from mathematical oscillators by
`scripts/generate-public-bgm.mjs`; it contains no third-party samples.

Files named `.gitkeep`, Markdown documentation, and empty asset directories do
not contain third-party creative material.

## Deliberately excluded local assets

Assets whose redistribution rights have not been confirmed are not part of the
public repository or release image. Local copies, when present, are preserved
under the ignored `frontend/local-assets-backup` directory. This includes the
previous music file, photographic avatars, card artwork, and the former themed
logo source/export.

The optional `frontend/design-showcase` directory is also excluded from public
releases. It is a local design workspace and is not required by the application.

Do not copy anything from either ignored directory into `frontend/public`
without documenting its author, source, and redistribution license here.
