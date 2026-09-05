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

## Release notes, initial release

Store search indexes the title, subtitle or short description, keywords, and long description.
Release notes carry little ranking weight, but they are the first paragraph many people read on
the listing after the screenshots, so they repeat the phrases people actually search for:
maintenance log, aircraft logbook, car maintenance tracker, boat and home maintenance, offline.

**Google Play release notes (500)**

```
SquawkIt 1.0 is here: one maintenance log for everything you own.

• Aircraft logbook with squawks, inspections, and AD compliance
• Car, motorcycle, bike, boat, and home maintenance tracker
• Recurring tasks by date, hours, or mileage, with due reminders
• Works offline, syncs across phone, tablet, and web
• Share with your mechanic or co-owner; sign-offs stay attached
• Photos and receipts on every entry
• Export PDF, CSV, and XLSX

Free to start. Built for owners who keep records.
```

**App Store “What’s New” (4000)**

```
Welcome to SquawkIt 1.0, the maintenance log for everything you own.

AIRCRAFT LOGBOOK
Track squawks, annuals, 100-hour inspections, ELT and transponder checks, and airworthiness directive compliance by tach or Hobbs time. Log AOG items and see what is grounding the airplane at a glance.

CAR, MOTORCYCLE, BIKE, BOAT, AND HOME
Every kind of thing gets its own vocabulary and meters: odometer for a car, ride distance for a bike, engine hours for a boat, and a chore schedule for a house. Each type ships a recommended starter schedule you can keep, skip, or edit.

NEVER MISS A DUE DATE
Recurring maintenance tasks by calendar, by meter, or on condition. Overdue and due-soon work rises to the top of every list.

WORKS OFFLINE, SYNCS EVERYWHERE
Records are written to your device first, so the hangar and the driveway both work without a signal. Sign in with Apple or Google and everything syncs across iPhone, iPad, and the web.

SHARE WITH THE PEOPLE WHO HELP
Invite a mechanic or co-owner with a code. Technicians sign off their own work, and their sign-offs stay attached to the record.

PAPERWORK THAT TRAVELS WITH THE RECORD
Attach photos, invoices, and inspection reports to the entry they belong to. Export PDF, CSV, and XLSX for a pre-buy, a resale, or a backup that is yours.

Free to start. Questions or ideas: https://squawkit.fanfly.dev
```

## Data disclosures

Answer Play Data Safety and App Store Privacy the same way. What the app
collects when signed in: account identifier and email (Firebase Auth), user
content (maintenance records, photos, documents), device push token,
analytics events (Firebase Analytics), crash data, and advertising identifier
for AdMob on the free tier. All of it is linked to the account. Offline-only
use collects nothing.
