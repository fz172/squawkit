# Product Requirements Document (PRD): WingsLog

## 1. Product Overview

WingsLog is a digital logbook and fleet management application designed for aircraft owners and
mechanics. It serves as a central hub to track aircraft details, manage maintenance logs, ensure
regulatory compliance/airworthiness, and perform essential flight planning calculations like
Weight & Balance.

> **Implementation status (2026-05).** The local-first storage architecture (R1) and the **Squawk** feature
> are both shipped. **Attachments** (R2) infrastructure has largely landed but the UI is gated behind the
> `attachmentUploadEnabled` feature-lab flag. The **Logbook Export** feature shipped and grew a server-side
> **email-delivery** capability (a Firebase Functions backend under `backend/`). Weight & Balance remains future work.

## 2. Scope

The application focuses on General Aviation (GA) aircraft management, specifically targeting:

- **Fleet Management**: CRUD operations for aircraft.
- **Maintenance Records**: Detailed logging for all major components (Airframe, Engine, Propeller).
- **Compliance Tracking**: Automated status summary for inspections.
- **Squawks**: Ad-hoc defect/discrepancy tracking with Open → Addressed/Dismissed lifecycle and AOG visibility.
- **Logbook Export**: On-device PDF/CSV/XLSX export (per-aircraft or fleet ZIP) with optional email delivery.
- **Attachments**: Files/images/PDFs on logs, tasks, and squawks (R2; behind feature flag).
- **Tools**: Weight & Balance calculator (future).

## 3. Features & Requirements

### 3.1 Fleet Management (Existing/In-Progress)

**Goal**: Provide a dashboard to view the fleet and tools to manage aircraft configuration.

- **Dashboard**:
    - Display list of managed aircraft.
    - Quick summary of status (e.g., Airworthy/Maintenance Due) - *Future*.
- **Add/Edit Aircraft**:
    - **Airframe Details**: Make, Model, Serial, Tail Number.
    - **Powerplant Configuration**:
        - Support multiple engines per aircraft.
        - Support multiple propellers per engine (or single prop).
        - Detailed component tracking (Make, Model, Serial) for Engines, Prop Hubs, and Blades.
    - **Validation**: Ensure critical identifiers are present (e.g., Serial numbers, Tail numbers).
- **Delete Aircraft**:
    - Ability to remove an aircraft and its associated history from the database.

### 3.2 Maintenance Log Management (Implemented)

**Goal**: Comprehensive digital record-keeping for maintenance actions.

- **Log Entry Structure**:
    - **Type/Category**: Airframe, Engine, or Propeller.
    - **Component Reference**: Smart component picker — loads actual aircraft data (engines, prop
      hub/blades) from Firestore. Selecting a component auto-fills the serial number. No manual
      serial entry.
        - ENGINE: dropdown of configured engines (make/model/serial).
        - PROPELLER: dropdown of hub + individual blades with their serials.
        - AIRFRAME: auto-fills aircraft airframe serial.
    - **Details**: Tach time, description of work.
    - **Inspection Types**: Multi-select from enum: `ANNUAL`, `HUNDRED_HOUR`, `ROUTINE`,
      `TRANSPONDER_CHECK`, `CONDITIONAL`, `OIL_CHANGE`, `ELT`, `ALTIMETER_PITOT_STATIC`. A single
      log entry can represent multiple inspection types simultaneously.
- **CRUD Operations**:
    - List logs (ordered by timestamp descending).
    - Create new log entry.
    - Edit existing entry.
    - Delete entry (with confirmation dialog ✅).
- **Attachments** (Implemented — R2; UI gated behind `attachmentUploadEnabled` feature-lab flag):
    - Store supporting documentation on logs, tasks, and squawks. Local-first blob store with background
      upload (WorkManager / URLSession) and lazy download on other devices.
    - **File Types**: PDF (8130, STC paperwork), Images (photos, physical logbook pages), generic files, links.
    - **Input Methods**: Device file storage, Camera capture.

### 3.3 Maintenance Summary & Compliance (Implemented)

**Goal**: Provide an "at-a-glance" view of airworthiness status based on log inputs.

- **Aircraft Overview Screen** (Implemented — 4-tab layout: **Overview → Squawks → Tasks → Logs**):
    - **Overview tab**: Hero header (make/model, tail number), `ConfigurationCard` accordion, `AogAlertSection` (open AOG squawks) above `CriticalAlertsSection` (overdue tasks), log stats / onboarding card.
    - **Squawks tab**: Open / Closed segmented filter; `SquawkCard` rows; detail sheet; "Add Squawk" FAB.
    - **Maintenance Tasks tab**: Active and complied tasks with a due/history toggle. `TaskCard` rows with due status. "Add Task" action.
    - **Logs tab**: Embedded `MaintenanceLogListContent` with search bar, component filter, log cards, detail sheet, and "Add Log" FAB.
    - The FAB is context-sensitive to the active tab. Tab selection resets to Overview on back-stack re-entry.
- **Compliance Tracking** (`feature/tasks` — Implemented):
    - Each task carries a `ComplianceType`: `ROUTINE_INSPECTION`, `SERVICE_BULLETIN`, or `AIRWORTHINESS_DIRECTIVE`. Type is shown as a chip in the UI.
    - ADs and SBs additionally store `reference_number` (e.g., "AD 2019-20-10"), `compliance_authority`, `compliance_details`, and an `is_one_time` flag (one-time ADs move to history after first log).
    - Scheduling rules: time-based (`TimeRule`), engine-hour-based (`EngineHourRule`), on-condition (`OnConditionRule`), linked to another task (`LinkedRule`), or immediate (`ImmediateRule`).
    - `TaskDueManager` computes `DueStatus` per task by comparing last-complied log entry against the task's schedule rules (tach-based or calendar-based).
    - Overdue and due-soon tasks surface in `CriticalAlertSection` on the Overview tab.
