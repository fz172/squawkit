# Thing icons

The Thing glyphs in the brand plane's style: car, bike, boat, home, toolbox (the "anything else"
preset), and the open crate the login hero pours them into.

```
generate_icons.py      the shapes — edit this, run it
<name>.svg             monochrome, currentColor, for UI
<name>-color.svg       the app icon's cyan-to-violet gradient, for marketing and a future icon set
contact-sheet.html     every glyph on navy and on white, for review in a browser
```

Running the script also rewrites `core/ui/.../brand/ThingGlyphPaths.kt`, so Compose draws exactly
these shapes. Each glyph is authored in a 1024×1024 box like `ic_launcher_foreground`, and the
script emits a tight square viewport per glyph so `Icon(glyph, Modifier.size(n))` fills `n` the way
the cropped plane does.

The plane itself is not generated here. Its paths live in `core/ui/.../brand/BrandPlane.kt`, copied
from the launcher foreground; the launcher stays the source of truth for the brand mark.

```bash
python3 docs/branding/thing-icons/generate_icons.py
open docs/branding/thing-icons/contact-sheet.html
```
