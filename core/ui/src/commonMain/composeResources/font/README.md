# Font Resources

This directory contains bundled font files required by `Type.kt`.

## Required Files

| File                         | Font           | Weight         | Download     |
|------------------------------|----------------|----------------|--------------|
| `space_grotesk_medium.ttf`   | Space Grotesk  | Medium (500)   | Google Fonts |
| `space_grotesk_semibold.ttf` | Space Grotesk  | SemiBold (600) | Google Fonts |
| `space_grotesk_bold.ttf`     | Space Grotesk  | Bold (700)     | Google Fonts |
| `jetbrains_mono_medium.ttf`  | JetBrains Mono | Medium (500)   | Google Fonts |
| `jetbrains_mono_bold.ttf`    | JetBrains Mono | Bold (700)     | Google Fonts |

## Download Instructions

### Space Grotesk

1. Go to: https://fonts.google.com/specimen/Space+Grotesk
2. Click "Download family"
3. Unzip and copy from `static/` folder:
    - `SpaceGrotesk-Medium.ttf` → rename to `space_grotesk_medium.ttf`
    - `SpaceGrotesk-SemiBold.ttf` → rename to `space_grotesk_semibold.ttf`
    - `SpaceGrotesk-Bold.ttf` → rename to `space_grotesk_bold.ttf`

### JetBrains Mono

1. Go to: https://fonts.google.com/specimen/JetBrains+Mono
2. Click "Download family"
3. Unzip and copy from `static/` folder:
    - `JetBrainsMono-Medium.ttf` → rename to `jetbrains_mono_medium.ttf`
    - `JetBrainsMono-Bold.ttf` → rename to `jetbrains_mono_bold.ttf`

## Notes

- File names must match exactly (lowercase, underscores) — the Compose resource generator
  derives Kotlin identifiers directly from file names.
- Only `.ttf` files are needed; `.woff2` etc. are for web use.
- These files are intentionally not committed to the repository if they are added to `.gitignore`.
  If so, document the download step in your project's contributing guide.
