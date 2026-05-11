# PRD: User Squawking Support

**Status:** Draft
**Last updated:** 2026-05-10

---

## 1. Overview

In aviation maintenance, a "squawk" refers to an identified issue, defect, or discrepancy that requires attention. Squawks are **ad-hoc** — they arise during inspection or operation and do not have a scheduled due date. This distinguishes them from maintenance tasks (`feature/tasks`), which define recurring, schedule-driven inspection requirements.

This feature allows users to quickly document anomalies and close the loop by linking maintenance log entries directly to these squawks.

---

## 2. Squawks vs. Maintenance Tasks

| Dimension | Squawk | Maintenance Task |
|-----------|--------|-----------------|
| Origin | Ad-hoc — discovered in the field | Pre-defined — recurring compliance requirement |
| Due date | None | Tach-based or calendar-based |
| Lifecycle | Open → Addressed | Due → Complied |
| Closed by | Linking a log entry | Logging an inspection of the matching type |
| Priority | Yes (Low / Medium / High / AOG) | N/A — urgency comes from due status |

Squawks and tasks are **orthogonal**. A squawk does not replace or derive from a task.

---

## 3. Goals and Objectives

- **Enable Ad-hoc Reporting:** Allow users to quickly create squawks during inspections or operations without needing a pre-defined task.
- **Traceability:** Create a clear link between an identified problem (Squawk) and the documented resolution (Log Entry).
- **Workflow Consistency:** Leverage the existing log-entry creation flow to resolve squawks.
- **Status Tracking:** Provide visibility into which squawks are Open and which have been Addressed.
- **AOG Visibility:** Surface aircraft-grounding squawks prominently on the Aircraft Overview screen, separate from overdue inspection tasks.

---

## 4. User Stories

| ID | User Role | User Story |
|:---|:---|:---|
| US.1 | Technician | As a technician, I want to quickly create a new squawk when I notice an unexpected defect so that I don't forget to report it later. |
| US.2 | Technician | As a technician, I want to link a log entry to one or more open squawks so that I can formally document that the required repair has been completed. |
| US.3 | User | As a user, I want to view a list of all open squawks so that I can track outstanding issues. |
| US.4 | User | As a user, I want to see which log entry addressed a squawk so that I have a complete repair history. |

---

## 5. Functional Requirements

### 5.1 Squawk Creation

- Users can create a new squawk with the following attributes:
    - **Title** — Required. Short summary of the issue.
    - **Description** — Optional. Detailed findings.
    - **Priority** — Required. `Low`, `Medium`, `High`, `AOG`. See §8 for AOG behavior.
    - **Status** — System-managed. `Open` on creation; `Addressed` when a log entry is linked.
    - **Component** — Optional. Same smart component picker used in the maintenance log form (Airframe, Engine, Propeller). Selecting a component auto-fills serial number.
    - **Attachments** — Fully supported via `feature/attachment`. Files, images, PDFs, and links can be attached at create or edit time.

### 5.2 Squawk Management

- **Squawk List View:** A list displaying all squawks for the selected aircraft, filterable by status and priority.
- **Status Transitions:**
    - **Open → Addressed:** Triggered automatically when a log entry is saved with this squawk linked.
    - **Addressed → Closed:** Deferred to a future release. For V1, Addressed is the terminal state.

### 5.3 Log ↔ Squawk Linkage

#### Data model

- `Squawk` stores `addressed_by_log_id` — the ID of the log entry that addressed it. Empty means Open; populated means Addressed.
- `MaintenanceLog` stores `squawk_ids` (repeated) — the squawks addressed by this log entry.

A single log entry can address multiple squawks (a mechanic fixes two issues in one repair session). Bidirectional: from a squawk you can navigate to its resolving log; from a log entry you can see which squawks it closed.

*Proto definition, Firestore collection path, and module layout are deferred to the design document.*

#### Addressing flow

When creating or editing a **Log Entry**:
1. An "Address Squawk(s)" section appears in the form.
2. Tapping it opens a picker showing all **Open** squawks for that aircraft (scoped to the current aircraft only — not fleet-wide).
3. The user selects one or more squawks.
4. On Save: `MaintenanceLog.squawk_ids` is written; `SquawkManager` updates each referenced squawk — setting `addressed_by_log_id` and transitioning status to Addressed.

#### Cascade on deletion

Deferred to design document.

---

## 6. User Flows

### Flow A — Reporting a Squawk

1. User navigates to the **Squawks** screen for an aircraft.
2. Taps **"New Squawk"**.
3. Enters Title, Priority, Description; optionally selects a component and adds attachments.
4. Taps **Save** — squawk is created with status **Open**.

### Flow B — Addressing a Squawk

1. User opens the **Log Entry** create or edit screen.
2. Taps **"Address Squawk(s)"** in the form.
3. Selects one or more **Open** squawks from the picker (aircraft-scoped, searchable).
4. Completes the rest of the log entry.
5. Taps **Save** — log is written; each selected squawk transitions to **Addressed** with `addressed_by_log_id` set.

---

## 7. UI/UX Requirements

- **Visual Cues:** Color-code squawk priority chips — AOG: `error`, High: `errorContainer`, Medium: `tertiary`, Low: `surfaceVariant`.
- **"Address Squawk(s)" placement:** In the log form, below the Technician section and above the attachment section.
- **Squawk Tab:** A dedicated **Squawks** tab is the primary home for the squawk list and add/edit flows. It sits between the Overview tab and the Tasks tab, making the Aircraft Overview tab order: **Overview → Squawks → Tasks → Logs**.
- **Squawk List:** Displayed within the Squawks tab. Grouped by status (Open first, Addressed below); within each group, sorted by priority descending (AOG → High → Medium → Low), then by creation date.
- **Addressed row:** Shows the linked log date and a "→ View Log" chip.
- **Add Squawk:** FAB on the Squawks tab navigates to `AddSquawkScreen` (full-screen). Edit navigates to `EditSquawkScreen`.

---

## 8. AOG Behavior

AOG (Aircraft on Ground) means the aircraft is grounded until the squawk is resolved. The app surfaces AOG squawks prominently but keeps them **visually distinct** from overdue inspection tasks.

- **Aircraft Overview — Overview tab:**
    - An `AogAlertSection` appears above `CriticalAlertSection` (overdue tasks) when one or more AOG squawks exist.
    - Uses `error` tint and a distinct icon (`FlightLandOutlined` or similar) to distinguish from the overdue-tasks alert.
    - Each AOG squawk is shown as a row with priority chip, title, and a "View Squawks" action that switches to the Squawks tab.
    - Section collapses when no AOG squawks are open.

- **Fleet Dashboard:**
    - Aircraft card shows a small AOG badge (similar to how due-status is shown today) when any AOG squawk is open.

- **No flight blocking:** The app does not technically block log entry creation for a grounded aircraft — this is a display concern only. Regulatory enforcement is the user's responsibility.

---

## 9. Attachments

Fully supported via `feature/attachment`. Squawks use the same attachment UX as maintenance logs: add-during-form, per-parent 25 MB cap, per-user 1 GB cap, background upload, lazy download on other devices, sync-state badge on each attachment row.

---

## 10. Out of Scope (V1)

- Deferred squawk status (MEL-based deferral — a known squawk explicitly deferred per Minimum Equipment List rules). Future release.
- Multi-user squawk assignment or commenting.
- Automated parts procurement based on squawk content.
- Integration with external third-party maintenance scheduling systems.
- Real-time push notifications.
- Cascade behavior when a resolving log entry is deleted (deferred to design document).
