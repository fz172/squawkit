# Promo page fonts

WOFF2 copies of the brand faces the app already bundles, for the static promo page (`index.html`
/ `promo.css`). Self-hosted rather than loaded from Google Fonts so the page and the app can't
drift onto different cuts, and so the page has no third-party origin — see
`docs/web/login_page_split_design.md` §6.2.

| File | Font | Weight | Used for |
|------|------|--------|----------|
| `space_grotesk_semibold.woff2` | Space Grotesk | SemiBold (600) | card and feature titles, buttons |
| `space_grotesk_bold.woff2` | Space Grotesk | Bold (700) | headings, the wordmark |
| `jetbrains_mono_bold.woff2` | JetBrains Mono | Bold (700) | section eyebrows, meter labels |

## Source and licence

Both families are from Google Fonts and are licensed under the
[SIL Open Font License 1.1](https://openfontlicense.org), which permits redistribution — including
bundling converted copies like these.

- Space Grotesk — <https://fonts.google.com/specimen/Space+Grotesk>
- JetBrains Mono — <https://fonts.google.com/specimen/JetBrains+Mono>

## Regenerating

These are converted from the TTFs the Compose app bundles, so the two surfaces stay on identical
outlines. To rebuild after those change:

```bash
pip install "fonttools[woff]" brotli
python - <<'EOF'
from fontTools.ttLib import TTFont
src = "core/ui/theme/src/commonMain/composeResources/font"
dst = "webApp/src/jsMain/resources/fonts"
for name in ("space_grotesk_semibold", "space_grotesk_bold", "jetbrains_mono_bold"):
    f = TTFont(f"{src}/{name}.ttf")
    f.flavor = "woff2"
    f.save(f"{dst}/{name}.woff2")
EOF
```
