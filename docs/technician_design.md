# Technician Feature Design Document

## 1. Overview
The goal is to allow users to specify "who did the work" (a Technician) for each maintenance log entry. The user can either leave it empty or select a technician from a predefined list.

Key constraints:
- **Lightweight Profile**: A Technician has a `name` (required), `cert type` (optional), `number` (optional), and `expiration` (optional).
- **Snapshot Behavior**: The maintenance log must capture a snapshot of the technician's data at the time of assignment. If the technician's underlying profile changes, existing logs will **not** update automatically.
- **Re-assignment**: Switching from Technician A to Technician B, and then back to Technician A should refresh Technician A's data on the log to their *current* profile data.

## 2. Module Architecture
The feature will be housed in its own dedicated domain module (`feature/technician`), while foundational data models will reside in `core/model`.

- **`core/model`**: Contains the protobuf definition (`technician.proto`). Since `maintenance_log.proto` is here, `technician.proto` must be here as well for proper compilation and cross-referencing.
- **`feature/technician/datamanager`**: Contains the `TechnicianManager` interface and implementation (`TechnicianManagerImpl`) to handle CRUD operations to Firestore.
- **`feature/technician/manage`**: Contains the UI for managing technicians, the picker sheet, and the add/edit form components. 
- **`feature/technician/sharedassets`**: Contains shared resources such as UI text strings, localized labels, and icons related to the technician feature, to be reused across modules.

## 3. Data Structure Design
We will introduce a new entity, `Technician`, and embed it within the `MaintenanceLog` entity.

### 3.1. Protobuf Models
**1. New `Technician` Model (`core/model/src/commonMain/proto/aircraft/technician.proto`)**
This represents the reusable technician profile stored at the user level, and will also be embedded into logs.
```proto
syntax = "proto3";

import "google/protobuf/timestamp.proto";

option java_package = "dev.fanfly.wingslog.aircraft";
option java_multiple_files = true;

message Technician {
  string id = 1;
  string name = 2; // Required
  string cert_type = 3; // Optional
  string cert_number = 4; // Optional
  google.protobuf.Timestamp cert_expiration = 5; // Optional
}
```

**2. Update `MaintenanceLog` Model (`core/model/src/commonMain/proto/aircraft/maintenance_log.proto`)**
We will add the `Technician` message as a field to `MaintenanceLog`. Because this is stored as a nested object in NoSQL/Firestore, it inherently acts as a snapshot.
```proto
// (Inside maintenance_log.proto)

import "aircraft/technician.proto";

message MaintenanceLog {
  // Existing fields...
  // 3: technician_id is deprecated or can be removed if not heavily used

  // New field
  Technician technician = 13; 
}
```

### 3.2. Database Layer (in `feature/technician/datamanager`)
- **Firestore Collection**: `users/{userId}/technicians/{technicianId}`
- **Repository**: Create a `TechnicianManager` (and `TechnicianManagerImpl`) to handle CRUD operations on the `technicians` collection.

## 4. UI Design (in `feature/technician/manage`)

### 4.1. Technician Form UI (Shared)
- **Add/Edit Technician Form (`EditTechnicianScreen` or `AddTechnicianDialog`)**: A shared component or screen that allows adding a new technician or editing an existing one.
  - **Fields**: 
    - Name (Text field, Required)
    - Certificate Type (Text field, Optional)
    - Certificate Number (Text field, Optional)
    - Expiration Date (Date Picker, Optional)
  - Used by both the Picker (from log form) and the Settings management screen.

### 4.2. Settings Management UI
We need a dedicated screen accessible from the app's Settings to manage technicians independently of the log flow.
- **Manage Technicians Screen (`TechnicianListScreen`)**: 
  - Lists all saved technicians.
  - Displays name, cert type, and number for each.
  - Tapping a technician opens the `EditTechnicianScreen`.
  - Includes a FAB (Floating Action Button) or '+' button to add a new technician.
  - Provides a mechanism to **delete** a technician (e.g., via swipe-to-delete, or a delete button in the edit screen). Note: Deleting a technician will NOT remove them from existing logs, since logs hold a snapshot.

### 4.3. Maintenance Log Entry UI Integration
In the `MaintenanceLogFormScreen` (located in `feature/maintenance/update`):
- **Technician Section**: A new section (e.g., below the work description or date) labeled "Performed By".
- **Empty State**: Shows "Unassigned" or "Select Technician" with a '+' or 'person' icon.
- **Selected State**: Displays the selected Technician's Name and Certificate details (e.g., "John Doe - A&P 123456").
- **Interaction**:
  - Tapping the section opens a `TechnicianPickerSheet` (provided by `feature/technician/manage`).
  - The picker allows the user to select an existing technician, clear the selection (set to empty), or navigate to the "Add/Edit Technician" form to create a new one.
  - Upon selection, the `MaintenanceLogFormViewModel` captures the *current* state of the selected `Technician` and embeds it into the log. This fulfills the requirement that re-selecting A fetches A's current data.
