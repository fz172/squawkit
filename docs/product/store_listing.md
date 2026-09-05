# Store listing copy

Text fields for Google Play Console and App Store Connect. The long
description for both stores is `play_store_description.txt`. Images come from
`store_assets/` (see `screenshot_generator/README.md`).

Character limits are checked by `screenshot_generator/check_listing.py`.

## Shared

| Field | Value |
|---|---|
| App name (30) | `SquawkIt: Maintenance Logbook` |
| Privacy policy URL | `https://squawkit.fanfly.dev/privacy.html` |
| Support / marketing URL | `https://squawkit.fanfly.dev` |
| Category | Productivity (Play) · Productivity, secondary Utilities (App Store) |
| Age rating | Everyone / 4+ (no user-generated public content, no violence, no gambling) |
| Contains ads | Yes on the free tier, Android and iOS. Declare it in both consoles. |
| In-app purchases | Yes, subscription removes ads. |

## Google Play

**Short description (80)**

```
Maintenance records for your aircraft, car, boat, and home. Offline-first.
```

**Full description (4000)**: `play_store_description.txt`.

**Assets**

| Slot | File | Size |
|---|---|---|
| App icon | `store_assets/play/icon_512.png` | 512×512 |
| Feature graphic | `store_assets/play/feature_graphic.png` | 1024×500 |
| Phone screenshots | `store_assets/play/phone/*.png` | 1080×2364, 2 to 8 |
| 7-inch tablet screenshots | `store_assets/play/tablet/*.png` | 2560×1600 |
| 10-inch tablet screenshots | same files as 7-inch | 2560×1600 |

## App Store

**Subtitle (30)**

```
Squawks, tasks, and logs
```

**Promotional text (170)**, editable without a new build:

```
Log squawks, track due dates by hours or calendar, and keep every maintenance record in sync across iPhone, iPad, and the web. Works fully offline.
```

**Keywords (100)**, comma separated, no spaces, do not repeat words from the name or subtitle:

```
aircraft,airplane,pilot,mechanic,inspection,annual,car,boat,home,tracker,repair,service,logbook
```

**Description (4000)**: `play_store_description.txt`, unchanged.

**Assets**

| Slot | File | Size |
|---|---|---|
| App icon | built from the Xcode asset catalog (`AppIcon-1024.png`, no alpha) | 1024×1024 |
| iPhone 6.9-inch screenshots | `store_assets/appstore/iphone_6_9/*.png` | 1320×2868, 1 to 10 |
| iPad 13-inch screenshots | `store_assets/appstore/ipad_13/*.png` | 2752×2064 or 2064×2752 |

iPad screenshots are required because the target's device family includes
iPad.

## Data disclosures

Answer Play Data Safety and App Store Privacy the same way. What the app
collects when signed in: account identifier and email (Firebase Auth), user
content (maintenance records, photos, documents), device push token,
analytics events (Firebase Analytics), crash data, and advertising identifier
for AdMob on the free tier. All of it is linked to the account. Offline-only
use collects nothing.
