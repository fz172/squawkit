# Product Requirements Document (PRD): WingsLog

## 1. Product Overview

WingsLog is a digital logbook and fleet management application designed for aircraft owners and
mechanics. It serves as a central hub to track aircraft details, manage maintenance logs, ensure
regulatory compliance/airworthiness, and perform essential flight planning calculations like
Weight & Balance.

## 2. Scope

The application focuses on General Aviation (GA) aircraft management, specifically targeting:

- **Fleet Management**: CRUD operations for aircraft.
- **Maintenance Records**: Detailed logging for all major components (Airframe, Engine, Propeller).
- **Compliance Tracking**: Automated status summary for inspections.
- **Tools**: Weight & Balance calculator.

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
- **Attachments**:
    - Goal: Store supporting documentation (not yet implemented).
    - **File Types**: PDF (8130, STC paperwork), Images (photos, physical logbook pages).
    - **Input Methods**: Device file storage, Camera capture.

### 3.3 Maintenance Summary & Compliance (Implemented)

**Goal**: Provide an "at-a-glance" view of airworthiness status based on log inputs.

- **Aircraft Overview Screen** (Implemented — 3-tab layout):
    - **Overview tab**: Hero header (make/model, tail number), `ConfigurationCard` accordion, `CriticalAlertsSection` (overdue tasks), log stats / onboarding card.
    - **Maintenance Tasks tab**: Active and complied tasks with a due/history toggle. `TaskCard` rows with due status. "Add Task" action.
    - **Logs tab**: Embedded `MaintenanceLogListContent` with search bar, component filter, log cards, detail sheet, and "Add Log" FAB.
    - Tab selection resets to Overview on back-stack re-entry.
- **Compliance Tracking** (`feature/tasks` — Implemented):
    - Maintenance tasks define recurring inspection requirements (100-hr, Annual, ELT, Altimeter/Pitot-Static, Transponder, AD compliance, etc.).
    - `TaskDueManager` computes `DueStatus` per task by comparing last-complied log entry against task schedule (tach-based or calendar-based).
    - Overdue and due-soon tasks surface in `CriticalAlertSection` on the Overview tab.
- **Reminders** (Future):
    - **Push Notifications**: Alert when service approaching.
    - **In-App Alerts**: Dashboard banners for due items.

### 3.4 Weight & Balance (New)

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

- **Local Storage**: **SQLDelight** (SQLite) — primary on-device store for all proto-backed domain data (local-first architecture, R1 in progress).
- **Cloud Sync**: **Firebase Firestore** — push/pull sync engine (`feature/sync/data`) for permanent signed-in users. Anonymous users work fully offline with no sync.
- **Media Storage**: **Firebase Storage** for attachment files and images (R2).
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
5. **Phase 5** (In Progress) — Local-first storage (R1): SQLDelight entity store + Firestore sync engine.
   See `docs/storage_r1_design.md`.
6. **Phase 6** (Planned) — Attachments R2: local blob store, background upload, lazy download.
   See `docs/storage_r2_design.md`.
7. **Phase 7** (Future) — Weight & Balance Calculator.
8. **Phase 8** (Future) — Intelligent Search (FTS5 + vector embeddings). See `docs/intelligentsearch.md`.