- **Reminders** (Future):
    - **Push Notifications**: Alert when service approaching.
    - **In-App Alerts**: Dashboard banners for due items.

### 3.4 Squawks (Implemented — `feature/squawk`)

**Goal**: Track ad-hoc defects/discrepancies that arise outside the scheduled-inspection workflow.

- Create a squawk with title (required), priority (`Low`/`Medium`/`High`/`AOG`), description, component, and attachments.
- Lifecycle: **Open → Addressed** (link a maintenance log) or **Open → Dismissed** (with a reason: Obsolete /
  Not Reproducible / Duplicate / Intended Behavior); dismissed squawks can be **Reopened**.
- Bidirectional linkage: `MaintenanceLog.squawk_ids` ↔ `Squawk.addressed_by_log_id`. Linking from either the
  log form (`SquawkPickerSheet`) or the squawk form (`LogPickerSheet`).
- AOG squawks surface in `AogAlertSection` on the Aircraft Overview tab. (Fleet-dashboard AOG badge is **not yet** built.)
- See `docs/user_squawking_prd.md` and `docs/squawk_design.md`.

### 3.5 Logbook Export (Implemented — `feature/export`)

**Goal**: Produce a portable, spreadsheet-friendly copy of all logbook data for handoff, backup, and pre-buy.

- Entry point: **Settings → Export logs**. Select one aircraft or the whole fleet, an optional date range, and options.
- Output: a ZIP containing a PDF, CSV files (paper-logbook tab layout), an XLSX workbook, attachments, and a README,
  saved to Files (iOS) / Downloads (Android). Reads run entirely against the local-first store.
- **Email delivery**: optionally email the export via the `requestExportDelivery` Firebase Function (signed URL +
  mailer + Firestore manifest). Export history supports re-send, retry, and delete.
- See `docs/export_logs_PRD.md`, `docs/export_logs_design.md`, and `docs/export_email_automation_design.html`.

### 3.6 Weight & Balance (Future)

**Goal**: Ensure safe loading of the aircraft.

- **W&B Calculator Page**:
    - Define arms/stations for the specific aircraft (configured in Edit Aircraft or separate W&B
      Profile).
    - Input weights for passengers, baggage, fuel.
    - Calculate and display:
        - Total Weight.
        - Center of Gravity (CG).
        - Plot CG on the envelope graph (optional: visual graph).

## 4. Technical Requirements

- **Local Storage**: **SQLDelight** (SQLite) — primary on-device store for all proto-backed domain data (local-first architecture, R1 **shipped**; the only read/write path).
- **Cloud Sync**: **Firebase Firestore** — push/pull sync engine (`feature/sync/data`) for permanent signed-in users with sync enabled. Anonymous users work fully offline with no sync.
- **Media Storage**: **Firebase Storage** for attachment files and images (R2 — substantially implemented, behind feature flag).
- **Backend**: **Firebase Cloud Functions** (`backend/firebase/functions/`, TypeScript) — `requestExportDelivery` (export email delivery) and `health_probe`.
- **Authentication**: Firebase Auth (Google Sign-In, Email/Password, Anonymous).
- **UI/UX**:
    - **Material Design 3** base.
    - **Aesthetic**: Clean, minimalistic, and modern color scheme (Refined Minimalism — see `.impeccable.md`).
    - **Usability**: Intuitive operation causing minimal friction for data entry.
    - Clear distinction between Airframe/Engine/Prop logs.
    - Easy navigation between Fleet → Aircraft Overview (3 tabs) → Logs/Tasks.

## 5. Roadmap / Next Steps

1. **Phase 1** ✅ Finalize Aircraft Editor (Validation, UI polish).
2. **Phase 2** ✅ Database Schema — `MaintenanceLog` proto with `repeated InspectionType inspection`,
   Firestore subcollection `maintenance_logs`.
3. **Phase 3** ✅ Log Entry UI (List, Create/Edit Form, Delete confirmation) + smart component
   picker. Attachment handling still pending.
4. **Phase 4** ✅ Compliance Logic — `feature/tasks` with `TaskDataManager` + `TaskDueManager`.
   `DueStatus` computed per task; surfaced in Aircraft Overview (Maintenance Tasks tab + CriticalAlertSection).
5. **Phase 5** ✅ Local-first storage (R1): SQLDelight entity store + Firestore sync engine. Shipped as the
   default and only data path (no rollout flag). See `docs/storage_r1_design.md`.
6. **Phase 6** ✅ Squawks — ad-hoc defect tracking (`feature/squawk`), Open → Addressed/Dismissed lifecycle,
   AOG alert on Aircraft Overview, log↔squawk linkage. See `docs/squawk_design.md`.
7. **Phase 7** (Substantially implemented — behind feature flag) — Attachments R2: local blob store, background
   upload, lazy download on logs/tasks/squawks. Gated behind `attachmentUploadEnabled`. See `docs/storage_r2_design.md`.
8. **Phase 8** ✅ Logbook Export (`feature/export`) — on-device PDF/CSV/XLSX ZIP export, per-aircraft or fleet,
   plus optional server-side email delivery (Firebase Functions backend). See `docs/export_logs_PRD.md` and
   `docs/export_email_automation_design.html`.
9. **Phase 9** (Future) — Weight & Balance Calculator.
10. **Phase 10** (Future) — Intelligent Search (FTS5 + vector embeddings). See `docs/intelligentsearch.md`.
