# Store listing image generator

Turns raw device captures into styled listing images for Google Play and the
App Store: a tilted device frame bleeding off the canvas edge, a bold two-tone
headline, and SquawkIt's own wordmark. No faked device chrome, no stock
photography. Style reference: [appshot.gallery](https://www.appshot.gallery/app/picture-insect-bug-identifier),
adapted to the brand palette and type from `DESIGN.md`.

It also renders the Play feature graphic. Output lives under
`docs/product/store_assets/`, ready to upload as-is.

## Run it

```bash
python3 docs/product/screenshot_generator/generate.py --check   # which captures are present
python3 docs/product/screenshot_generator/generate.py           # render everything that has captures
python3 docs/product/screenshot_generator/generate.py appstore_iphone feature_graphic
```

Requires Python 3 (stdlib only) and Google Chrome at the default macOS path.
Point `CHROME_BIN` at another Chrome or Chromium binary if needed.

Fonts come straight from `core/ui/theme/src/commonMain/composeResources/font/`
and the app icon from `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`,
so nothing here can drift from what ships.

## Folder contract

Drop raw captures here, named by screen, one folder per device class:

| Folder under `docs/product/screenshots/` | Capture on | Native size | Renders to |
|---|---|---|---|
| `phone/` | Pixel 8 class Android phone | 1080×2364 | `store_assets/play/phone/` 1080×2364 |
| `tablet/` | Pixel Tablet emulator, landscape, or leave empty to derive from `ipad/` | 2560×1600 | `store_assets/play/tablet/` 2560×1600 |
| `iphone/` | iPhone 16 Pro Max simulator, or leave empty to derive from `phone/` | 1320×2868 | `store_assets/appstore/iphone_6_9/` 1320×2868 |
| `iphone/` (same captures) | rendered again by `appstore_iphone_6_5` | | `store_assets/appstore/iphone_6_5/` 1284×2778 |
| `ipad/` | Any iPad Pro simulator (the frame scales the capture) | e.g. 2420×1668 | `store_assets/appstore/ipad_13/` 2752×2064, or 2064×2752 for a portrait capture |

Screen filenames, in upload order: `overview_1`, `tasks`, `squawks`, `logs`,
`log_detail`, `export`, `sharing` (all `.png`). A folder may hold any subset;
missing screens are skipped with a message, so a partial set still renders.

The first image stacks up to three dashboards: `overview_1.png` is the front
device, `overview_2.png` and `overview_3.png` sit behind it, up and to the
left, so each one's header stays visible. Capture three different kinds of
Thing (say an airplane, a car, and a home). Only `overview_1` is required.

When `iphone/` is empty the iPhone set is derived from the Android phone captures: the Android
status bar (the top `ANDROID_STATUS_BAR_PX` of a 1080-wide capture) is cropped off and an
iOS-style bar with a Dynamic Island is drawn in its place, coloured from the capture's own top
edge. Drop real simulator captures into `iphone/` to bypass this.

Tablet captures get the reverse treatment. The iPad simulator has no demo mode, so its real clock
and battery readout are painted over with `9:41 AM  Mon Jun 9` and full-battery glyphs, each patch
coloured from the capture right beside it (so a sheet's scrim is respected). When `tablet/` is empty
the Play tablet set is derived from the same `ipad/` captures with an Android bar (`5:00`, Wi-Fi,
the Pixel battery pill) painted in the same spots; the app lays out identically on both. Drop
Pixel Tablet captures into `tablet/` to bypass this.

Orientation is read from each capture. A landscape capture gets the landscape
layout (copy on the left, device on the right); a portrait capture gets the
portrait layout (copy on top, device below). Play accepts either for tablets,
and the App Store accepts either for iPad, so shoot whichever shows the screen
best. The multi-pane tablet layout reads best in landscape.

The phone captures in `phone/` also feed the feature graphic: `overview_1.png`
appears in the device on its right side. Without it the icon takes that spot.

Capture in dark mode, with the Android demo-mode status bar (5:00, LTE, 100). A capture taken
outside demo mode, or with a sheet's scrim dimming the bar, can be brought in line afterwards:

```bash
python3 docs/product/screenshot_generator/normalize_status_bar.py \
    docs/product/screenshots/phone/log_detail.png docs/product/screenshots/phone/logs.png
```

That rewrites the first capture's status bar with the second's glyphs on the first's own
background colour. Needs ffmpeg.

## Adding or changing a screen

Edit the `SCREENS` list in `generate.py`:

```python
{
    "num": "08",                        # upload order
    "src": "your_screen",               # <src>.png in every capture folder; a list stacks several
    "feature_label": "Feature Name",    # small wordmark line
    "l1": "First headline line,",
    "l2": "Second line.",
    "object_position": "50% 0%",        # crop anchor when the frame crops the capture
},
```

Keep headlines short. The portrait canvas fits about 20 characters per line at
phone width; the landscape column fits about 22. Avoid aviation jargon and
words that imply something is broken (issues, defects, faults): the app
covers cars, boats and homes too, and the copy should read as neutral
record-keeping.

## The one rule that matters: no per-user data on the canvas

The headline and wordmark are the only text on the canvas, and both must
describe the **feature**, never today's content. Every user has a different
set of things, different entry counts, different names on their sign-offs. A
canvas that says "36 Entries" or names a real technician looks broken the
moment a reviewer's or a future user's app doesn't match it.

- Good: "Never Miss A Due Date." (what the Tasks screen *does*)
- Bad: "3 Overdue Tasks." (true today, for one seeded demo account)

An earlier version had callout bubbles pulled from the capture ("Poe Dameron",
"36 Entries"). They were removed for exactly this reason.

## What this deliberately isn't

`DESIGN.md` states the product style as "nothing decorates; everything
documents" and rejects consumer-app marketing flourish. This generator's style
is a heavier treatment than that, adopted on request to match the
appshot.gallery reference. If the brand direction changes, revisit whether a
flat capture with the app's own card radius would serve better.

## Files here

| File | Purpose |
|---|---|
| `generate.py` | The generator. `SCREENS` lists the screens, `TARGETS` the device classes and canvas sizes. |
| `templates/portrait.html` | Portrait canvas. Phone or tablet frame chosen per target. |
| `templates/landscape.html` | Landscape canvas for tablet and iPad captures. |
| `templates/feature_graphic.html` | The 1024×500 Play feature graphic. |
| `normalize_status_bar.py` | Rewrites one capture's Android status bar to match another's. |
| `check_listing.py` | Checks `store_listing.md` copy against each console's character limits. |
