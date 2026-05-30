# PRD: Logbook Export

**Status:** ✅ Implemented (scope expanded beyond this PRD)
**Last updated:** 2026-05-22

---

> **Implementation status.** Shipped in `feature/export/` (`datamanager` + `sharedassets` + `update`), reading
> from the local-first store as designed. Two deliberate expansions beyond this PRD:
> - **PDF output added.** §2.2 listed a PDF facsimile as a non-goal, but the shipped ZIP bundles a **PDF**
>   alongside the CSV files and the XLSX workbook.
> - **Server-side email delivery added.** §2.2 ("No new backend") and §10 ("Scheduled / automated exports… out
>   of scope") were superseded: exports can optionally be **emailed** via the `requestExportDelivery` Firebase
>   Function (`backend/firebase/functions/`), which generates a signed download URL and mails it. The app keeps
>   an **export history** (list / delete / retry / **re-send**), uploading archives to Firebase Storage with a
>   Firestore manifest. See `export_email_automation_design.html` for that design.

---

## 1. Overview

Aircraft owners and mechanics are legally and operationally tied to **paper logbooks** — separate volumes for the airframe, each engine, and each propeller. Even owners who keep their primary records in Hopply still need an offline, portable, spreadsheet-friendly copy of every log entry, compliance event, and squawk for:

- Annual / pre-buy inspection handoff to an A&P or IA who works off paper or Excel.
- Backup against vendor outage or account loss (regulatory exposure if records vanish).
- Pre-sale due-diligence packages, where the buyer's mechanic wants times, ADs, and SBs in a single workbook.
- Personal archive — a CSV/XLSX copy is auditable and survives any future migration.

Today, none of this is possible from inside the app. This PRD specifies a **Logbook Export** feature that produces a Google-Sheets-compatible workbook whose tab and column structure mirrors the conventions of FAA-style paper logbooks.

---

## 2. Goals & Non-Goals

### 2.1 Goals

- **Logbook fidelity.** Output tab names and columns that an A&P would recognise on sight — one tab per logbook volume (airframe, each engine, each prop), with familiar columns (Date, Total Time, Description of Work, Reference, Technician + Cert #).
- **Google Sheets first.** Files must import cleanly into Google Sheets with no manual cell formatting. XLSX workbook plus CSV/UTF-8 fallback, ISO dates, decimal hours.
- **Whole truth.** All log entries, compliance events (ADs, SBs, inspections), and squawks for the chosen scope, plus the technician sign-offs that close each one.
- **Two scopes.** Per-aircraft export (most common) and whole-fleet export (single zip).
- **Date-range filter.** Optional. Defaults to all-time.
- **Offline-capable for the textual data.** Logs, tasks, squawks, and aircraft records run against the local-first store (R1) with no network round-trip. Attachment binaries (§4.10) may require a download for entries that have never been opened on this device.
- **Attachment binaries included.** Every IMAGE / PDF / FILE attachment referenced by any exported log, task, or squawk is downloaded (if not already local) and embedded in the zip alongside the CSVs and XLSX workbook. Attachments are not embedded inside the workbook. LINK-type attachments stay textual — their URL is preserved in the relevant spreadsheet cell. See §4.10.

### 2.2 Non-Goals (this release)

- **Direct Google Sheets push** via OAuth. Same — future work.
- **PDF facsimile** of a paper logbook page. Out of scope for MVP.
- **Re-import / round-trip.** This release is one-way. The export is for human consumption; we do not commit to parsing exported files back into the app.
- **Editing exported files in-app.** Exports are snapshots. Subsequent log edits do not propagate to a previously exported file.
- **Server-side generation.** The export runs entirely on-device. No new backend.

---

## 3. User Stories

| ID | User Role | User Story |
|:---|:---|:---|
| US.1 | Aircraft owner | As an owner, I want to go to Settings → Export logs, pick one aircraft, and hand a Google-Sheets-ready zip to a buyer's mechanic without giving them app access. |
| US.2 | A&P / IA | As an A&P, I want the export to have a separate tab for the airframe and each engine, with the same columns I'm used to in paper, so I can scan it like a logbook. |
| US.3 | Owner | As an owner, I want to export only the last 12 months of activity so I can attach a focused record to my annual sign-off. |
| US.4 | Fleet operator | As a fleet operator (Part 91 small flight school), I want to pick all my aircraft at once and get a single zip so I have one quarterly backup file. |
| US.5 | Fleet operator | As a fleet operator, I want to export a chosen subset of aircraft (e.g. just the two on the line this month) without exporting the whole fleet, so I can hand a focused record to a contracted inspector. |
| US.6 | Any user | As any user, I want the file saved into Files (iOS) or Downloads (Android) directly so I can manage it like any other document — no share sheet detour. |
| US.7 | A&P | As an A&P, I want each row's technician sign-off (name + cert type + cert number) in dedicated columns so I can verify and audit who did what. |
| US.8 | Buyer's mechanic | As a buyer's mechanic, I want every AD and SB compliance event listed with reference number, authority, and last-complied date so I can quickly assess airworthiness. |

---

## 4. Functional Requirements

### 4.1 Entry point

- **Settings screen** → **"Export logs"** row. This is the single entry point. The Aircraft Overview screen does **not** carry an export action; users who want to export one aircraft go through Settings and select that aircraft on the selection screen.

  Rationale: keeps Aircraft Overview focused on operational status (airworthiness, due tasks, recent activity); export is a periodic, archival action that belongs alongside other account-level operations in Settings.

Tapping the row pushes the **Export Selection screen** onto the navigation stack.

### 4.2 Export Selection screen

A full-screen destination (not a modal sheet) that collects everything needed for one export. Layout, top to bottom:

#### 4.2.1 Aircraft section

- A list of every aircraft in the user's fleet, rendered as multi-select rows with a leading checkbox. Each row shows tail number (primary), make + model (secondary), and total log count for context.
- A **"Select all" / "Clear all"** chip at the top of the list.
- Default selection: **all aircraft selected**. Rationale: the most common case is "back up everything"; a single tap on "Clear all" reverses to a per-aircraft picker.
- A live summary line below the list: `3 of 5 aircraft selected · 412 log entries in scope`.

The selection cardinality drives the output layout (§4.5–4.6):
- **1 aircraft selected** → per-aircraft zip layout.
- **2 or more aircraft selected** → multi-aircraft zip layout with a `csv/00_Fleet_Summary.csv` reflecting the selected subset (not the entire fleet).

#### 4.2.2 Date range section

| Input | Type | Default |
|:---|:---|:---|
| Date range | Segmented control: **All time** / **Last 12 months** / **Custom** | All time |
| Start / End | Date pickers (shown only when **Custom**) | Today minus 12 months / today |

The "Last 12 months" preset is included because it matches the most common compliance window (annual cycle). End date is inclusive.

#### 4.2.3 Options section

| Input | Default | Effect |
|:---|:---|:---|
| Include open squawks | On | Off restricts the Squawks tab to addressed/dismissed items. Useful for handover packages where open issues are tracked separately. |

#### 4.2.4 Primary action

A pinned bottom button labeled **"Export"** with the resolved file size estimate, e.g. `Export · ~1.2 MB`. The button is disabled when zero aircraft are selected and shows a helper line: `Select at least one aircraft`.

#### 4.2.5 Progress and success states

When the user taps **Export**, the screen swaps its content area for a progress state — determinate progress bar, current step (`Writing Engine 1 of Cessna 172 (N12345)…`), and a **Cancel** action. The top app bar back arrow is replaced with a close button that triggers the same cancel.

On completion, the screen swaps to a success state: large success icon, file size, saved location (`Saved to Files → Hopply` / `Saved to Downloads/Hopply`), and two actions — **Open** (opens the file in the OS default handler) and **Done** (pops back to Settings).

Cancellation discards the temp file and returns to the configuration state with prior selections preserved.

### 4.3 Output format

Output layout is determined by **how many aircraft the user selected**, not by a separate scope toggle:

- **Single-aircraft export (1 aircraft selected):** one ZIP archive containing a `csv/` folder, a README, a generated XLSX workbook, and any attachment files.
- **Multi-aircraft export (≥2 aircraft selected):** one ZIP archive containing a `csv/` folder with one subfolder per selected aircraft, a top-level README, a generated XLSX workbook, and per-aircraft README/attachment files.

All CSV files are bundled under `csv/`. They are **UTF-8, RFC 4180–compliant**, comma-separated, with `CRLF` line endings, and quoted fields where content contains commas, quotes, or newlines. The XLSX workbook contains the same table data as the CSV files, without embedding attachment binaries.

### 4.4 File naming

Filenames use the aircraft's tail number with non-alphanumeric characters replaced by `_`. The current date (local) is the export's date stamp.

- Single-aircraft: `Hopply_Logs_<TAIL>_<YYYYMMDD>.zip`
  - Example: `Hopply_Logs_N12345_20260518.zip`
- Multi-aircraft (selected subset or all): `Hopply_Logs_Fleet_<YYYYMMDD>.zip`
  - Example: `Hopply_Logs_Fleet_20260518.zip`
  - The same name is used whether the user selected the entire fleet or a subset; the README and Fleet Summary tab disambiguate.

### 4.5 Zip layout — single-aircraft

```
Hopply_Logs_N12345_20260518.zip
├── Hopply_Logs_N12345_20260518.xlsx
├── csv/
│   ├── 00_Aircraft_Info.csv
│   ├── 01_Airframe.csv
│   ├── 02_Engine_1.csv          ← one per engine
│   ├── 02_Engine_2.csv
│   ├── 03_Propeller_1.csv       ← one per propeller (per engine)
│   ├── 03_Propeller_2.csv
│   ├── 10_Compliance.csv
│   ├── 11_Squawks.csv
│   └── 20_Technicians.csv
├── attachments/             ← binaries (images, PDFs, files) — see §4.10
│   ├── 8f3a_8130-3_form.pdf
│   ├── 9b21_oil_filter_after.jpg
│   └── …
└── README.txt
```

Numeric prefixes (`00_`, `01_`, …) preserve logbook order when CSVs are imported as sheets — Google Sheets imports tabs in alphabetical order. The included XLSX workbook already contains these tables as ordered tabs. The `csv/` folder is a fallback for apps that prefer CSV import. The `attachments/` folder is excluded from spreadsheet import; spreadsheet rows reference files inside it by relative path.

### 4.6 Zip layout — multi-aircraft

```
Hopply_Logs_Fleet_20260518.zip
├── Hopply_Logs_Fleet_20260518.xlsx
├── README.txt
├── csv/
│   ├── 00_Fleet_Summary.csv     ← reflects ONLY the aircraft selected, not the whole fleet
│   ├── N12345_Cessna_172/
│   │   └── 00_Aircraft_Info.csv  …  20_Technicians.csv
│   └── N67890_Beechcraft_A36/
│       └── 00_Aircraft_Info.csv  …  20_Technicians.csv
├── N12345_Cessna_172/
│   ├── README.txt
│   └── attachments/          ← per-aircraft binaries
└── N67890_Beechcraft_A36/
    ├── README.txt
    └── attachments/
```

CSV files are scoped under `csv/`. Attachments are scoped per-aircraft and live inside that aircraft's subfolder. There is no top-level `attachments/` folder, so a file referenced only by `N67890`'s log will never be duplicated under `N12345`.

If two selected aircraft share a tail number (legacy data quality issue), the second is disambiguated with a `(2)` suffix on the folder name.

### 4.7 Tab specifications

> **Reference sample:** see `docs/export_logs_sample/N12345_Cessna_172/` for a hand-written set of every CSV in this section populated with realistic data. The sample demonstrates the multi-value newline-in-cell convention, the attachments folder, all status / dismiss-reason values, and the cross-references between tabs.

All tabs share the following conventions:

- **Dates** are `YYYY-MM-DD` in the local time zone the entry was created in. Sortable lex = sortable chronologically.
- **Times** (airframe / engine / prop hours) are decimal hours to **1 decimal place**, e.g. `1247.3`. Empty when the original log didn't record that component's time.
- **Internal identifiers are omitted.** CSV tabs do not expose raw database IDs; cross-references are rendered as user-readable names and dates where possible.
- **Empty cells** for unset optional fields (no `N/A` or `—` literal sentinels).
- **Multi-value cells** (Inspections, Reference Numbers, Squawks Addressed, Attachments) join entries with a single `\n` (LF) **inside** an RFC 4180–quoted cell. Google Sheets renders these as multi-line cells, one entry per line, which preserves readability and keeps each entry independently filterable. Commas inside an entry never become part of the field separator because the cell is fully quoted. Example raw bytes for a 2-entry Inspections cell: `"Annual\n100hr"` (newline literal between entries, surrounding double quotes are part of the CSV record).

#### 4.7.1 `00_Aircraft_Info.csv`

Two-column key-value sheet — the front matter of a logbook:

| Field | Value |
|:---|:---|
| Tail Number | `N12345` |
| Make | `Cessna` |
| Model | `172N` |
| Serial Number | `17265432` |
| Engines | `1` |
| Propellers | `1` |
| Current Airframe Time | `4521.6` |
| Current Engine 1 Time | `1247.3` |
| Current Propeller 1 Time | `894.0` |
| Total Log Entries | `132` |
| Total Squawks | `7` |
| Open Squawks | `2` |
| Export Generated | `2026-05-18 14:22 PDT` |
| Export Period | `All time` or `2025-05-18 → 2026-05-18` |
| Export App Version | `Hopply 1.4.0 (147)` |

#### 4.7.2 `01_Airframe.csv`

Logs whose `component_type == COMPONENT_AIRFRAME`. Columns:

| # | Column | Source |
|:--|:---|:---|
| 1 | Date | `MaintenanceLog.timestamp` (date portion) |
| 2 | Airframe Time | `airframe_time` |
| 3 | Engine 1 Time | `engine_hour` (when the log was filed against airframe but engine hour recorded) |
| 4 | Work Description | `work_description` (multi-line preserved, CSV-quoted) |
| 5 | Inspections | newline-joined names of inspection cards from `inspection_ids` (resolved against `MaintenanceTask.title`). One entry per line within the cell — see Multi-value cells convention in §4.7. |
| 6 | Reference Numbers | newline-joined `reference_number` of any task referenced via `inspection_ids` (e.g. `AD 2019-20-10\nSB 2024-03`). Empty entries omitted. |
| 7 | Squawks Addressed | newline-joined squawk titles from `squawk_ids` (one per line). |
| 8 | Technician | `technician.name` (falls back to `technician_id` if name missing) |
| 9 | Cert Type | `technician.certificate_type` rendered (e.g. `A&P`, `IA`, `Repairman`) |
| 10 | Cert # | `technician.cert_number` |
| 11 | Attachments | newline-joined `attachment.name → attachments/<file>` entries (one per line); LINK-type rendered as `name → <url>`. See §4.10 for the filename scheme and §4.7 for the multi-value cell convention. |

Rows ordered **oldest → newest** (paper-logbook order, opposite of the in-app list view which is newest-first).

#### 4.7.3 `02_Engine_N.csv`

Header rows (rows 1–5) describe the engine:

```
Engine Position,1
Make,Lycoming
Model,O-320-E2D
Serial,L-12345-78
,
Date,Engine Time,Airframe Time,Work Description,Inspections,Reference Numbers,Squawks Addressed,Technician,Cert Type,Cert #,Attachments
2024-08-04,1180.2,4480.1,"Oil & filter change. SAE 100 added...",100hr,SB 2024-03,,Jane Smith,A&P/IA,3201234,
...
```

Filter: logs where `component_type == COMPONENT_ENGINE` AND `component_serial == engine.serial`.

#### 4.7.4 `03_Propeller_N.csv`

Same structure as the Engine tab, with header rows describing hub + each blade:

```
Propeller Position,1 (Engine 1)
Hub Make,McCauley
Hub Model,1A170/DTM
Hub Serial,DTM-9912
Blade 1 Make,McCauley
Blade 1 Model,DTM7657
Blade 1 Serial,B-7657-A
Blade 2 Make,McCauley
Blade 2 Model,DTM7657
Blade 2 Serial,B-7657-B
,
Date,Prop Time,Airframe Time,Work Description,Inspections,Reference Numbers,Technician,Cert Type,Cert #,Attachments
```

Filter: logs where `component_type == COMPONENT_PROPELLER` AND `component_serial` matches the propeller hub serial or any blade serial.

> **Note on cross-component log entries.** A single `MaintenanceLog` is filed against exactly one component (its `component_type` + `component_serial`). An annual inspection covering airframe + engine + propeller is currently logged once — typically as airframe — and references those components via `inspection_ids`. The MVP routes each log to *one* tab matching its `component_type`. Surfacing the same compliance event on all relevant tabs is tracked under §10 Future Work.

#### 4.7.5 `10_Compliance.csv`

All `MaintenanceTask` records — active and historical. One row per task. Columns:

| # | Column | Source |
|:--|:---|:---|
| 1 | Title | `MaintenanceTask.title` |
| 2 | Component | `component` (e.g. `Airframe`, `Engine`, `Propeller`) |
| 3 | Type | `ComplianceType` (`Routine Inspection`, `Service Bulletin`, `Airworthiness Directive`) |
| 4 | Reference # | `reference_number` |
| 5 | Authority | `compliance_authority` |
| 6 | Schedule | Human-readable interval rule (e.g. `Every 12 months`, `Every 100 hours`, `On condition`, `One-time`) |
| 7 | Last Complied — Date | Date of most recent log entry whose `inspection_ids` references this task. Blank if no log exists, even when `force_complied_status` is set (see §8 Decision 6). |
| 8 | Last Complied — Hours | Engine hours from the same log row as Last Complied — Date. Blank if no log exists. |
| 9 | Next Due — Date | Computed by `TaskDueManager` |
| 10 | Next Due — Hours | Computed by `TaskDueManager` |
| 11 | Status | `OK`, `Due Soon`, `Overdue`, `Complied (one-time)` |
| 12 | One-Time | `Yes` / `No` |
| 13 | Notes | `notes` |
| 14 | Compliance Details | `compliance_details` |

Rows ordered **status priority desc, then title asc** (overdue first — the most useful view for an inspector).

#### 4.7.6 `11_Squawks.csv`

Columns:

| # | Column | Source |
|:--|:---|:---|
| 1 | Created | `Squawk.created_at` (date) |
| 2 | Title | `title` |
| 3 | Description | `description` |
| 4 | Priority | `priority` (`Low`, `Medium`, `High`, `AOG`) |
| 5 | Component | `component_type` |
| 6 | Component Serial | `component_serial` |
| 7 | Status | `Open` / `Addressed` / `Dismissed` |
| 8 | Addressed By — Date | log date of that linked log entry |
| 9 | Dismiss Reason | rendered enum (`Obsolete`, `Not Reproducible`, `Duplicate`, `Intended Behavior`) |
| 10 | Dismissed | `dismissed_at` (date) |

Rows ordered **created date descending** (newest first — squawks are read forward-in-time more often than logs).

If "Include open squawks" was off, rows where `status == Open` are omitted.

#### 4.7.7 `20_Technicians.csv`

Distinct technicians who signed off any log in scope. Columns:

| # | Column |
|:--|:---|
| 1 | Name |
| 2 | Cert Type |
| 3 | Cert # |
| 4 | Cert Expiration |

Rows ordered by technician name.

#### 4.7.8 `00_Fleet_Summary.csv` (multi-aircraft only)

One row per **selected** aircraft (not the whole fleet — unselected aircraft are not listed). Columns:

| # | Column |
|:--|:---|
| 1 | Tail Number |
| 2 | Make |
| 3 | Model |
| 4 | Serial Number |
| 5 | Engines |
| 6 | Current Airframe Time |
| 7 | Log Entries (in export) |
| 8 | Open Squawks |
| 9 | Overdue Tasks |
| 10 | Folder | name of the aircraft subfolder in this zip |

#### 4.7.9 `README.txt`

Plaintext, opens in any viewer. Contents:

```
Hopply Logbook Export

Generated: 2026-05-18 14:22 PDT
Scope:     Cessna 172 N12345
Period:    All time
App:       Hopply 1.4.0 (147)

How to import into Google Sheets
1. Extract this ZIP and locate the included `Hopply_Logs_*.xlsx` workbook.
2. Open https://sheets.google.com and create a new blank spreadsheet.
3. File → Import → Upload → choose the `Hopply_Logs_*.xlsx` workbook.
4. Select "Replace spreadsheet" and click "Import data".
   The workbook already contains one tab for each exported table.

CSV fallback
1. Open https://sheets.google.com and create a new blank spreadsheet.
2. File → Import → Upload → choose csv/00_Aircraft_Info.csv.
3. Select "Insert new sheet(s)" and click "Import data".
   This adds the CSV as a new tab instead of replacing the current sheet.
4. Repeat for each CSV in the csv/ folder in the order they are numbered (00, 01, 02, …).
   The numeric prefixes keep the tabs in logbook order.
5. After the last CSV, delete the default "Sheet1" tab.

Tab order in a paper logbook
  Aircraft Info  →  Airframe  →  Engines  →  Propellers
  →  Compliance  →  Squawks  →  Technicians

Notes
- Dates are YYYY-MM-DD in the export device's local time zone.
- Times are decimal hours (1247.3, not 1247:18).
- CSV files are bundled under the csv/ folder. Fleet exports keep each aircraft's CSVs
  in its own folder inside csv/.
- Attachment binaries (photos, PDFs, files) are bundled under the
  attachments/ folder. The spreadsheet "Attachments" column shows
  "<name> → attachments/<file>" so you can locate each file after
  extracting the zip. Attachments are not embedded in the XLSX workbook.
  LINK-type attachments show the original URL.
- This export is a snapshot. It does not update when logs change in Hopply.
```

### 4.8 Save destination

- **iOS:** `~/Documents/` for the Hopply app, surfaced through the Files app under "On My iPhone → Hopply". No share sheet.
- **Android:** `Downloads/Hopply/` via MediaStore (API 30+). Visible in Files app and Downloads.
- After save completes, the success state in the Export Sheet shows the resolved path and an **"Open"** action that hands the file to the OS default handler (Files / Downloads viewer). Re-export overwrites a same-named file from the same day; older dates are preserved.

### 4.9 Permissions

- iOS: no permission needed (app's Documents container).
- Android: no runtime permission needed for MediaStore-based Downloads writes on API 30+ (minSdk 33 — always satisfied).

### 4.10 Attachment bundling

Every IMAGE / PDF / FILE attachment referenced by any exported log, task, or squawk is downloaded (if not already local) and embedded in the zip under the owning aircraft's `attachments/` folder. LINK-type attachments — which have no binary, only a URL — are not downloaded; their URL is preserved verbatim in the CSV cell.

#### 4.10.1 Source of bytes

Hopply's `AttachmentManager` already provides a `ensureLocal(attachment): Flow<DownloadState>` API that downloads a REMOTE_ONLY blob to the local store and emits `Downloading(progress) → Done` (or `Failed(error)`). The export reuses this contract:

- For each attachment in scope, call `ensureLocal()` and await `Done`.
- Once local, read the file bytes from `LocalBlobStore.localUri(blobId)` and stream them into the zip entry.
- If `ensureLocal()` emits `Failed`, fall back to a placeholder cell in the CSV (see §4.10.4).

This means attachments **may require network** for entries that have never been viewed on this device. The progress UI accounts for this — see §4.10.3.

#### 4.10.2 Filename scheme inside `attachments/`

```
attachments/<short_id>_<sanitized_name>
```

Where:
- `<short_id>` = first 4 hex characters of `attachment.id` — keeps filenames short and avoids collisions for attachments that share a display name.
- `<sanitized_name>` = `attachment.name` with non-alphanumeric characters (except `.`, `-`, `_`) replaced by `_`. If `name` is empty, the file extension is inferred from `mime_type` and the file is named `<short_id>.<ext>`.

Examples:
- `8f3a_8130-3_form.pdf`
- `9b21_oil_filter_after.jpg`
- `c40e_left_magneto_part_label.heic`

Two attachments with the same `attachment.id` short-id prefix collide every ~65k attachments per aircraft — far beyond realistic scale.

#### 4.10.3 Progress and selection-screen feedback

- The Selection screen's button-label size estimate now includes the sum of `attachment.size_bytes` for every attachment in scope. A typical 1-aircraft × 200-log export with 50 photos jumps from ~500 KB (text-only) to ~80 MB (with photos), so the size hint is genuinely informative.
- The Running state's step messages include attachment work: `Downloading 12 of 47 attachments — Engine 1, N12345 (3.2 MB)`. Percent is computed from cumulative bytes transferred, not row count.
- If any attachment requires a download, the Running state is marked `(network)` so the user understands the export isn't purely offline.

#### 4.10.4 Failure modes

| Condition | Behaviour |
|:---|:---|
| Attachment is LINK-type | No binary write. CSV cell shows `"name → <url>"`. |
| Attachment is already SYNCED locally | Stream from disk into zip. No network. |
| Attachment is REMOTE_ONLY, download succeeds | `ensureLocal()` runs, bytes installed, then streamed into zip. |
| Attachment is REMOTE_ONLY, download fails (network, auth, integrity) | Skip the file. CSV cell renders `"name → [download failed]"`. Export does **not** abort. The README's notes section lists the failing attachment IDs. |
| Attachment is PENDING_UPLOAD (never reached the server) | Skip. CSV cell renders `"name → [upload pending]"`. README notes it. |
| Anonymous user with REMOTE_ONLY attachment | Skip. CSV cell renders `"name → [download requires sign-in]"`. README notes it. |
| Attachment proto present but `storage_path` and `url` both empty (R1-era legacy) | Skip with `"name → [legacy attachment]"`. README notes it. |
| Disk space exhausted mid-export | Abort export, surface error state, delete partial zip. The Selection screen returns with prior selections preserved. |

The export's success criterion is that the *spreadsheet* is complete and accurate. A missing binary degrades to a textual marker; it does not invalidate the spreadsheet.

#### 4.10.5 Cancellation

Cancel while attachments are downloading aborts the in-flight `ensureLocal()` collection and discards the partial zip. No half-downloaded blobs are written to the local store — `AttachmentManager` already handles its own atomicity.

---

## 5. UX Flow

```
Settings ─── "Export logs" row ──→ push navigation
                                          │
                                          ▼
                       ┌────────────────────────────────────┐
                       │  Export Selection screen           │
                       │                                    │
                       │  AIRCRAFT (3 of 5 selected)        │
                       │   ☑ N12345 — Cessna 172N    132 logs│
                       │   ☑ N67890 — Beech A36       89 logs│
                       │   ☐ N11111 — Piper PA-28     45 logs│
                       │   ☑ N22222 — Cirrus SR22    191 logs│
                       │   ☐ N33333 — Piper PA-46     27 logs│
                       │   [Select all]  [Clear all]        │
                       │                                    │
                       │  DATE RANGE                        │
                       │   [All time] [Last 12 mo] [Custom] │
                       │                                    │
                       │  OPTIONS                           │
                       │   Include open squawks      ●○○○○  │
                       │                                    │
                       │  ┌──────────────────────────────┐  │
                       │  │  Export · ~1.2 MB     →      │  │
                       │  └──────────────────────────────┘  │
                       └─────────────────┬──────────────────┘
                                         │ Export tapped
                                         ▼
                       ┌────────────────────────────────────┐
                       │  Progress (same screen)            │
                       │  Writing Engine 1 of N12345…  47%  │
                       │  [Cancel]                          │
                       └─────────────────┬──────────────────┘
                                         │
                                         ▼
                       ┌────────────────────────────────────┐
                       │  Success                           │
                       │  Hopply_Logs_Fleet_20260518.zip    │
                       │  1.18 MB · Saved to                │
                       │  Files → Hopply                    │
                       │  [Open]   [Done]                   │
                       └────────────────────────────────────┘
```

The flow is a single forward-only journey: Settings → Selection → Progress → Success → back to Settings. **Done** pops back to Settings; **Open** hands the file to the OS file viewer (the user can return to the app via the task switcher).

---

## 6. Edge Cases & Data Quality

| Case | Behaviour |
|:---|:---|
| Aircraft has zero log entries | All log-row tabs (`01_`, `02_`, `03_`) are written with header + zero data rows. `00_Aircraft_Info.csv` notes `Total Log Entries: 0`. |
| Aircraft has no engines / no propellers | Engine and Propeller tabs are not emitted. `Aircraft_Info` still lists `Engines: 0`. |
| Log entry's `technician` is missing (legacy data) | Technician columns left blank; `Technicians` tab does not list an entry for the missing person. |
| Log's `component_serial` doesn't match any current engine/prop serial (component replaced) | Entry is still routed to its `component_type` tab. If the matching component no longer exists, the entry lands in a fallback `02_Engine_Unknown.csv` / `03_Propeller_Unknown.csv` tab so it's not lost. The README explains this. |
| Empty date range (no rows in range) | All log tabs render headers + zero rows. `Aircraft_Info` notes the empty period. Export still succeeds. |
| Inspection / Squawk reference points to an entity that has been deleted | The referenced title/reference is rendered as `[deleted]` to preserve that something was referenced without exposing the internal ID. |
| Concurrent log edit during export | Export reads a snapshot at start; later edits do not affect the file in progress. |
| User has zero aircraft in their fleet | The Selection screen renders an empty-state card (`No aircraft to export. Add an aircraft in Fleet to get started.`) instead of the aircraft list. The Export button is hidden. |
| User clears all aircraft selections | Export button disabled with helper text `Select at least one aircraft`. |
| Very large multi-aircraft selection (e.g. 50+ aircraft) | Generation is per-aircraft and streams to the ZIP; memory remains bounded. Progress indicator includes "Aircraft N of M" prefix. |
| User backgrounds the app during export | Generation continues in the foreground service / appropriate platform mechanism; success state is presented when the user returns. (Background completion notification is future work — see §10.) |
| User navigates back from the Selection screen mid-configuration | Selections are not persisted across navigation. Re-entering Settings → Export logs starts with defaults. |
| Filename collision (re-export same day) | Overwrite without prompting. Different dates keep distinct files. |
| Disk full during write | Abort, surface error toast, no partial file left on disk (write to temp, rename on completion). |
| Cancellation mid-export | Discard temp file. Sheet returns to configuration state. |
| Attachment binary not yet downloaded to this device | `AttachmentManager.ensureLocal()` is called as the export reaches that attachment. Export progress reflects the in-flight downloads. See §4.10. |
| Attachment download fails (network / auth / sha256 mismatch) | Skip the file, mark the CSV cell `name → [download failed]`, record the attachment id in the README's notes. Export succeeds with a textual marker. |
| Attachment never uploaded (PENDING_UPLOAD on this device) | Skip with `[upload pending]` marker. README lists the affected attachments. |
| Anonymous user, attachment is REMOTE_ONLY | Skip with `[download requires sign-in]` marker. Encourages sign-in flow before retrying. |
| Same attachment referenced by multiple logs in the same aircraft | Written to `attachments/` exactly once (keyed by attachment.id). Each referencing CSV row links to the same path. |
| Same attachment referenced across aircraft (rare; copy/share) | Written once **per aircraft folder** for self-containment. The zip remains internally consistent even if a single subfolder is extracted in isolation. |

---

## 7. Success Criteria

### 7.1 Quantitative

- 95th percentile single-aircraft export of an aircraft with 200 log entries and **all attachments already local** completes in **under 5 seconds** on a 2022-era Android mid-range device.
- Same as above with **all attachments REMOTE_ONLY** on a 50 Mbps Wi-Fi link with 50 attachments totaling 80 MB completes in **under 30 seconds**.
- Multi-aircraft export of 10 aircraft × 100 logs each with all attachments local completes in **under 30 seconds**. With attachments to download, scales linearly with attachment payload size.
- Exported zip size: text-only is **under 5 MB**; with attachments, dominated by attachment payload (`attachment.size_bytes` sum).
- Memory ceiling stays under **100 MB** regardless of attachment payload size — attachments stream directly from local files into zip entries without being buffered whole.

### 7.2 Qualitative

- A&P / IA early-access participants can import the single-aircraft export into Google Sheets and locate any log entry by date with zero formatting work.
- Three sample exports (single-engine, twin, with multiple propellers) are reviewed and confirmed by an aviation maintenance professional to be "readable as a logbook".
- Zero crashes or partial files in 100 consecutive synthetic exports across stress-test data (`feature/stresstest`).

### 7.3 Adoption signal (post-launch, instrumented)

- Track an `export_logs_completed` analytics event with selection size, date range preset, and row counts. Target: ≥15% of monthly active aircraft owners trigger one export within 60 days of launch.

---

## 8. Decisions

The questions raised during PRD review are resolved as follows.

1. **Time zone for `Date` columns.** **Decision:** Device-current time zone at export time. Each timestamp renders as `LocalDateTime` in `TimeZone.currentSystemDefault()`. Revisit only if cross-time-zone operators report off-by-one-day issues.
2. **Cert Type rendering.** **Decision:** Prefer the `certificate_type` enum and render `A&P` / `Repairman` accordingly; fall back to the legacy `cert_type` string only when the enum is `CERTIFICATE_TYPE_NONE`.
3. **Cross-component duplication of compliance entries.** **Decision:** MVP routes each log entry to a single tab matching its `component_type`. A single annual that covers airframe + engines + props appears once (in whichever tab the log was filed under, typically airframe). Revisit after early-access feedback — duplication is tracked under §10 Future Work.
4. **Multi-engine indexing.** **Decision:** Numeric `Engine 1, Engine 2, …` ordered by position in `Aircraft.engine[]`. Left/right or other position labels are deferred.
5. **Squawk priority rendering.** **Decision:** Render as `AOG` (industry abbreviation), matching in-app usage. `Aircraft On Ground` is not used.
6. **Compliance tab — Last Complied source.** **Decision:** `Last Complied — Date` / `Last Complied — Hours` reflect only log-derived compliance (the most recent log referencing the task via `inspection_ids`). `MaintenanceTask.force_complied_status` is **not** consulted for the Last-Complied columns. A force-complied task with no backing log shows blank Last-Complied columns; its Status and Next Due columns still reflect `TaskDueManager` output (which does honour force-complied state).

---

## 9. Dependencies & Touch Points

- **`feature/logs/datamanager`** — `MaintenanceLogManager.observeLogs(aircraftId)` already returns the full log list; reuse without modification.
- **`feature/tasks/datamanager`** — `TaskDataManager` for tasks, `TaskDueManager` for status computation, both already implemented.
- **`feature/squawk/datamanager`** — `SquawkManager` for squawks.
- **`feature/fleet/datamanager`** — `FleetManager.observeAircraft()` for whole-fleet enumeration.
- **`feature/technician/datamanager`** — `TechnicianManager` to resolve technician records when a log carries only `technician_id`.
- **`feature/attachment/datamanager`** — `AttachmentManager.ensureLocal()` to download REMOTE_ONLY blobs; `LocalBlobStore.localUri()` to stream bytes into the zip.
- **`core:appinfo`** — read app version / build for the `Export App Version` field.
- **`core:storage` (R1)** — exports read from the local entity store; no Firestore round-trip required.

### New module

A new `feature/export` module. Because export has one user-driven flow (Selection → Progress → Success) and no read-only display surface elsewhere in the app, it uses the canonical layout with `update` as the user-flow submodule:

```
feature/export/
  datamanager/    # ExportManager interface + LogbookExportWriter (CSV + ZIP)
  sharedassets/   # Strings for selection screen and export progress
  update/         # ExportSelectionRoute, ExportSelectionScreen,
                  # ExportViewModel, ExportUiState (sealed: Configuring | Running | Success | Error)
```

The CSV + ZIP writer lives in `commonMain` using `kotlinx.io` primitives. Platform-specific `actual`s only for the file-destination resolver (Documents on iOS, `Downloads/Hopply/` via MediaStore on Android).

### Settings wiring

`feature/settings/` (the existing flat module) gains one new row, **Export logs**, that navigates to the route owned by `feature/export/update`. The route is registered in `composeApp`'s nav graph alongside the other top-level destinations. No structural change to the Settings screen beyond the new row.

---

## 10. Future Work

| Item | Why deferred |
|:---|:---|
| **Direct push to Google Sheets via OAuth** | Best UX (one tap → workbook opens in Sheets), but adds an OAuth flow, new scopes, and a token store. |
| **PDF facsimile** | A printable logbook-page rendering would close the loop for users who still file paper. Adds a PDF generator dependency. |
| **Scheduled / automated exports** | "Email me a backup zip every quarter." Server pipeline needed; out of scope for an on-device-only feature. |
| **Background completion notification** | If the export runs while the app is backgrounded or terminated, post a system notification with the saved path. Needs platform-specific notification plumbing. |
| **Persist last-used selection across sessions** | Remember which aircraft and date range were picked last time. Skipped in MVP to keep state model simple; revisit if users report repetitive picking. |
| **Re-import / round-trip from CSV** | Risk surface is large (data validation, conflict resolution with sync). |
| **Cross-component duplication of compliance entries** | Driven by user feedback after MVP — see §8 Decision 3. |
| **A standalone `99_Attachments_Index.csv` listing every attachment with metadata** | Useful for cataloguing photos and 8130s separately from the per-log columns. The MVP already bundles binaries; an index sheet is purely a cross-reference convenience. |

---

## 11. Out of Scope (explicit)

- Editing or annotating exports inside the app.
- Sharing or collaboration features ("send this export to my A&P from the app").
- Differential / incremental exports ("everything new since my last export").
- Any change to in-app log presentation or data model.
