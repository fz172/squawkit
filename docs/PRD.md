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

### 3.3 Maintenance Summary & Compliance (In Progress)

**Goal**: Provide an "at-a-glance" view of airworthiness status based on log inputs.

- **Aircraft Overview Screen** (Implemented):
    - Inspection status displayed as a **2-column card grid** (not horizontal scroll).
    - **Log count stat cards**: Total logs + breakdown by Airframe / Engine / Propeller (live counts
      from Firestore).
    - "Log details" FAB navigates to maintenance log list.
    - Note: "Airworthy" status chip removed — airworthiness determination is deferred to Phase 4.
- **Automated Tracking** (Future):
    - **Next 100-Hour Inspection**: Based on last inspection Tach time + 100.
    - **Next Annual Inspection**: Based on last Annual date + 12 calendar months.
    - **ELT Check**: Due date tracking.
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

- **Database**: **Firebase** (Firestore/Realtime Database) for cloud sync and persistence.
- **Media Storage**: **Firebase Storage** for documents and images.
- **Authentication**: Firebase Auth (Google Sign-In, Email/Password).
- **UI/UX**:
    - **Material Design 3** base.
    - **Aesthetic**: Clean, minimalistic, and modern color scheme.
    - **Usability**: Intuitive operation causing minimal friction for data entry.
    - Clear distinction between Airframe/Engine/Prop logs.
    - Easy navigation between Fleet -> Aircraft Details -> Logs/Tools.

## 5. Roadmap / Next Steps

1. **Phase 1** ✅ Finalize Aircraft Editor (Validation, UI polish).
2. **Phase 2** ✅ Database Schema — `MaintenanceLog` proto with `repeated InspectionType inspection`,
   Firestore subcollection `maintenance_logs`.
3. **Phase 3** ✅ Log Entry UI (List, Create/Edit Form, Delete confirmation) + smart component
   picker. Attachment handling still pending.
4. **Phase 4** (Next): Compliance Logic — automated tracking of inspection due dates (100hr, Annual,
   ELT, Altimeter/Pitot-Static, Transponder). Surface in Aircraft Overview.
5. **Phase 5**: Weight & Balance Calculator.
