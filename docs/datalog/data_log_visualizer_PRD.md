# PRD: Data Log Visualizer (Flight Data)

**Design doc:** [`data_log_visualizer_design.md`](data_log_visualizer_design.md)
**Mock:** [Flight Data Design — Claude Design](https://claude.ai/design/p/dfe4d880-6486-4790-a8d3-63830a1d7c6d?file=Flight+Data+Design.dc.html)
(`Flight Data Design.dc.html`: frames 1a–1d entry points and visualizer, 2a–2c phone, 3a–3c signed-out
gate and ad placement; `Flight Data Visualizer.dc.html` is the live chart component)
**Status:** 📋 Proposed
**Last updated:** 2026-09-13

> **Naming.** The code, protos, schema, and this document call the feature **data logs** — the
> domain-neutral name, per the Thing-not-aircraft rule ([AGENTS.md § Coding Conventions](../../AGENTS.md#coding-conventions)).
> The *airplane* template names the section **Flight Data**; a future *automotive* template will
> name it **Drive Data**. Every user-visible word comes from the template's lexicon, never from code.

---

## 1. Problem

Modern avionics record everything. A Garmin G3X Touch writes a CSV to its SD card on every power-up:
one row per second, 108 columns — engine (RPM, MAP, oil pressure and temperature, CHT/EGT per
cylinder, fuel flow), electrical (volts, amps, backup-bus discretes), flight (altitude, airspeed,
attitude, G-load, AOA), navigation (GPS position, track, CDI), autopilot modes, and CAS alerts.
A G1000 writes the same family of file; a Dynon SkyView writes its own.

Owners of these aircraft already use this data for maintenance. The questions are exactly the ones a
logbook entry raises: *Did oil pressure hold after the change? Did CHT 3 run hot before I re-baffled?
What was the fuel flow when the engine stumbled on climb-out?* Today the answer lives on an SD card, a
laptop spreadsheet, or a third-party analysis service that knows nothing about the aircraft's
maintenance record. SquawkIt holds the record and none of the evidence.

The gap is sharpest at the seams the app already owns:

| Moment | What the owner wants | What they do today |
|---|---|---|
| Post-maintenance ground run | Attach the run to the work log as proof the fix held | Note "ground run OK" in the log body |
| Investigating a squawk ("rough at 4,500 RPM") | Look at RPM, fuel pressure and EGT spread around the event | Pull the card, open Excel, screenshot, lose it |
| Recurring inspection | Compare oil temperature trend across the last several flights | Nothing |
| Shared aircraft | Let a co-owner or mechanic see the same trace | Email a 6 MB CSV |

The same shape recurs outside aviation: OBD-II exports from a car, track-day telemetry, NMEA logs
from a boat. Squawk / task / log transfers unchanged to every domain
([`multi_domain_maintenance_PRD.md`](../product/multi_domain_maintenance_PRD.md)); so does *the data
the machine recorded while it ran*. This feature is built for the aircraft first and designed so a
second domain is a parser and a lexicon, not a rewrite.

## 2. Goals

- **G1.** An owner can upload an avionics data log to a Thing and see it charted on every platform
  (Android, iOS, web) within seconds, with no configuration.
- **G2.** The visualizer is a real instrument: unlimited panes, unlimited series per pane, shared time
  cursor and zoom, drag-to-brush zoom, pinch and wheel zoom, series moved between panes by drag.
  It should replace the spreadsheet, not summarise it.
- **G3.** A data log is a first-class record of the Thing, and *also* attachable to a log entry, task,
  or squawk as evidence, the same way a photo or PDF is today.
- **G4.** Garmin G3X ships first. Adding a format is a parser plus fixtures, with no UI changes:
  G1000 and Dynon SkyView follow, then the low-effort long tail (§7).
- **G5.** Data logs sync and share like every other record: local-first, available offline once on
  the device, visible to everyone the Thing is shared with. The feature is free on every tier, with
  no limit on the number or size of files.
- **G6.** Nothing in the data model or UI assumes an aeroplane. The section label, the noun, and the
  default chart layout come from the template; the canonical series vocabulary is
  domain-namespaced so `engine.rpm` means the same thing on a Rotax and a Honda.

## 3. Non-Goals

- **Analysis and diagnosis.** No exceedance detection, trend reports, engine-monitor "insights", or
  comparisons across flights in V1. The badge on the mock's flight list (`CHT HIGH`) is the V2
  direction (§12), not a V1 requirement.
- **Server-side parsing.** Files are parsed on the device that uploads them and on the device that
  opens them. No Cloud Function reads a data log in V1. (V2's destination lookup, §12, reads the
  record's metadata, not the file.)
- **Editing the data.** No trimming, splicing, unit conversion, or column renaming.
- **Guest uploads.** Uploading requires a signed-in, non-anonymous account (§5.7). Guests can browse
  the section and read what it does; the upload control leads to sign-in, never to a file picker.
- **Inline sign-in on mobile.** The guest prompt routes to the existing *Link to an account* flow in
  Settings. No second sign-in UI is built.
- **Joining split files.** A source that starts a new file at every power cycle produces two files
  for a flight with a shutdown. Each is its own upload and its own record; the app never
  concatenates, groups, or infers a "flight" across files.
- **Export.** Data logs are not included in the logbook export bundle in V1; a 6 MB CSV per flight
  would swamp it. Follow-up in §12.
- **Flight logbook features.** No pilot time, landings, or route logging. A data log's start/end
  identifiers are metadata for finding the file, not a flight record.
- **Live telemetry, Bluetooth, or Wi-Fi import from the avionics.** File import only.
- **Automotive parsers.** The architecture accommodates them (§8.4); none ship in V1.

## 4. Users and Stories

- **Owner-operator, day of maintenance.** "I changed the oil and ran it up. I want the run attached
  to the log." → Add Log → Add attachment → *Flight data log* → picks the run from this morning
  (or uploads it) → the log entry carries a Flight Data row that opens the visualizer.
- **Same owner, at home that evening.** "Did oil pressure settle?" → Flight Data → tap the run →
  RPM is already plotted → drags *Oil Press* and *Oil Temp* into a second pane → brushes the two
  minutes after start.
- **Owner chasing a squawk.** "Rough at 4,500 RPM on climb." → Flight Data → last flight →
  adds a pane with EGT1–4 → cursor across the climb → sees EGT 3 lag → attaches the log to the squawk.
- **Co-owner on a shared aircraft, on the web.** Opens the same Thing, sees the same logs, uploads
  Saturday's flight from the SD card by drag and drop. The other owner sees it on their phone.
- **Mechanic with view access.** Opens the attached ground run from the log entry, reads it, does
  not need to know what a G3X is.
- **Rental pilot with a borrowed log.** Uploads a file whose tail number is not this aircraft's.
  Gets a clear `TAIL MISMATCH` warning and can still import it.
- **G1000 owner, three months later.** Drops a G1000 CSV. It parses because the Garmin family
  shares one column vocabulary. Nothing else changed.

## 5. Requirements

Priorities: **P0** ships in V1 or the feature does not ship; **P1** ships in V1 unless it slips, in
which case it is the first follow-up; **P2** is designed for, not built.

### 5.1 Entry points

- **R1 (P0).** A Thing whose template declares the data-log section gets a sixth shell section,
  after Logs and before Settings, titled by the lexicon ("Flight Data" on the airplane preset). The
  section is absent — not disabled — on templates that do not declare it, following the
  capabilities-remove-not-disable rule.
- **R2 (P0).** The section shows an upload control (an *Upload Log* button on wide layouts, the
  shell's context FAB on phones), the list of the Thing's data logs newest first, and an empty state
  written in the lexicon. On phones the section joins the bottom bar as its fifth item; Settings
  already lives in the top bar at that width, so the bar stays within five.
- **R2c (P2).** Drag-and-drop upload is a nice-to-have, not part of V1. If built, it is one mechanism
  that serves both ordinary attachments (on the add-attachment sheet and record forms) and data-log
  upload, never a data-log-only feature. The mock's dashed drop zone is this affordance.
- **R2a (P1).** On phones the section header carries a search action that filters the list by date,
  identifier, or attached-record title, matching the other sections' search bars.
- **R2b (P0).** **Variable-width bottom bar.** Five labelled items do not fit the floating pill on a
  320 dp phone without truncating even the short plurals. As part of this project the pill changes on
  every preset: only the selected item shows icon and label; the others show their icon alone, with
  the label as the icon's accessibility name. Today the unselected items are text-only, so this is a
  swap, not an addition, and it frees the width the fifth item needs.
- **R3 (P0).** *Flight data log* is a new attachment type wherever attachments exist today (log
  entries, tasks, squawks). The add-attachment sheet gains a third option beside *Choose file* and
  *Add link*. Choosing it opens a picker that lists the Thing's existing data logs, marks any on the
  same day as the record, notes where each is already attached, and offers *Upload log file* for a
  new one.
- **R4 (P0).** A data-log attachment row on a record shows a `FLIGHT DATA` badge (lexicon), the
  source and duration, and opens the visualizer on tap. It never opens in the system viewer.
- **R5 (P0).** Attaching does not copy. The attachment references the data-log record by id; one
  data log may be attached to any number of records. Deleting the parent record removes the
  reference only. Deleting the data log (from the section) removes its bytes and leaves referencing
  rows showing *Removed*.

### 5.2 Import

- **R6 (P0).** Import accepts a file from the platform picker or a drop; format is detected from
  the file's content, never its extension or name. Detection runs on the header only, so a wrong
  file fails in under a second.
- **R7 (P0).** An unrecognised file fails with a message naming the formats that *are* supported
  and a way to send us a sample. Nothing is stored.
- **R8 (P0).** Parsing runs off the main thread on every platform. A 10 MB file (roughly a 6-hour
  G3X flight) parses in under 3 seconds on a mid-range 2024 phone.
- **R9 (P0).** The raw file is kept, byte-exact, so a future parser can re-read it. It is stored
  compressed; CSV of this shape compresses roughly 8–10×.
- **R10 (P0).** Duplicate upload by content (same SHA-256) is refused with a link to the existing
  record. A file with the same source unit and start timestamp as an existing record is flagged as
  a probable duplicate and requires confirmation.
- **R11 (P0).** The source's own identity is compared with the Thing's. For an aeroplane that is the
  `aircraft_ident` header field against the tail number. A mismatch shows a persistent warning on the
  record and in the visualizer; it never blocks import.
- **R12 (P1).** If the mismatched identity matches *another* Thing in the account, the import offers
  to file the log there instead.
- **R13 (P0).** A log with no airborne segment is labelled *Ground run* in the list instead of a
  route. Airborne is any sample with GPS ground speed above 30 kt or height above ground above
  50 ft, when the source has those series.
- **R14 (P0).** No product limit on the number of data logs per Thing or the size of a file. The
  attachment quotas (5 MB per file, 15 MB per parent, 1 GB per user) do not apply to data logs, and
  data logs do not count against them. The only rejection is a file that does not parse (R7).

### 5.3 The record

- **R15 (P0).** A data log record holds: id, Thing id, format id and version, source product and
  unit identity, start instant with UTC offset, duration, sample count and rate, the series catalogue
  (name, short name, unit, kind, min, max, sample count), the raw-file blob reference, SHA-256, size,
  the identity-mismatch flag, and the start/end location identifiers when derivable.
- **R16 (P0).** The record is a synced entity like a log or squawk: local-first, pushed and pulled by
  the sync engine, resolved through `ThingScopeResolver`, never from the signed-in uid.
- **R17 (P0).** Bytes travel through the existing blob pipeline (local store, background upload and
  download, per-blob sync state, GC on record deletion). Storage rules distinguish data-log blobs
  from attachment blobs so the attachment size rule does not apply to them.
- **R18 (P0).** Metadata is available offline. Bytes are available offline once downloaded; a
  remote-only log downloads on open with the same sync-state badges attachments use.
- **R19 (P0).** Deleting a data log confirms, tombstones the record, and reclaims the blob through
  the existing record-deletion GC.

### 5.4 The visualizer

- **R20 (P0).** Opening a data log shows a header (date, identity chip, mismatch warning if any,
  local start time with UTC offset, duration, source product), a stack of panes, a shared time axis,
  and a series sidebar.
- **R21 (P0).** **Panes.** Any number of panes; add one from the *New pane* target at the bottom;
  remove one from its header; each pane holds any number of numeric series. The default layout on
  open is one pane containing the template's default series (RPM on the airplane preset), or the
  first numeric series if absent.
- **R22 (P0).** **Axes.** Series in a pane that share a unit share one Y scale. The first unit in the
  pane reads on the left axis, the second on the right; further units read only in the legend. Scales
  fit the *visible* time range with a small pad and refit on zoom. Splitting units across panes is the
  user's choice, not a limitation.
- **R23 (P0).** **Time.** All panes share one time domain and one cursor. Three navigation gestures,
  each with a pointer equivalent:

  | Intent | Touch | Mouse / trackpad |
  |---|---|---|
  | **Brush zoom** — select a span, zoom to it | One-finger horizontal drag; release zooms | Left-button drag; release zooms |
  | **Pan** — move along the timeline at the current zoom | Two-finger horizontal scroll | Horizontal wheel or two-finger trackpad swipe; Shift + vertical wheel |
  | **Zoom** — change the zoom level around a point | Two-finger pinch, centred on the fingers' midpoint | Ctrl/⌘ + vertical wheel, or trackpad pinch, centred on the pointer |

  A plain vertical wheel or one-finger vertical drag scrolls the pane stack and never touches the
  time domain, so the page stays scrollable. Pan is clamped to the log's bounds and does nothing at
  full zoom-out. A *Reset* control shows the zoomed range and returns to the full log. Minimum zoom
  span is 5 seconds.
- **R23a (P0).** The axis is elapsed time (`mm:ss`, `h:mm:ss`) with tick density chosen from the chart
  width (about one label per 72 dp, steps from 5 s to 1 h) and edge labels kept inside the chart.
  Hovering or touching a pane places the cursor; it shows its time on the axis and every series'
  value at that instant in its legend entry.
- **R24 (P0).** **Legend.** The legend is a row of chips in the pane header. Each chip shows a colour
  swatch, short name, live value at the cursor, unit, and a remove control; the chip is also the drag
  handle. Chips drag onto another pane to move, onto *New pane* to split.
- **R24a (P0).** **Series colour.** A series' colour is a deterministic function of the series and the
  theme, not of its position in a pane. RPM is the same colour in every pane, after every add or
  remove, and on every reopen; it changes only when the theme changes. Light and dark themes each
  have their own palette, chosen for contrast on that theme's chart surface.
- **R25 (P0).** **Sidebar — Series.** A searchable list of every plottable series with name, unit,
  and full-range min–max. Tap adds to the *target* pane (the last pane touched, highlighted);
  drag adds to any pane. Series already in the target show a check.
- **R26 (P0).** **Sidebar — Info.** Flight facts (date, start, UTC offset, duration, samples and
  rate, series counts, file name) and source-unit facts (product and unit, software version, system
  id, airframe and engine hours as recorded), plus the identity match or mismatch notice.
- **R27 (P0).** **Narrow layouts.** Below tablet width the sidebar becomes a right-hand drawer behind
  a *tune* control (mock 2c); the header's *Upload* and *Reset* controls collapse to icons; panes
  stack full-width at about 170 dp; the R23 touch gestures apply, and vertical scroll is never
  captured by the chart.
- **R28 (P0).** **Performance.** Rendering decimates to the pixel column (min/max per column), so a
  6-hour log pans and zooms at frame rate on a phone. Zoom re-decimates from the full-resolution data.
- **R29 (P1).** **Map pane.** When the source has latitude and longitude, they collapse into one
  *Position* series. Adding it creates a map pane: base tiles, the track as a polyline, and a dot at
  the cursor. Map and chart series never share a pane. Tile provider and attribution are a design-doc
  decision; the map must degrade to track-on-blank if tiles fail to load.
- **R30 (P1).** **Layout presets.** One-tap layouts from the template — on the airplane preset:
  *Engine* (RPM, MAP, oil pressure and temperature, CHT and EGT per cylinder), *Fuel*, *Flight*
  (altitude, airspeed, vertical speed), *Electrical*. Presets are canonical-series references (§8.3),
  so they apply to any source that carries those series.
- **R31 (P1).** The last layout used on a data log is remembered on that device and restored on
  reopen. Named, synced layouts are P2.
- **R32 (P1).** Clock-time axis as an alternative to elapsed time, in the log's local zone.
- **R33 (P2).** Text and discrete series (autopilot mode, CAS alerts, backup-bus flags) rendered as
  an event strip under the time axis.

### 5.5 The list

- **R34 (P0).** Each row: date and route or *Ground run* (R13), start local time, location
  identifier when known, duration in monospace, source product, series count, and an
  attached-to indicator naming the record it is attached to.
- **R35 (P0).** Upload progress and parse failures appear inline in the list, not in a dialog.
- **R36 (P1).** Route shows the start identifier from the file when the format carries one (the G3X
  filename suffix is the nearest airport at power-up). The destination identifier is resolved on the
  server in V2 (§12) from the end position the client writes into the record.

### 5.6 Sharing and collaboration

- **R37 (P0).** Data logs live under the host's tree and follow the Thing's ACL: anyone who can see
  the Thing's logs can see and open its data logs; anyone who can add a log can upload one. Firestore
  and Storage rules are the enforcement, as everywhere else.
- **R38 (P0).** Sharing adds no gating: a member's tier and the host's tier are both irrelevant to
  uploading or viewing a data log.
- **R39 (P1).** Uploading or deleting a data log sends the collaboration notification other members
  already receive for record writes: "*Name* added a flight data log · Sep 2 · Ground run" and
  "*Name* removed a flight data log …". Same fan-out as logs, tasks, and squawks, one notification per
  write, noun from the lexicon. Layout edits inside the visualizer never notify.

### 5.7 Gating

Three mechanisms, kept separate, per [AGENTS.md § Gating](../../AGENTS.md#gating-three-mechanisms-kept-separate).

- **R40 (P0). Account.** Upload requires a signed-in, non-anonymous account, because data logs are
  stored and synced per account like attachments. The section itself is browsable by a guest. Web has
  no guest mode at all (the app is entered through sign-in), so the guest states below are mobile
  only:
  - **Guest on a wide layout** (tablet; mock 3a's layout): the section renders a *Sign in to upload
    logs* card offering the login screen's providers (Google, Apple, email link), a *What gets
    charted* list, and a preview of the visualizer. No drop zone.
  - **Guest on a phone** (mocks 3b–3c): tapping *Upload Log* opens a sheet, *Link to an account to
    upload logs*, with *Open Settings* and *Not now*. *Open Settings* lands on Settings with the
    existing *Link to an account* sheet already open; on return the user taps Upload again. Nothing
    picked before sign-in is kept.
  - Viewing a data log on a Thing shared *to* a signed-in user is never gated.
- **R41 (P0). Entitlement.** None. Upload and viewing are free on Basic and Pro alike, with no count
  or size allowance. `SubscriptionManager` gains no method for this feature, and data logs do not
  appear on the storage-usage line of the subscription page.
- **R42 (P0). Template — no data migration.** The section and the attachment option exist only on
  Things whose template declares the section. The airplane preset declares it in a new version (the
  bump-on-every-edit rule); no other preset does. **Verified constraint:** a Thing carries its
  template inline as DNA, and a canonical preset edit reaches only Things created after it, unless
  the DNA refresh script rewrites every Thing document. That is a data backfill, and this feature
  must not depend on one.
  - **How the lexicon already avoids this.** The words a Thing renders do not come from its DNA.
    `TemplateRegistry.lexiconFor` resolves the lexicon **by template id** from the canonical preset
    baked into the running build, and reads the stored copy only for an id the build does not carry.
    The rationale recorded there: a lexicon is app UI, written against a release's screens, so it
    belongs to the app like `strings.xml`; freezing a copy per Thing made every user a fork of the
    vocabulary. Old Things keep carrying a lexicon nobody reads, and every release's words reach
    every Thing with no migration.
  - **Capabilities get the same rule.** Today `CurrentThingTemplate` reads capabilities straight
    from the DNA. This feature adds `TemplateRegistry.capabilitiesFor`, mirroring `lexiconFor`:
    canonical by id first, the stored copy only for an unknown id, `ALL_ENABLED` when nothing
    applies. Declaring the section in the new airplane preset then reaches every existing airplane
    the moment the build ships. Same for `scheduleTypesOffered`'s legacy default, which becomes
    unnecessary.
  - This is safe for the same reason the DNA refresh script is: a stored template can currently
    only be an unmodified copy of a canonical one, because custom and fetched templates are designed
    but unbuilt. When they arrive, the rule narrows to "canonical wins for an unmodified copy" and
    the design doc for that work decides how a copy declares itself modified.
  - **Degradation.** `TemplateResolution` marks a Thing degraded when its DNA names a `Section` value
    the build has no code for. New Things created from the new preset carry that value, so an older
    build would render them degraded. With capabilities resolved by id, a build that carries the id
    renders from its own canonical and skips that check; degradation stays only for ids the build
    does not carry. The proto change itself is additive: no schema migration, no rules change.
  - The DNA refresh becomes optional housekeeping, run whenever convenient, never a launch gate.
- **R43 (P0). Platform and rollout.** A new `AppCapability.isDataLogsSupported` flag gates the
  section, the attachment option, and the parser registration. It is **true on developer builds
  only** while the feature is built, and flipped to true on every host when V1 is complete. It is a
  rollout switch, not a per-user flag; it never reaches Firestore.
- **R44 (P0).** No `DeveloperFlags` entry and no subscription hook; the app capability is the only
  switch.

### 5.8 Ads

- **R44a (P0).** The visualizer carries one fixed 320 × 50 unit for the free tier, following the
  display-ads PRD's card: a *Sponsored* label, a *Subscribe to remove ads* link, and no adaptive
  sizing. On wide layouts it sits in the sidebar footer; on phones it sits below the *New pane*
  target, under the panes. It is never inside a pane, never over a chart, and counts toward the
  session cap. It is hidden whenever `shouldShowAds()` is false. **Required on Android and iOS
  only**; web carries no ad product and none is required for this feature. The section's list shows
  no ads in V1.
- **R44b (P0).** The unit reports through the existing ad events with a new surface value,
  `data_logs`, added to `AdSurface`.

### 5.9 Lexicon

- **R45 (P0).** New lexicon entries: the data-log noun (airplane: "flight data log" / "flight data
  logs", short plural "Flight Data"), and the empty-state hint. Every string that names the concept —
  section title, attachment option, badge, picker title, delete confirmation, snackbars — resolves
  from these. No string hard-codes "flight".

### 5.10 Analytics

- **R46 (P0).** Four Thing-scoped events, following the typed taxonomy in `core/analytics`:
  `data_log_imported` (format, source: `section` / `attachment`, duration bucket, size bucket,
  series count), `data_log_import_failed` (reason: `unrecognized` / `duplicate` / `parse_error`), `data_log_opened` (source: `section` / `attachment`), and `data_log_layout_applied`
  (preset id or `custom`).

## 6. UX

The mock renders the visualizer on a dark instrument surface (`#1C1C1E`) inside the light app shell.
That is the dark-theme rendering. The visualizer follows the app theme: on the light theme the chart
surface, grid, axes, and series palette are the light-theme set. Series colours are stable within a
theme (R24a).

### 6.1 Shell section (mock 1a)

```
┌ Sidebar ─────────┐┌───────────────────────────────────────────────────────────────┐
│ ✈ Sling TSi       ││ Flight Data                                    [⬆ Upload Log] │
│   N532SL          ││ Engine and flight logs from the G3X, linked by tail number.   │
│ ▫ Dashboard       ││ ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐ │
│ ▫ Squawks         ││ │  ☁  Drop a G3X data log here (log_YYYYMMDD_HHMMSS_*.csv)  │ │
│ ▫ Tasks           ││ │     Garmin G3X · G3X Touch · GDU 4xx. More formats coming. │ │
│ ▫ Logs            ││ └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘ │
│ ▪ Flight Data NEW ││ Recent flights  4                                             │
│                   ││ ┌───────────────────────────────────────────────────────────┐ │
│                   ││ │ 📈  Sep 3, 2026 · E16 → E16                 108 series  › │ │
│                   ││ │     10:06 · San Martin · 17m 13s · GDU 460                │ │
│                   ││ ├───────────────────────────────────────────────────────────┤ │
│                   ││ │ 📈  Sep 2, 2026 · Ground run                108 series  › │ │
│                   ││ │     14:47 · San Martin · 4m 15s · GDU 460                 │ │
│                   ││ ├───────────────────────────────────────────────────────────┤ │
│                   ││ │ 📈  Aug 28, 2026 · E16 → KWVI   📎 Oil change  108 series › │
│ ▫ Settings        ││ │     09:12 · Watsonville · 1h 04m · GDU 460                │ │
└───────────────────┘│ └───────────────────────────────────────────────────────────┘ │
                     └───────────────────────────────────────────────────────────────┘
```

The description line, the drop-zone copy, and "Recent flights" are lexicon strings. The dashed drop
zone is the P2 drag-and-drop affordance (R2c); V1 ships the button alone. The `NEW` pill is a
release-launch affordance that goes away after the first open. `E16 → KWVI` depends on
the V2 server lookup (R36); V1 renders `E16` alone.

### 6.2 Attachment type (mock 1b)

Log detail, attachment rows — the third is the new type:

```
Attachments
┌──────────────────────────────────────────────────────────┐
│ 📄  Rotax SB-915-012                      PDF · 1.2 MB ☁✓ │
│ 🖼  Filter cut-open                       JPG · 3.8 MB ☁✓ │
│ 📈  Post-service ground run  [FLIGHT DATA]              ☁✓ │
│     G3X log · 4m 15s · Opens in visualizer                │
└──────────────────────────────────────────────────────────┘
```

Add-attachment sheet, then the data-log picker it leads to:

```
Add attachment                    ← Attach flight data log
 📁 Choose file                     Logs already uploaded for N532SL
 🔗 Add link                        ○ Sep 3, 2026 · E16 → E16     10:06 · 17m 13s
 📈 Flight data log       NEW       ● Sep 2, 2026 · Ground run    14:47 · 4m 15s · same day as this log
    G3X CSV — parsed and            ○ Aug 28, 2026 · E16 → KWVI   09:12 · 1h 04m · attached to "Oil change"
    viewable in charts              [⬆ Upload log file]                    [Attach]
```

A log already attached elsewhere is dimmed for information only and remains selectable (R5).

### 6.3 Visualizer (mocks 1c and 1d)

```
← Sep 2, 2026  [N532SL] ⚠ TAIL MISMATCH     14:47 local (UTC-07:00) · 4m 15s · Garmin GDU 460
                                          [⤢ 01:10 – 02:40 Reset] [⬆ Upload log] [⚙]
┌ Pane 1 ────────────────────────────────────────────────┐┌ Series ─────┬ Flight ┐
│ ⠿ [■ E1 RPM 4,930 rpm ×] [■ E1 MAP 29.1 inHg ×] [+ Series] 🗑 ││ 🔍 Search series      │
│ 5,200 ┤        ╭──╮                              ┤ 30 ││ Click adds to Pane 2 · │
│       │   ╭────╯  ╰───╮  ╭───╮                   │    ││ drag onto any pane  67 │
│ 4,000 ┤───╯           ╰──╯   ╰────────╮          ┤ 20 ││ ⠿ RPM      0 – 4,930  +│
│       │              ┊                ╰───       │    ││ ⠿ Oil Press 0 – 87 PSI ✓│
│ 2,800 ┤              ┊ 01:42                     ┤ 10 ││ ⠿ Oil Temp 52–149 °F  +│
├ Pane 2 (target) ───────────────────────────────────────┤│ ⠿ CHT1 … EGT4          │
│ ⠿ [■ E1 OilP 52 PSI ×] [+ Series]                   🗑 ││ ⠿ Position  lat, lon 🗺│
│  87 ┤ ╭──────────────────────────────                  ││ ⠿ Volts  12.5 – 13.9  +│
│   0 ┤─╯              ┊                                 ││ …                      │
├────────────────────────────────────────────────────────┤│                        │
│ 00:00     01:00    ┊[01:42]   02:00     03:00    04:00 ││                        │
│ ┌ ─ ─ ─ ─ ─ ─  + New pane — click, or drop a series here ┐ ││                        │
└────────────────────────────────────────────────────────┘└────────────────────────┘
```

- Pane headers carry the legend as chips (mock 1c). Mock 1d's floating in-chart legend was
  considered and rejected: the chip is also the drag handle and the remove control, and it costs no
  chart area on a phone.
- The target pane has an amber border and the *+ Series* chip is amber; everything else in the
  chart is blue-family. Amber is the cursor and the target, nothing else, keeping within the
  ≤10% accent budget.
- Left axis in the first unit's colour, right axis in the second's; grid at quartiles; the cursor
  is a thin amber line across every pane with the time in an amber pill on the axis.
- The map pane (1d) fits the track's bounding box, draws the track in the primary blue and the
  cursor dot in amber, and has no Y axis.
- On a phone: the header wraps, the sidebar is a drawer, panes are 150 dp tall, the *New pane*
  target sits under the time axis.

### 6.4 Phone (mocks 2a–2c)

```
← Flight Data                    🔍     │  ← Sep 2, 2026 [N532SL]      [⤢] [⬆] [⚙]
  N532SL · Sling TSi                    │  14:47 local (UTC-07:00) · 4m 15s · Garmin GDU 460
┌────────────────────────────────────┐  │  ┌ ⠿ [■ E1 RPM 4,930 rpm ×] [+ Series]   🗑 ┐
│ 📈 Sep 3 · E16 → E16             › │  │  │  ╭──╮     ╭───╮                          │
│    10:06 · 17m 13s                 │  │  │──╯  ╰─────╯   ╰──────── ┊                │
├────────────────────────────────────┤  │  ├ ⠿ [■ Position 37.081, -121.600 ×]    🗑 ┤
│ 📈 Sep 2 · Ground run            › │  │  │        ╱‾‾╲   map tiles                 │
│    14:47 · 4m 15s                  │  │  │   ●───╯    ╲__                          │
├────────────────────────────────────┤  │  ├──────────────────────────────────────────┤
│ 📈 Aug 28 · E16 → KWVI           › │  │  │ 00:00     01:00  ┊[01:42]  03:00   04:00 │
│    📄 Oil change & run-up          │  │  │ ┌ ─ ─ + New pane — tap, or drop here ─ ┐ │
│    09:12 · 1h 04m                  │  │  │  Sponsored        Subscribe to remove ads │
└────────────────────────────────────┘  │  │  [        AD · 320 × 50        ]          │
                      [⬆ Upload Log]    │  └──────────────────────────────────────────┘
   ▦    ⚠    ☑    ▤   [📈 Flight Data]
```

The bottom pill shows icons for the unselected sections and icon plus label for the selected one
(R2b). The list card's attached-record line is a link in the primary colour with the record's icon.
The *tune* control opens the Series / Flight drawer from the right (2c).

### 6.5 Guest (mocks 3a–3c)

Guest on a tablet-width layout (the mock draws it in the web frame, but web has no guest mode): the
section body is a two-column card, sign-in on the left, *What gets charted* on the right:

```
🔒  Sign in to upload logs                    WHAT GETS CHARTED
    Uploaded logs are stored with the account   ✓ Every numeric column in the G3X CSV
    and available on every device.              ✓ Unlimited panes, shared cursor and zoom
    [ G  Continue with Google        ]          ✓ Aircraft position on a map
    [    Continue with Apple         ]          ✓ Logs attach to entries, tasks and squawks
    [ ✉  Continue with email link    ]          ┌ preview of the visualizer ┐
    Free accounts include Flight Data with ads. Compare plans
```

Mobile, guest: the dimmed empty section behind a bottom sheet, *Link to an account to upload logs*,
then Settings with the *Link to an account* sheet open and the same three providers. Copy on that
sheet ends "Then return to Flight Data to upload the log."

## 7. Supported Formats

Every format needs a real sample file checked into `docs/datalog/samples/` (identity and coordinates
anonymised, per the no-real-data rule), a parser fixture test on that file, and a row in this table
before it is called supported.

| Format                                                                                | Detect by                                                                                          | Shape                                                                                                                              | Notes                                                                                                                                                                                                                                           | Phase    |
|---------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| **Garmin G3X / G3X Touch (GDU 4xx, GDU 37x)**                                         | Line 1 `#airframe_info,` with `product="GDU …"`                                                    | 3 header lines: metadata; long names with `(unit)`; G1000-style short names. Then 1 Hz rows, local date/time + UTC + offset first. | Empty cells; text columns (GPS fix, nav annunciation, CAS alerts); `(discrete)` 0/1 flags; signed `+lat`. One file per power cycle, so a flight with a restart is two files, uploaded separately. Filename suffix is the nearest airport ident. | **V1**   |
| **Garmin G1000 / G1000 NXi / Perspective**                                            | Line 1 `#airframe_info,` with `airframe_name=` and no `product="GDU 4`                             | 3 header lines: metadata; `#`-prefixed units row; short names — the *same vocabulary* as G3X line 3, with leading spaces.          | Shares the Garmin parser; column mapping by short name.                                                                                                                                                                                         | **V1.1** |
| **Dynon SkyView (HDX / Classic / SE)**                                                | Header row beginning `Session Time,` with Dynon column names (`GPS Fix Quality`, `Thermocouple N`) | Single header row with `(unit)`; user-configurable rate (1/16 s to 10 s); may restart mid-file at a power cycle.                   | Engine columns are generic (`Thermocouple 1`) and need per-install mapping to CHT/EGT — offer a one-time mapping prompt, remembered per source unit.                                                                                            | **V1.1** |
| Avidyne IFD / Entegra                                                                 | CSV header signature                                                                               | Single header row                                                                                                                  | Low effort once the canonical schema exists.                                                                                                                                                                                                    | V1.2     |
| JPI EDM 700 / 730 / 830 / 900 series                                                  | EzTrends export header                                                                             | Engine-only, no GPS                                                                                                                | Very common in the certified fleet; high value for the CHT/EGT stories.                                                                                                                                                                         | V1.2     |
| Advanced Flight Systems, GRT, MGL                                                     | CSV header signatures                                                                              | Single header row                                                                                                                  | Experimental fleet.                                                                                                                                                                                                                             | V1.2     |
| ForeFlight / Garmin Pilot track logs                                                  | GPX / KML / CSV                                                                                    | Position and altitude only                                                                                                         | Feeds the map pane; no engine data.                                                                                                                                                                                                             | V1.2     |
| OBD-II app exports (Torque, Car Scanner, OBD Fusion); track loggers (RaceChrono, AiM) | CSV header signatures                                                                              | Single header row, variable rate                                                                                                   | Automotive template. Needs the automotive lexicon and section declaration.                                                                                                                                                                      | Later    |

**The G3X sample** (a 4 m 15 s ground run, 260 rows, 121 KB — about 465 bytes per row) yields 108
series, 67 numeric. Categories the sidebar groups by, derived from the header text: Engine, Fuel,
Electrical, Autopilot, Navigation, Flight, System.

## 8. Architecture Constraints

Pointers for the design doc; the design doc decides the details.

### 8.1 Module

A canonical feature module, `feature/datalog`, with `model` / `datamanager` / `sharedassets` /
`viewing` / `update` and a `di/` uber module, following `feature/tasks` and the five-step checklist.
Parsers live in `datamanager` behind one interface with two responsibilities: recognise a file from
its header alone, and parse it into a format-neutral result (metadata, a time column, series with
canonical ids where known and raw names always). Formats are identified by an enum, stored on the
record, so a re-parse knows which parser produced it. Types and signatures are the design doc's.

### 8.2 Storage

- A new `CollectionKind.DataLog` for the record (§5.3), nested under the Thing like logs and
  squawks; zero-migration since the collection column is text. The `CollectionKind.ALL` coverage test
  forces the registration.
- Bytes in the R2 blob store, gzip-compressed, tagged as a data-log blob so the attachment size rule
  and quotas do not apply to that path. Existing upload/download drivers, sync-state badges, and GC
  apply.
- A device-local parsed cache (columnar, typed) beside the blob so reopening does not re-parse.
  Cache, not source of truth; rebuilt from the blob when absent or when the parser version changes.

### 8.3 Canonical series vocabulary

A small registry mapping source columns to namespaced ids: `time.local`, `position.lat`,
`position.lon`, `flight.alt_gps`, `flight.ias`, `flight.vs`, `engine[1].rpm`, `engine[1].map`,
`engine[1].oil_press`, `engine[1].oil_temp`, `engine[1].cht[n]`, `engine[1].egt[n]`,
`engine[1].fuel_flow`, `fuel.qty[n]`, `elec.volts[n]`, `elec.amps[n]`, and so on. Unknown columns keep
their raw name and are still plottable. Presets (R30) and the default series (R21) reference
canonical ids, which is what makes them source-independent and domain-portable (`engine[1].rpm` on
a car is the same id).

### 8.4 Template

- `Capabilities.Section` gains `SECTION_DATA_LOGS`; `ShellSection` gains the matching value; the
  shell's section mapping already drops unknown sections safely on older builds.
- `Lexicon` gains the data-log noun and empty-state hint (R45). The template also names the default
  series and the preset layouts by canonical id, so a car template can say `vehicle.speed` where the
  aeroplane says `engine[1].rpm`.
- Airplane template bumps to a new version declaring the section; existing Things move via the
  existing DNA refresh.

### 8.5 Rendering

Compose `Canvas` on all three hosts; no chart library dependency exists in the project and none is
proposed. Decimation (R28) is computed per pane per frame from the visible range. For the map pane,
a raster-tile provider drawn through Coil (already a dependency) is one implementation for three
hosts; a native map SDK is three. The design doc weighs that against the provider chosen under
§11 decision 7.

## 9. Rollout

| Phase              | Scope                                                                                                                  | Exit                                                                             |
|--------------------|------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| **A — Foundation** | Module, record, blob path, G3X parser with fixture, canonical registry, `isDataLogsSupported` (R43), account gate (R40, mocks 3a–3c), template bump and DNA fallback (R42) | Upload from the section on developer builds of all three hosts; record syncs; opens to a placeholder |
| **B — Visualizer** | R20–R28, R34–R35; chips legend; variable-width bottom bar (R2b) | Mock 1c reproduced on web and phone with the sample file |
| **C — Attach**     | R3–R5; attachment type, picker, badge, row                                                                             | Mock 1b reproduced                                                               |
| **D — Polish** | R12, R13, R29–R32, R36, R39 notifications, presets, ad slot (R44a), `NEW` pill, analytics review | Flip `isDataLogsSupported` on every host; V1 release |
| **E — Formats**    | G1000, Dynon, then §7's V1.2 row                                                                                       | Each behind its fixture                                                          |

Phases A–D are one epic with a project board, one PR per phase; E is a rolling epic.

## 10. Success Criteria

- A G3X CSV from the SD card is charted on a phone within 10 seconds of picking the file, including
  parse, with no configuration.
- A 6-hour log pans and zooms without dropped frames on a mid-range phone and in Chrome.
- Half of data logs uploaded in the first month are attached to at least one record, showing the
  feature is being used as evidence, not as a file drawer.
- Zero data logs stored without a byte-exact raw file (the re-parse guarantee).
- G1000 support ships as a parser and fixtures only, with no changes under `viewing/`.

## 11. Decisions

Settled by product direction on 2026-09-13.

1. **Free to all.** No entitlement gate, no per-Thing count, no file-size limit (R14, R41). The
   only gate is a signed-in account (R40).
2. **Legend is chips in the pane header** (R24). The floating in-chart variant is not built.
3. **Destination identifier resolves on the server in V2.** The client writes the end position into
   the record; a Cloud Function fills in the nearest identifier. V1 shows the start identifier only.
4. **Split files stay split.** Every file is its own upload and record; no concatenation or grouping.
5. **The visualizer follows the app theme**, and a series keeps one colour within a theme (R24a).
6. **Units are the source's units in V1.** V2 adds a preferences screen for unit preferences and a
   display-theme preference for the visualizer (§12).
7. **Map tiles come from whichever provider is free and simple to integrate.** Google Maps Platform
   is acceptable; so is any raster-tile provider with a free tier and required attribution. The
   design doc picks the concrete provider (§8.5 notes the one-versus-three-integrations trade-off).
8. **Rollout is an app capability**, true on developer builds until V1 is complete (R43).
9. **The bottom bar goes variable-width** on every preset to make room for the fifth item (R2b).
10. **No data migration** for the new section: capabilities resolve by template id from the build,
    exactly as the lexicon already does (R42).
11. **Drag-and-drop is P2** and, when built, shared between attachments and data logs (R2c).
12. **Ads are required on mobile only** (R44a).

### Still open

- **Dynon engine-channel mapping** (§7): how the one-time thermocouple-to-CHT/EGT prompt is worded
  and where the mapping is stored. Decide with the Dynon parser.

## 12. Later

- **Insights.** Exceedance badges on the list (`CHT HIGH`), needing engine limits from the template
  or the component (a Rotax 915 has published CHT and oil-temperature limits). Trend charts across
  flights. The canonical vocabulary is what makes this possible without per-format work.
- **Event strip** for text and discrete series (R33).
- **Export.** Include attached data logs in the logbook export bundle as CSV, or as links.
- **Destination identifier.** A Cloud Function resolves the nearest location identifier from the
  end position on the record and writes it back; the list then reads `E16 → KWVI`.
- **Preferences.** A settings screen for unit preferences (temperature, volume, distance, pressure)
  applied at render time with the source units kept in the data, and a display-theme preference for
  the visualizer (follow app, always light, always dark).
- **Automotive.** The automotive template declares the section as *Drive Data*, names
  `vehicle.speed` as the default series, and ships an OBD-II CSV parser. Everything under
  `viewing/` is untouched.

## Appendix A — G3X CSV anatomy

From the sample `log_20260902_144756_E16.csv` (GDU 460, software 9.51):

```
Line 1  #airframe_info,log_version="1.00",log_content_version="1.02",product="GDU 460",
        aircraft_ident="N—",unit_software_part_number="006-B1727-40",software_version="9.51",
        system_id="6000…",unit="PFD1",airframe_hours="0.3",engine_hours="1.2"
Line 2  Date (yyyy-mm-dd),Time (hh:mm:ss),UTC Time (hh:mm:ss),UTC Offset (hh:mm),Latitude (deg),
        Longitude (deg),GPS Altitude (ft),GPS Fix Status,…,RPM,Oil Press (PSI),Oil Temp (deg F),
        …,EGT1 (deg F),…,EGT4 (deg F),EFIS ON BKUP (discrete),…,CAS Alert,Terrain Alert
Line 3  Lcl Date,Lcl Time,UTC Time,UTCOfst,Latitude,Longitude,AltGPS,GPSfix,,GndSpd,TRK,…,
        E1 RPM,E1 OilP,E1 OilT,…,E1 EGT1,…,E1 EGT4,,,,,,,,,
Line 4+ 2026-09-02,14:47:56,21:47:56,-07:00,+37.08…,-121.60…,290,3D-,…,0,12,8,,…
```

Line 3's short names are the G1000 vocabulary (`E1 RPM`, `AltGPS`, `GndSpd`), which is why one
Garmin parser covers both; a blank short name means "G3X-only column, use the long name". Column
groups in the sample, by header text: 4 time · 14 GPS/position · 9 flight/attitude · 2 AOA ·
4 selected (bugs) · 1 baro · 2 COM · 14 nav/CDI · 13 autopilot/FD · 6 air data · 8 AHRS/system ·
2 transponder · 7 engine · 1 flaps · 4 fuel · 1 trim · 6 electrical · 1 CO · 4 EGT · 7 discretes ·
2 alerts.
