# PRD v2: Unified Compliance Tracking

## 1. Objective

Expand WingsLog to track **Service Bulletins (SBs)** and **Airworthiness Directives (ADs)** using a
unified framework that leverages the existing "Inspection Card" logic. This ensures all safety and
compliance requirements are managed in one place.

## 2. User Stories

- **As an Owner/Mechanic**, I want to add an SB to my aircraft with a reference number, description,
  and due requirement.
- **As a Pilot**, I want to see "Immediate" SBs or ADs highlighted as grounding events (Critical
  Airworthiness).
- **As a Mechanic**, I want to select specific SBs I've complied with while filling out a
  Maintenance Log.
- **As an Owner**, I want to see a history of "one-time" SBs that have already been complied with.

## 3. Data Model Enhancements

### 3.1 Compliance Type

Add a `Type` field to the existing `InspectionCard` model:

- `ROUTINE_INSPECTION` (e.g., Annual, 100-Hr)
- `SERVICE_BULLETIN` (SB)
- `AIRWORTHINESS_DIRECTIVE` (AD)

### 3.2 Rule Extensions

Support new compliance triggers:

- **Immediate**: Item is overdue the moment it is added.
- **One-Time**: Once referenced in a `MaintenanceLog`, the item is marked `COMPLIED_WITH` and no
  longer computes a "Next Due" date.
- **Fixed Hour/Date**: Existing `force_due` logic handles this (e.g., "Comply by 500 engine hours").
- **Linked Compliance (Due by Next Major Inspection)**:
    - Item is linked to a specific routine inspection (usually "Annual").
    - The "Next Due" for this item is inherited from the linked inspection's due date.
    - This ensures that if the Annual is due in Dec 2024, the SB is also flagged as due in Dec 2024.
    - If the linked inspection is performed (logged), this item must also be marked as completed in
      that same log, or it becomes immediately **OVERDUE**.

## 4. UI/UX Changes

### 4.1 Unified "Compliance" Section

The "Inspection Status" section on the Aircraft Overview screen will be renamed to **Compliance &
Inspections**.

- **Visual Distinction**: ADs and SBs should have distinct icons/tags to differentiate them from
  standard maintenance.
- **Filtering**: A tab or toggle to filter between *Active* (Due) and *History* (Complied one-time
  items).

### 4.2 SB/AD Entry Form

A specialized version of the "Add Inspection" sheet:

- **Reference Number**: Input for the official bulletin ID (e.g., SB-2024-01).
- **Origin/Component**: Mandatory selection of where the SB originates (Airframe, Engine, Propeller,
  or Avionics).
- **Requirement Picker**: "Immediate", "By Hours", "By Date", or "Next Inspection".
- **SB Link (URL)**: Field to store a direct link to the official manufacturer's PDF or website for
  the bulletin.
- **Compliance Details**: A rich text or multi-line area for detailed compliance instructions, part
  numbers, or specific serial number ranges affected.

### 4.3 Integrated Logging

The "Select Inspection Work" picker in the Maintenance Log form will now include all active SBs and
ADs. Checking them off will record compliance in the permanent log.

## 5. Success Metrics

- Zero confusion between standard inspections and one-time SBs.
- Reduced "Grounding" risk by highlighting immediate ADs/SBs in the Critical section.
- 100% data reuse of the existing Inspection/Log linkage.
