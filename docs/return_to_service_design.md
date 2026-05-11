# Return to Service (FAR 43.9) Design

**Issue:** #49
**Status:** Draft
**Last updated:** 2026-05-10
**UI goal:** Minimal — one toggle on the form, one block at the bottom of the detail sheet, one icon on the card.

---

## 1. What FAR 43.9 requires

Every maintenance record entry must contain:

1. Description of work performed — ✅ `work_description`
2. Date of completion — ✅ `timestamp`
3. Name of person performing the work — ✅ `technician` snapshot
4. Certificate type and number of the person approving return to service — ⬜ **missing**
5. A statement of approval: *"I certify that the work identified above was accomplished in accordance with the requirements of Part 43 of the Federal Aviation Regulations and that the aircraft is approved for return to service."* — ⬜ **missing**

Items 4 and 5 are the only gaps. The approver is often the same person who performed the work, but not always — an IA (Inspection Authorisation holder) must approve annual inspections even when an A&P did the physical work.

---

## 2. Data model

### New proto message

Add to `core/model/src/commonMain/proto/aircraft/maintenance_log.proto`:

```proto
// Approval block for FAR 43.9 return to service.
// Absence means the log entry has not been approved for RTS (draft / work-in-progress).
message ReturnToService {
  Technician approver = 1;             // Snapshot at time of approval
  google.protobuf.Timestamp approved_at = 2;
}
```

Embed in `MaintenanceLog` as field 14:

```proto
message MaintenanceLog {
  // ... existing fields 1–13 unchanged ...
  ReturnToService return_to_service = 14;  // optional; absent = not approved
}
```

**Design decisions:**

- **Boolean, not an enum.** "Approved" vs. "not approved" covers all practical cases. A 3-state enum (Pending / Approved / Closed) adds complexity with no UX benefit — a draft log simply has no `return_to_service` field set.
- **Snapshot, not a foreign key.** The approver's cert details are embedded at approval time (same pattern as `MaintenanceLog.technician`). Changing the technician's profile later does not alter the signed record.
- **Separate from `technician`.** The performer and the approver are different roles. Both are optional, both are snapshots. A log may have a performer with no approval yet, or both simultaneously.

### No manager changes

`MaintenanceLogManager.addLog` / `updateLog` already persist the full proto. The RTS block rides along automatically.

---

## 3. Form UI (`feature/logs/update`)

### Placement

Insert a single **"Approved for Return to Service"** row immediately after the Inspection Tasks section and before Engine Time. When unchecked (the default), it occupies one row — no vertical expansion.

**Current form order:**
1. Maintenance Date
2. Work Description
3. Performed By (technician)
4. Inspection Tasks
5. ← **Return to Service row inserted here**
6. Engine Time
7. Airframe Time
8. Prop Time
9. Component
10. Attachments

### Collapsed state (default)

A single `Row` with a `Checkbox` + label:

```
☐  Approved for Return to Service
```

No other UI visible. Zero vertical footprint beyond one row.

### Expanded state (checkbox checked)

The row expands to show two read-only fields below the checkbox:

```
☑  Approved for Return to Service

   Approved by   [John Doe — A&P 123456  ▾]
   Date          May 10, 2026
```

- **Approved by** — a tappable read-only field. Defaults to the "Performed By" technician already selected on the form. Tapping opens `TechnicianPickerSheet` (existing component, reused as-is) so the user can pick a different approver (e.g., an IA).
- **Date** — non-editable, displays today's date. Not a picker — FAR 43.9 requires the date the approval was given, which is today.

If "Performed By" is unset when the user checks the box, "Approved by" starts blank and shows the same "Select Technician" placeholder the form already uses.

### Checking / unchecking behaviour

- **Check** → `approved_at` set to today; `approver` defaults to `selectedTechnician` (snapshot taken at that moment).
- **Uncheck** → `ReturnToService` cleared from state entirely (`return_to_service = null`).
- **Changing "Performed By" after checking** → does NOT automatically update "Approved by". The approver is independently selected; changing the performer is a separate action.

### ViewModel state additions

```kotlin
// Inside MaintenanceLogFormUiState
val returnToService: ReturnToServiceDraft? = null

data class ReturnToServiceDraft(
  val approver: Technician,      // snapshot
  val approvedAt: LocalDate,     // today at time of toggle
)
```

```kotlin
// New events on MaintenanceLogFormViewModel
fun onRtsToggled(checked: Boolean)          // check: default approver = selectedTechnician; uncheck: clear
fun onRtsApproverSelected(tech: Technician) // from TechnicianPickerSheet
```

`save()` maps `ReturnToServiceDraft` to the `ReturnToService` proto message before calling `logManager.addLog` / `updateLog`.

---

## 4. Card UI (`feature/logs/viewing` — `MaintenanceLogCard`)

**Minimal change: one icon added to the existing footer row.**

Current footer row:
```
[Date]                    [task count]  [technician name]
```

When `return_to_service` is set, add a small `CheckCircleOutline` icon (18 dp, `primary` tint) to the right of the technician name:

```
[Date]                    [task count]  [technician name]  ✓
```

No new row. No new label. The icon is self-explanatory in context; a tooltip or content description of "Approved for return to service" covers accessibility.

When `return_to_service` is absent, no change to the card at all.

---

## 5. Detail sheet UI (`feature/logs/viewing` — `MaintenanceLogDetailSheet`)

Add a **Return to Service block** at the very bottom of the sheet, after Attachments, before the footer divider. Only rendered when `return_to_service != null`.

```
──────────────────────────────────
Return to Service

Approved by  John Doe · A&P · 123456
Date         May 10, 2026

"I certify that the work identified above was accomplished in
 accordance with the requirements of Part 43 of the Federal
 Aviation Regulations and that the aircraft is approved for
 return to service."
```

- Section title: `labelSmall`, `onSurfaceVariant`
- "Approved by" and "Date" rows: same two-column key/value layout used elsewhere in the sheet
- Legal statement: `bodySmall`, italic, `onSurfaceVariant`, full width
- No edit affordance in the sheet — editing goes through the log form

---

## 6. Files touched

| File | Change |
|------|--------|
| `core/model/.../maintenance_log.proto` | Add `ReturnToService` message + field 14 on `MaintenanceLog` |
| `feature/logs/update/.../MaintenanceLogFormScreen.kt` | Insert RTS row after Inspection Tasks section |
| `feature/logs/update/.../MaintenanceLogFormViewModel.kt` | Add `ReturnToServiceDraft` to UiState; add `onRtsToggled`, `onRtsApproverSelected`; map to proto in `save()` |
| `feature/logs/viewing/.../MaintenanceLogCard.kt` | Add conditional `CheckCircleOutline` icon in footer row |
| `feature/logs/viewing/.../MaintenanceLogDetailSheet.kt` | Add RTS block at bottom (only when field is set) |

No new modules. No new Koin bindings. No manager changes. No navigation changes.

---

## 7. Out of scope

- **Digital signature capture** (drawing pad / biometric) — the approver selection is the proxy for a signature in V1. Physical logbooks use a handwritten signature; this mirrors the intent without the complexity.
- **IA vs. A&P enforcement** — the app does not validate that the selected approver holds the correct certificate for the work type (e.g., an IA is required for annual sign-offs). That is a regulatory concern left to the user.
- **"Not approved" badge on the card** — logs without RTS show no indicator. Absence of the checkmark is the implicit signal.
