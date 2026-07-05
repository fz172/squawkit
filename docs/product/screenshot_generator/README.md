# Play Store screenshot generator

Turns a raw device screenshot into a styled Play Store listing image: a tilted
phone frame bleeding off the canvas edge, a bold two-tone headline, and
SquawkIt's own wordmark — no device chrome faked, no stock photography. Style
reference: [appshot.gallery](https://www.appshot.gallery/app/picture-insect-bug-identifier),
adapted to SquawkIt's actual brand palette/type from `DESIGN.md` and cut down
to match this app's personality (see "What this deliberately isn't" below).

Output lives in `docs/product/store_assets/`, ready to upload to the Play
Console as-is (1080×2364, matches the raw capture's native resolution).

## Regenerating the current set

```bash
python3 docs/product/screenshot_generator/generate.py
```

Requires:
- Python 3 (stdlib only — no pip install).
- Google Chrome installed at the default macOS path. If yours lives
  somewhere else (or you're on Linux with `chromium`), point at it with
  `CHROME_BIN=/path/to/chrome python3 docs/product/screenshot_generator/generate.py`.

The script always reads the brand fonts straight from
`core/ui/theme/src/commonMain/composeResources/font/` and the raw captures
from `docs/product/screenshots/*.png` — there are no committed copies of
either to fall out of sync. It renders each one with headless Chrome and
overwrites the matching file in `docs/product/store_assets/`.

## Adding a new screenshot

1. Drop the raw device capture in `docs/product/screenshots/` (portrait,
   whatever the device's native resolution is — dark mode reads best against
   this style's light backdrop, but isn't required).
2. Add an entry to the `SCREENS` list in `generate.py`:
   ```python
   {
       "num": "07",                        # controls submission order
       "src": "your_screenshot",           # filename without .png
       "out": "short_name",                # used in the output filename
       "feature_label": "Feature Name",    # small wordmark line, e.g. "Export Logs"
       "l1": "First headline line,",
       "l2": "Second line.",
       "object_position": "50% 0%",        # crop anchor if the top isn't the best crop
   }
   ```
3. Re-run the script.

## The one rule that matters: no per-user data on the canvas

The headline and wordmark are the only text on the canvas, and both must
describe the **feature**, never today's content. Every SquawkIt user has a
different fleet, different entry counts, different names on their
sign-offs — a screenshot that says "36 Entries" or names a real technician
looks broken the moment a reviewer or a future user's own app doesn't match
it.

- Good: "Never Miss A Deadline." (describes what the Maintenance Tasks
  screen *does*)
- Bad: "3 Overdue Tasks." (a number that's true today, for this one seeded
  demo account, and wrong for literally everyone else)

This is also why there are no callout bubbles pointing at specific rows
anymore — an earlier version of this generator had two small labels per
screen ("Poe Dameron", "36 Entries") pulled from the actual capture. They
were removed for exactly this reason; don't reintroduce them without solving
the per-user-data problem first.

## What this deliberately isn't

SquawkIt's own design system (`DESIGN.md`) states its style as "nothing
decorates; everything documents" and explicitly rejects consumer-app
marketing flourish. This generator's style — bold oversized headline, tilted
device photo, gradient backdrop — is a heavier, more energetic treatment than
that, adopted because it was explicitly requested to match the
appshot.gallery reference. If the brand direction changes, revisit whether
this whole approach (vs. e.g. a flat screenshot with the app's own card
radius and no tilt/gradient) is still the right call — see the
`screenshot-style-pitch` artifact from the same conversation this generator
came out of for the more brand-conservative alternatives that were considered
and rejected in favor of this one.

## Files here

| File | Purpose |
|---|---|
| `generate.py` | The generator. Edit `SCREENS` to add/change entries. |
| `export_template.html` | The HTML/CSS canvas template; `generate.py` substitutes `__PLACEHOLDER__` tokens into it per screen. |
| `_render_*.html` | Intermediate per-screen render, written and deleted automatically on every run — never committed. |
