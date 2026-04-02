# Design Doc: Unified Compliance & Inspection System

## 1. Overview
This document outlines the technical implementation for WingsLog v2, integrating **Service Bulletins (SBs)** and **Airworthiness Directives (ADs)** into the existing Inspection framework. 

## 2. Data Model Changes (Protobuf)

### 2.1 Inspection Card Extensions
We will modify `inspection_card.proto` to support different compliance categories and metadata.

```protobuf
enum ComplianceType {
  COMPLIANCE_TYPE_ROUTINE_INSPECTION = 0;
  COMPLIANCE_TYPE_SERVICE_BULLETIN = 1;
  COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE = 2;
}

message LinkedRule {
  string parent_inspection_id = 1; // e.g., ID of the "Annual" card
}

message ImmediateRule {}

message InspectionRule {
  oneof rule {
    TimeRule time_rule = 1;
    EngineHourRule engine_hour_rule = 2;
    OnConditionRule on_condition_rule = 3;
    LinkedRule linked_rule = 4;    // NEW
    ImmediateRule immediate_rule = 5; // NEW
  }
}

message InspectionCard {
  string id = 1;
  string title = 2;
  InspectionComponentType component = 3;
  repeated InspectionRule rules = 4;
  
  // v2 Additions
  ComplianceType type = 8;
  string reference_number = 9; // e.g., "SB-2024-01"
  string compliance_authority = 10;
  string compliance_details = 11;
  bool is_one_time = 12; // If true, moves to history after first log
  
  // ... existing force_due fields ...
}
```

## 3. Logic & State Management

### 3.1 The `ComplianceManager` (Refactor of InspectionManager)
The logic for `computeNextDue` must be expanded:
1.  **Immediate**: Always returns `DueStatus.OVERDUE`.
2.  **Linked**: 
    - Fetch the `parent_inspection_id`.
    - Retrieve the calculated `DueMetadata` for that parent.
    - Inherit the `nextDueDate` or `nextDueEngine` from the parent.
3.  **One-Time Completion**: 
    - When a `MaintenanceLog` contains an `inspection_id` where `is_one_time == true`, the `DueStatus` becomes `COMPLIED`.
    - These items will be filtered out of the active "Due" list.

### 3.2 Component-Specific Tracking
The `Origin/Component` field determines which metric to use:
- **Engine/Propeller/Avionics**: Tracks against `engine_hour`.
- **Airframe**: Tracks against `airframe_time`.

## 4. UX & UI Components

### 4.1 "Compliance & Inspections" Section (Overview)
- **Visual Hierarchy**: 
    - **ADs**: Red "AD" badge + Warning icon.
    - **SBs**: Amber "SB" badge.
    - **Inspections**: Standard Blue icon.
- **Filtering**: Add a toggle for `Active` vs `History (Complied)`.

### 4.2 Entry Flow: The "Compliance Creator"
A new sheet or a mode in `AddInspectionSheet`:
- **Step 1**: Select Type (Inspection vs SB/AD).
- **Step 2**: If SB/AD, show "Reference Number" and "Origin" fields.
- **Step 3**: "Due Strategy" picker:
    - *Linked*: Show a dropdown of existing routine inspections (Annual, etc.).
    - *Immediate*: No further inputs.
    - *Interval*: Existing Time/Engine hour inputs.
- **Step 4**: Compliance Authority and Compliance Detail text areas.

### 4.3 Log Integration
The `InspectionPickerSheet` used during maintenance logging will now group items by type:
- **Section 1: Due Items** (Inspections/SBs that are currently due).
- **Section 2: All Compliance** (Searchable list of all ADs/SBs).

## 5. Implementation Phases
1.  **Phase 1**: Update Protos and regenerate code.
2.  **Phase 2**: Implement `Immediate` and `Linked` logic in `InspectionManagerImpl`.
3.  **Phase 3**: Build the specialized SB/AD entry UI.
4.  **Phase 4**: Update Overview and Log Form to handle the new grouping/history.
