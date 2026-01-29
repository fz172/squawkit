# Product Requirements Document (PRD): WingsLog

## 1. Product Overview
WingsLog is a digital logbook and fleet management application designed for aircraft owners and mechanics. It serves as a central hub to track aircraft details, manage maintenance logs, ensure regulatory compliance/airworthiness, and perform essential flight planning calculations like Weight & Balance.

## 2. Scope
The application focuses on General Aviation (GA) aircraft management, specifically targeting:
- **Fleet Management**: CRUD operations for aircraft.
- **Maintenance Records**: Detailed logging for all major components (Airframe, Engine, Propeller).
- **Compliance Tracking**: Automated status summary for inspections.
- **Tools**: Weight & Balance calculator.

## 3. Features & Requirements

### 3.1 Fleet Management (Existing/In-Progress)
**Goal**: Provide a dashboard to view the fleet and tools to manage aircraft configuration.

-   **Dashboard**:
    -   Display list of managed aircraft.
    -   Quick summary of status (e.g., Airworthy/Maintenance Due) - *Future*.
-   **Add/Edit Aircraft**:
    -   **Airframe Details**: Make, Model, Serial, Tail Number.
    -   **Powerplant Configuration**:
        -   Support multiple engines per aircraft.
        -   Support multiple propellers per engine (or single prop).
        -   Detailed component tracking (Make, Model, Serial) for Engines, Prop Hubs, and Blades.
    -   **Validation**: Ensure critical identifiers are present (e.g., Serial numbers, Tail numbers).
-   **Delete Aircraft**:
    -   Ability to remove an aircraft and its associated history from the database.

### 3.2 Maintenance Log Management (New)
**Goal**: comprehensive digital record-keeping for maintenance actions.

-   **Log Entry Structure**:
    -   **Type/Category**: Airframe, Engine, or Propeller.
    -   **Component Reference**: Must link to the specific Serial Number of the component worked on (e.g., "Engine 1 Serial #12345").
    -   **Details**: Date, Tach/Hobbs time, Description of work, Signature/Certificate #.
-   **CRUD Operations**:
    -   List logs (filterable by component).
    -   Create new log entry.
    -   Edit existing entry.
    -   Delete entry (with confirmation).
-   **Attachments**:
    -   Goal: Store supporting documentation.
    -   **File Types**:
        -   **PDF**: For official forms (8130, STC paperwork).
        -   **Images**: Photos of damage, repairs, or physical logbook pages.
    -   **Input Methods**:
        -   Select from device file storage.
        -   Capture directly via Camera.

### 3.3 Maintenance Summary & Compliance (New)
**Goal**: Provide an "at-a-glance" view of airworthiness status based on log inputs.

-   **Automated Tracking**:
    -   **Next 100-Hour Inspection**: Calculated based on last inspection Tach time + 100.
    -   **Next Annual Inspection**: Calculated based on last Annual date + 12 calendar months.
    -   **ELT Check**: Due date tracking.
-   **Reminders**:
    -   **Push Notifications**: Alert the operator when service is approaching (e.g., "10 hours until 100hr inspection", "Annual due in 30 days").
    -   **In-App Alerts**: Dashboard banners or highlights for due items.

### 3.4 Weight & Balance (New)
**Goal**: Ensure safe loading of the aircraft.

-   **W&B Calculator Page**:
    -   Define arms/stations for the specific aircraft (configured in Edit Aircraft or separate W&B Profile).
    -   Input weights for passengers, baggage, fuel.
    -   Calculate and display:
        -   Total Weight.
        -   Center of Gravity (CG).
        -   Plot CG on the envelope graph (optional: visual graph).

## 4. Technical Requirements
-   **Database**: **Firebase** (Firestore/Realtime Database) for cloud sync and persistence.
-   **Media Storage**: **Firebase Storage** for documents and images.
-   **Authentication**: Firebase Auth (Google Sign-In, Email/Password).
-   **UI/UX**:
    -   **Material Design 3** base.
    -   **Aesthetic**: Clean, minimalistic, and modern color scheme.
    -   **Usability**: Intuitive operation causing minimal friction for data entry.
    -   Clear distinction between Airframe/Engine/Prop logs.
    -   Easy navigation between Fleet -> Aircraft Details -> Logs/Tools.

## 5. Roadmap / Next Steps
1.  **Phase 1** (Current): Finalize Aircraft Editor (Validation, UI polish).
2.  **Phase 2**: Database Schema update for Logs and Attachments.
3.  **Phase 3**: Log Entry UI (List & Editor) + Attachment handling.
4.  **Phase 4**: Compliance Logic (Summary Page).
5.  **Phase 5**: Weight & Balance Calculator.
