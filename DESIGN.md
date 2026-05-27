---
name: Hopply (Wingslog)
description: Aviation maintenance logbook for pilots and mechanics — safety-critical records, zero friction.
colors:
  aviation-blue: "#1A5FAE"
  sky-container: "#D5E3FF"
  deep-navy: "#001849"
  panel-slate: "#525E72"
  slate-container: "#D6E4F5"
  amber-glow: "#FFBA4E"
  amber-haze: "#FFDFA6"
  airworthy-green: "#276B39"
  caution-amber: "#8B5E00"
  caution-bg: "#FFECB3"
typography:
  display:
    fontFamily: "Space Grotesk, system-ui, sans-serif"
    fontSize: "36sp"
    fontWeight: 900
    lineHeight: "40sp"
    letterSpacing: "0sp"
  headline:
    fontFamily: "Space Grotesk, system-ui, sans-serif"
    fontSize: "24–32sp"
    fontWeight: 700
    lineHeight: "32–40sp"
    letterSpacing: "0sp"
  title:
    fontFamily: "Space Grotesk, system-ui, sans-serif"
    fontSize: "14–22sp"
    fontWeight: 600
    lineHeight: "20–28sp"
    letterSpacing: "0–0.15sp"
  body:
    fontFamily: "system-ui, sans-serif"
    fontSize: "14–16sp"
    fontWeight: 400
    lineHeight: "20–24sp"
    letterSpacing: "0.25–0.5sp"
  label:
    fontFamily: "system-ui, sans-serif"
    fontSize: "11–14sp"
    fontWeight: 500
    lineHeight: "16–20sp"
    letterSpacing: "0.1–0.5sp"
  data:
    fontFamily: "JetBrains Mono, monospace"
    fontSize: "12–16sp"
    fontWeight: 500
    lineHeight: "16–24sp"
    letterSpacing: "0sp"
rounded:
  badge: "4dp"
  chip: "12dp"
  card: "12dp"
  button: "16dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  xxl: "32dp"
  screen-padding: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.aviation-blue}"
    textColor: "#FFFFFF"
    rounded: "{rounded.button}"
    height: "56dp"
    padding: "0dp 16dp"
  button-primary-hover:
    backgroundColor: "{colors.deep-navy}"
    textColor: "#FFFFFF"
    rounded: "{rounded.button}"
    height: "56dp"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.aviation-blue}"
    rounded: "{rounded.button}"
    height: "56dp"
  button-danger:
    backgroundColor: "transparent"
    textColor: "#B3261E"
    rounded: "{rounded.button}"
    height: "56dp"
  card:
    backgroundColor: "#EEF1F6"
    rounded: "{rounded.card}"
    padding: "{spacing.lg}"
---

# Design System: Hopply

## 1. Overview

**Creative North Star: "The Logbook"**

Every screen is a page in a regulation-compliant maintenance record. Nothing decorates; everything documents. The aviation blue is ink on vellum. Space Grotesk is the hand that signs the form. The interface does not hurry the user — it makes each entry feel considered and permanent. When a pilot opens Hopply after a cross-country or a mechanic logs a 100-hour inspection at the end of a long day, the interface meets them with calm authority, not engagement-driven friction.

The light theme is not a safe default. It is the deliberate choice: a logbook is a paper artifact, and this screen extends that physical register. The palette is derived from glass cockpit references — the Garmin G1000 panel blue, advisory amber annunciators, the green-and-amber visual language of IFR compliance charts. These are not metaphors imported for flavor; they are the actual visual vocabulary of the domain.

The system explicitly rejects the SaaS dashboard aesthetic. No hero-metric grids with gradient accents, no identical icon-cards, no at-a-glance information packing that sacrifices trust for density. Complexity is revealed progressively; primary views show only what matters right now.

**Key Characteristics:**
- Authoritative, archival, unhurried
- Aviation-specific color semantics carried consistently across every surface
- Typographic hierarchy does the work; decoration does not
- Confident, structured controls — every action feels committed
- Safety-critical status (OVERDUE, DUE SOON) always surfaces first

## 2. Colors: The Instrument Palette

Two structural tones, one personality accent used sparingly, two semantic anchors. Never derived from the user's wallpaper; dynamic color is permanently disabled.

### Primary
- **Instrument Blue** (#1A5FAE / dark: #A7C8FF): The dominant brand tone. Primary actions, current selection, navigation indicators, focused state. References the display color of glass cockpit avionics (Garmin G1000, ForeFlight).
- **Sky Container** (#D5E3FF / dark: #004785): The soft tonal surface behind primary elements. Flight-time card backgrounds, primary chip fills, selected badge containers.
- **Deep Navy** (#001849): On-container text over primary surfaces. The ink beneath the instrument glass.

### Secondary
- **Panel Slate** (#525E72 / dark: #BAC8E0): Cool, grounded. Instrument panel tone. Secondary actions, inactive states, supporting UI chrome. Pairs with instrument blue and reads as background instrumentation.
- **Slate Container** (#D6E4F5 / dark: #3A4557): Tonal surface behind secondary elements.

### Tertiary
- **Advisory Amber** (light text: #7A5200 / dark: #FFBA4E): The brand personality accent. References advisory annunciators and classic 6-pack gauge amber. Appears on ≤10% of any given screen. Light-mode text form is the deep muted amber (#7A5200) for WCAG contrast compliance; dark mode shows the full amber glow (#FFBA4E).
- **Amber Haze** (#FFDFA6 / dark: #5B3D00): Warm advisory surface. Used as container background behind amber-tinted advisory elements.

### Semantic
- **Airworthy Green** (#276B39 / dark: #81C784): "In the green" — compliant, airworthy, go. Passing inspections, compliant task indicators, success states. Always as text or icon on a neutral surface, never as a fill.
- **Caution Amber** (#8B5E00 / surface: #FFECB3 / dark text: #FFCA28, dark surface: #514500): Advisory caution — action required, not immediate. The semantic amber paired with Airworthy Green. Matches the mental model of the amber annunciator panel light.

### Named Rules
#### Color Mapping (from `Color.kt`)

| M3 Role | Light | Dark | Usage |
|----------|-----|------|-------|
| Primary | `#1A5FAE` (AviationBlue40) | `#A7C8FF` (AviationBlue80) | Filled buttons, focus, active state, tail numbers |
| Primary Container | `#D5E3FF` (AviationBlue90) | `#004785` (AviationBlue30) | Card backgrounds, chip fills, selected badges |
| On Container | `#001849` (AviationBlue10) | — | On-primary text |
| Secondary | `#525E72` (BlueGray40) | `#BAC8E0` (BlueGray80) | Secondary actions, inactive chrome |
| Secondary Container | `#D6E4F5` (BlueGray90) | `#3A4557` (BlueGray30) | Secondary fills |
| Tertiary (light) | `#7A5200` (Amber40) | `#FFBA4E` (Amber80) | Advisory — ≤10% of color moments |
| Tertiary Container (light) | `#FFDFA6` (Amber90) | `#514500` (Amber30 dark) | Advisory background |
| Positive text | `#276B39` (StatusOkLight) | `#81C784` (StatusOkDark) | Airworthy — text/icon only |
| Positive container | `#E3F2E8` (StatusOkContainerLight) | `#1B4D2B` (StatusOkContainerDark) | Positive status chip bg |
| Caution text | `#8B5E00` (StatusWarningLight) | `#FFCA28` (StatusWarningDark) | Due soon — text/icon |
| Caution container | `#FFECB3` (StatusWarningContainerLight) | `#514500` (StatusWarningContainerDark) | Caution status chip bg |
| Blocking/Error | M3 `error` / `errorContainer` | M3 `error` / `errorContainer` | AOG squawks, overdue |

**The Advisory Rule.** Instrument Amber (tertiary) appears on ≤10% of any given screen. Its power comes from scarcity. A screen full of amber has no amber.

**The Semantic Lock Rule.** Airworthy Green and Caution Amber are semantic signals, not decorative colors. They may not appear for brand moments, empty state illustrations, or visual interest. If a color looks like a status, it is a status.

## 3. Typography

**Display / Headline / Title:** Space Grotesk (Black, Bold, SemiBold, Medium)
**Body / Labels:** System sans (SF Pro on iOS, Roboto on Android — native rendering quality in data-dense contexts)
**Technical Data:** JetBrains Mono (Bold, Medium)

**Character:** Space Grotesk signals precision without coldness — geometric but not sterile, appropriate for a professional who trusts their instruments. System sans keeps body text native and legible at any density. JetBrains Mono carries character-alignment semantics: when a value is in mono, it is a measurement or identifier, never copy.

### Hierarchy
- **Display** (Black, 36sp/40sp): Dashboard hero data — next due dates, tail numbers as primary identifiers. Once per screen maximum.
- **Headline Large** (Bold, 32sp/40sp): Screen-level headings at maximum emphasis.
- **Headline Medium** (Bold, 28sp/36sp): Section headings within complex screens.
- **Headline Small** (Bold, 24sp/32sp): Detail sheet titles, prominent card headings.
- **Title Large** (SemiBold, 22sp/28sp): Primary content labels within cards and forms.
- **Title Medium** (SemiBold, 16sp/24sp): Secondary headings, tab labels, toolbar titles.
- **Title Small** (Medium, 14sp/20sp): Supporting section headers, chip labels.
- **Body Large** (Normal, 16sp/24sp): Primary paragraph content, form field values.
- **Body Medium** (Normal, 14sp/20sp): Supporting text, list item descriptions, secondary data.
- **Label Large** (Medium, 14sp/20sp): Button labels (rendered UPPERCASE), form field labels.
- **Label Medium** (Medium, 12sp/16sp): Badge labels, compact chip text.
- **Label Small** (Medium, 11sp/16sp): Timestamps, dense data footnotes.
- **Data Large** (JetBrains Mono Bold, 16sp/24sp): Engine hours, airframe time, serial numbers — any value where character alignment is semantic.
- **Data Medium** (JetBrains Mono Medium, 14sp/20sp): Compact technical identifiers within cards.
- **Data Small** (JetBrains Mono Medium, 12sp/16sp): Inline technical data within list rows.

### Exact M3 Typography Mappings (from `Type.kt`)

| M3 Key | Weight | Size | Line Height | Use |
|--------|------|------|---------|-----|
| headlineLarge | Bold | 32sp | 40sp | Screen headings |
| headlineMedium | Bold | 28sp | 36sp | Section headings |
| headlineSmall | Bold | 24sp | 32sp | Detail titles, alert section headers |
| titleLarge | SemiBold | 22sp | 28sp | Card headers, form titles |
| titleMedium | SemiBold | 16sp | 24sp+0.15 | Tabs, secondary headings |
| titleSmall | Medium | 14sp | 20sp+0.1 | Chip labels |
| bodyLarge | Normal | 16sp | 24sp+0.5 | Primary paragraph content |
| bodySmall | Normal | 12sp | 18sp+0.25 | Secondary card text |
| bodyMedium | Normal | 14sp | 20sp+0.25 | List descriptions |
| labelLarge | Medium | 14sp | 20sp+0.1 | **Button labels → UPPERCASE** |
| labelMedium | Medium | 12sp | 16sp+0.5 | Badge labels, tech names |
| labelSmall | Medium | 11sp | 16sp+0.5 | Timestamps, status values |
| displaySmall | Black | 36sp | 40sp+0.0 | Hero display (tail numbers) |
| heroDisplay | Black | 36sp | 40sp+0.0 | Aircraft make/model (Space Grotesk) |
| dataLarge | JetBrains Mono Bold | 16sp | 24sp+0.0 | Engine hours, tach times |
| dataMedium | JetBrains Mono Medium | 14sp | 20sp+0.0 | Tail numbers in cards |
| dataSmall | JetBrains Mono Medium | 12sp | 16sp+0.0 | Inline technical data |

### Named Rules
**The Mono Rule.** JetBrains Mono is reserved for technical aviation data: tail numbers, serial numbers, tach/Hobbs times, airframe hours. It never appears in UI chrome (buttons, labels, navigation, body copy).

**The Uppercase Commitment Rule.** All button labels render UPPERCASE with Bold weight. Sentence case everywhere else. Uppercase signals commitment; a button is a decision, not an option.

## 4. Elevation

Material 3 tonal elevation throughout. Depth is expressed through surface color shifts, not cast shadows. No custom shadow vocabulary exists. Surfaces at higher effective elevation receive a stronger wash of the primary (instrument blue) tone via M3's tonal layering system.

Surface hierarchy, lightest to deepest: `background` → `surface` → `surfaceContainer` → `surfaceContainerHigh`. Each step is slightly warmer toward the primary blue. Cards live at `surfaceContainer`. Overlaid sheets (bottom sheets, dialogs) float above `surface` through M3's scrim.

**The No-Shadow Rule.** Do not introduce custom elevation parameters or manual shadow modifiers. If visual separation feels insufficient, the tonal hierarchy is not doing its job — fix the surface color assignment, not the shadow. Tonal elevation is the system; cast shadows are not.

## 5. Components

### Buttons
Three variants sharing a 56dp height and 16dp corner radius — pronounced enough to read as rounded, tight enough to feel decisive rather than friendly.

- **Primary (Filled):** Instrument Blue (#1A5FAE) fill, white text, UPPERCASE Bold. Full available width in `BottomButtons`. Loading state: 18dp circular progress indicator replaces the label, same color as text.
- **Secondary (Outlined):** Transparent fill, Instrument Blue 1dp border, Instrument Blue text. Used for Cancel and non-destructive secondary actions.
- **Danger (Outlined):** Transparent fill, Error color 1dp border, Error color text. Used for Delete, Dismiss, and irreversible secondary actions. Same dimensions as Secondary; the color carries the weight.
- **Disabled state:** `surfaceVariant` fill, `outline` text. Same shape, muted palette, unambiguous.

### Segmented Controls
Material 3 `SingleChoiceSegmentedButtonRow` (two segments, full width). Used as the primary filter mechanism on list screens — Open/Closed squawks, Active/Completed tasks. No custom styling beyond M3 defaults.

### Cards
- **Corner radius:** 12dp (gently curved; neither pill nor rectangle)
- **Background:** `surfaceContainer` — one tonal step above `surface`
- **Border:** Optional `outlineVariant` at 1dp for emphasis (empty-state cards, section delimiters)
- **Padding:** 16dp (`Spacing.large`) internal

### Bottom Sheet (DetailSheet)
Modal bottom sheet with `skipPartiallyExpanded = true` — always fully expanded, never half-state. Horizontal padding: 24dp (screen padding). Header: trailing `TextButton` action; headline fills remaining width. Internal vertical scroll with 32dp footer spacer to clear the system navigation bar.

### Top App Bar
`TopAppBar` with `background` color at rest and on scroll — no elevation color shift, no scrim. Blends flush with content below. Title at `titleLarge` weight (Space Grotesk). Back navigation arrow always present on non-root screens.

### Empty States
Centered column: 80dp icon at 60% primary alpha, `headlineSmall` Bold title, `bodyLarge` description at `onSurfaceVariant`, optional primary Button with 32dp gap above. Empty states name the next action, not just the void.

### Status Badges

4dp corner radius (`badgeCornerRadius`) — reads as a stamp, not a pill. Tinted background from the relevant container color, matched on-container text. Text is `labelSmall`/SemiBold/UPPERCASE/0.5sp letterSpacing, colored `tone.onContainer`.

### StatusTier Enum Mapping (from `StatusColors.kt`)

Maps domain status to M3 roles. **No ad-hoc color choices in feature code.** Use: `toneFor(tier)` → `StatusTone(accent, container, onContainer)`.

| Tier | Condition | Text/Icon Color | Chip Container |
|-----|-------|-----------------|--------------------|
| BLOCKING | AOG, operational stop | `error` | `errorContainer` |
| CRITICAL | Overdue, high-priority | `error` | `errorContainer` |
| CAUTION | Due soon, medium | `#8B5E00` / `#FFCA28` (dark) | `#FFECB3` / `#514500` (dark) |
| POSITIVE | Compliant, current | `#276B39` / `#81C784` (dark) | `#E3F2E8` / `#1B4D2B` (dark) |
| NEUTRAL | Low-priority, inactive | `onSurfaceVariant` | `surfaceVariant` |

### Component Border Accent Rule

Overdue/DueSoon cards get a 1dp left-border accent at `statusTone.accent.copy(alpha = 0.5f)`. AOG squawks get `blocking.accent`. Normal cards get `outlineVariant`. Component type badges use context-specific fills (ENGINE → primaryContainer, AIRFRAME → surfaceContainerHigh, PROPELLER → secondaryContainer).

## 6. Do's and Don'ts

### Do:
- **Do** use `WingslogTypography.dataLarge` / `dataMedium` / `dataSmall` (JetBrains Mono) for every tail number, serial number, engine hour, tach time, and airframe hour. Character alignment is semantic.
- **Do** keep Instrument Amber (tertiary) to ≤10% of any screen's color moments. Its rarity is the signal.
- **Do** surface OVERDUE and DUE SOON status at the top of every list. Safety-critical items are never buried by sort order.
- **Do** use `Spacing.screenPadding` (24dp) as the horizontal inset for all screen-level content, consistently.
- **Do** reserve Airworthy Green (#276B39) and Caution Amber (#8B5E00) for compliance and status semantics only.
- **Do** render button labels UPPERCASE + Bold. It signals commitment.
- **Do** prefer tonal surface hierarchy (`surfaceContainer`, `surfaceContainerHigh`) over manual shadows to express depth.
- **Do** use `DetailSheet` (bottom sheet, always fully expanded) for record details — not a full-screen push for a quick read.
- **Do** disable dynamic color. The aviation palette is the brand. Wallpaper-derived colors erase the instrument-blue identity.

### Don't:
- **Don't** expose multi-step complexity on a single form. Use tabs or wizard flows; a single form is for simple, linear operations.
- **Don't** use spreadsheet-style tables, dense grid layouts, or raw data dumps on primary screens. Complexity lives one level deeper.
- **Don't** use gradient text, glassmorphism fills, hero-metric grids (big number + label + supporting stats + gradient), or identical icon-card grids. These are the SaaS dashboard aesthetic this system rejects.
- **Don't** use Instrument Amber for anything non-advisory: no brand accents, no empty-state illustrations, no "interesting" visual moments.
- **Don't** use `border-left` or `border-right` stripes greater than 1dp as decorative callout accents. Rewrite with full-border containers or background tints.
- **Don't** introduce custom shadow or `elevation` modifier values. Tonal elevation handles depth.
- **Don't** use Space Grotesk for body text, form field values, or dense data labels. It is for headings and titles only.
- **Don't** use JetBrains Mono for anything that is not a technical measurement or identifier. No buttons, no labels, no body copy.
- **Don't** add decorative motion: no orchestrated entrances, no elastic or bounce easing, no scroll-driven choreography. Motion is state feedback only (150–250ms, ease-out).

---

## 7. Spacing & Radius

All values from `Spacing` object (`core/ui/theme/Spacing.kt`). These are the only spacing tokens — never invent new ones.

| Token | Value | Use |
|-------|----------|---
| `none` | 0dp | — |
| `extraSmall` | 4dp | Tiny gaps (chip→chip, label→value, row item gaps) |
| `small` | 8dp | Row-level gaps within cards |
| `medium` | 12dp | Multi-line card section gaps (most common card-level gap) |
| `large` | 16dp | Internal card padding, screen-content row gaps |
| `xLarge` | 20dp | Component row spacing |
| `extraLarge` | **24dp** | **Screen padding, card internal padding** (the primary structural spacing) |
| `huge` | 32dp | Bottom-sheet footer spacer, large section gaps |
| `massive` | 48dp | Rare — full page section gaps |

### Radius
| Token | Value | Use |
|-------|------|---|
| `badgeCornerRadius` | 4dp | Status chips, component type badges (reads as a stamp) |
| `cardCornerRadius` | 12dp | All card surfaces |
| `chipCornerRadius` | 12dp | Form controls, dropdowns, outlined fields |
| `buttonCornerRadius` | **16dp** | All buttons |

**Screen-level horizontal inset:** `Spacing.screenPadding` = `Spacing.large` = 16dp. All screen-level content starts at this inset.

---

## 8. Domain Data Shape

The entity hierarchy (from protobuf sources in `core/model/src/commonMain/proto/`) determines the natural page navigation and content organization. **Every view groups by this shape.**

```
Fleet (collection)
  └── Aircraft (core/model/aircraft/aircraft.proto)
        id, make, model, serial, tail_number
        ├── Engines[] (aircraft/engine.proto)
        │     id, make, model, serial
        │     └── Propeller (optional)
        │          hub: PropellerHub (make, model, serial)
        │          blades[]: PropellerBlade (serial)
        │
        ├── MaintenanceLog[] (aircraft/maintenance_log.proto)
        │     id, timestamp, technician_id, work_description
        │     component_type: COMPONENT_ENGINE | COMPONENT_AIRFRAME | COMPONENT_PROPELLER
        │     component_serial (references Engine/Prop serial)
        │     engine_hour, airframe_time, prop_time
        │     inspection_ids[] (links to MaintenanceTask)
        │     attachments[]
        │     Technician {name, certificate}
        │
        ├── MaintenanceTask[] (aircraft/maintenance_task.proto)
        │     id, title, component, notes
        │     rules[]: TimeRule | EngineHourRule | OnConditionRule | LinkedRule | ImmediateRule
        │     force_due_date, force_due_engine_hour
        │     type: COMPLIANCE_TYPE_ROUTINE_INSPECTION | SERVICE_BULLETIN | AIRWORTHINESS_DIRECTIVE
        │     reference_number, compliance_authority, compliance_details
        │     is_one_time (moves to history after first log)
        │     ForceCompliedStatus {complied_date, complied_engine_hours}
        │     ComplianceType (ROUTINE_INSPECTION, SERVICE_BULLETIN, AIRWORTHINESS_DIRECTIVE)
        │
        ├── Squawk[] (aircraft/squawk.proto)
        │     id, title, description
        │     priority: SQUAWK_PRIORITY_LOW | MEDIUM | HIGH | AOG
        │     component_type, component_serial
        │     status: OPEN | ADDRESSED | DISMISSED
        │     addressed_by_log_id, dismiss_reason, dismissed_at
        │     attachments[]
        │
        └── MaintenanceOverview (aircraft/maintenance_overview.proto)
              total_log_count, airframe/engine/prop_log_count
              current_airframe_time, current_engine_time, current_prop_time
```

### How this shapes the UX

The data model has four entity types per aircraft. Each maps to a tab. The Overview tab **does not list entities** — it aggregates them.

| Tab | Source | Layout |
|-----|---|---|
| Overview (`feature/aircraft/dashboard`) | Aggregated | Vertical flow: hero → config → alerts → stats |
| Squawks (`feature/squawk/viewing`) | `SquawkWithStatus` | Vertical card list + segmented filter (Open/Closed) |
| Tasks (`feature/tasks/viewing`) | `MaintenanceTaskWithStatus` | Vertical card list + segmented filter (Active/Complied) |
| Logs (`feature/logs/viewing`) | `MaintenanceLog` | Vertical card list + segmented filter (All/Inspection types) |

**Overview tab layout priority (rule-driven, not alphabetical):**

1. **Hero display** — `make model` + `tail_number` (heroDisplay Black 36sp) — one screen
2. **Configuration card** — collapsible aircraft data (AircraftDataCard)
3. **AOG alert** — open AOG squawks (AogAlertSection) — **only if AOG squawks exist**
4. **Critical alerts** — overdue/due-soon tasks (CriticalAlertsSection) — **only if overdue exists**
5. **Maintenance summary** — log stats (LogStatsSection) — only if total > 0; otherwise LogOnboardingCard

When there are no overdue tasks, the Configuration card expands by default. When work is overdue it collapses — the pilot's attention goes to what matters first.

---

## 9. Screen Layouts

### 9A. Fleet Dashboard (`feature/fleet/viewing/DashboardScreen.kt`)

The fleet list **is** the page. No hero metrics. Primary data is the list of aircraft.

```
┌───────────────────────────┐
│ WingsLog    [AvatarIcon]  │ ← TopAppBar (ConstrainedTopBar)
│                           │
│ ┌────────────────────┐    │
│ │ Sling TSi        → │    │ ← AircraftDashboardCard
│ │ N532SL   DueSoon   │    │   (surfaceContainer card +
│ └─────────────────────────────────────────┘   outlineVariant border)
│                                   │
│ ┌─────────────────────┐         │
│ │ Cessna 172      →  │         │
│ │ ...          Airworthy │       │
│ └───────────────────────────┘   │
│                                   │
│        [ + FAB (Add Aircraft) ]  │
└─────────────────────────────────────┘
```

**AircraftDashboardCard anatomy** (`AircraftDashboardCard.kt`):
```
┌──────────────────────────────────────────────┐
│ [Airframe chip]        [DUE SOON] →        │ ← Top row (SpaceBetween)
│                                              │
│ Sling TSi (titleLarge onSurface)             │
│ N532SL (dataMedium primary)                  │ ← tail = JetBrains Mono
│ ───────────────────────────────────────────  │ ← Divider
│ 05/10/2026     Annual (primary)     J. Rivera│ ← Footer row
└──────────────────────────────────────────────┘
```

Key detail: health status (`DueStatus?`) renders as a StatusChip (CRITICAL → error, CAUTION → caution, else hidden) or the card border color. The card border is always `outlineVariant` at 1dp.

### 9B. Aircraft Detail — Overview Tab (`feature/aircraft/dashboard/compose/tabs/OverviewTab.kt`)

```
┌──────────────────────────────────────────────┐
│ Sling Tsi        N532Sl                      │ ← Hero Row (heroDisplay × 2)
│                                              │
│ ┌──────── AIRCRAFT DATA (collapsible) ──────┐│
│ │ [⬇] Component breakdown                   ││
│ │ AIRFRAME        Sling TSi        S/N:      ││ ← ComponentCard (surfaceContainerLow)
│ │               SLING532                      ││
│ │ ENGINE 1         Rotax 915       S/N:      ││
│ │                915-0001                     ││
│ │ Propeller        Airmaster        S/N:      ││
│ │                                AP430-001   ││
│ │              B1   B2   B3                   ││ ← BladeChipsOverview (FlowRow)
│ └─────────────────────────────────────────────┘│
│                                              │
│ ┌─── ✈ AOG ALERT ────────────────┐  │ ← AogAlertSection (only if AOG)
│ │ AOG ALERT — aircraft grounded   │  │
│ │ ─────────────────────────────── │  │
│ │ [●] O2 pressure leaking         │  │
│ │ [●] Engine fire warning         │  │
│ │            VIEW SQUAWKS         │  │
│ └──────────────────────────────────────┘│
│                                              │
│ ┌─── MAINTENANCE DUE ──────────────────────┐│ ← CriticalAlertsSection (only if overdue)
│ │ [⚠] OVERDUE / AOG DUE SOON               ││
│ │ ──────────────────────────────────────────│ │
│ │ AIRWORTHINESS                             ││
│ │ [●] 100 Hr Inspection    EXPIRED 03/15    ││
│ │ [●] Annual                 DUE 14 DAYS     ││
│ └────────────────────────────────────────────┘  │
│                                              │
│ MAINTENANCE SUMMARY                         │ ← LogStatsSection (or LogOnboardingCard)
│ ┌──────────────┐ ┌──────────────┐           │
│ │ 1432.5h      │ │ 1428.1h      │           │
│ │ A/TOTAL      │ │ E/TOTAL      │           │
│ └──────────────┘ └──────────────┘           │
│ ┌──────────────┐ ┌──────────────┐           │
│ │ 2340.3h      │ │      87      │           │
│ │ P/TOTAL      │ │ LOGS         │           │
│ └──────────────┘ └──────────────┘           │
└──────────────────────────────────────────────┘
```

**Overview layout rules:**
- AOG alerts above all — immediate operational stop
- Critical alerts below — compliance work requiring attention
- Health status determines card expansion: if no overdue, expand config; if overdue, collapse it (focus on alerts)
- `Spacing.screenPadding` = 16dp on all content

### 9C. Aircraft Tabs (`feature/aircraft/dashboard/compose/tabs/AircraftDashboardTabRow.kt`)

Four tabs with icon+label, using `IconLabelTabSpec`:

1. ✈ **Overview** (FlightTakeoff)
2. 🐛 **Squawks** (BugReport)
3. ✓ **Tasks** (TaskAlt)
4. 📜 **Logs** (History)

### 9D. Squawks List (`feature/squawk/viewing/`)

```
┌──────────────────────────────────────┐
│ [  OPEN   |   CLOSED  ]             │ ← DualSegmentedFilter
│ ───────────────────────────────── │
│ ┌───────────┬──────────────┐  │   │
│ │     AOG   │     OPEN     │  │   │
│ │ [AOG] O2 pressure leaking to cabin  │  │
│ │ 05/10/2026                     │   │
│ └──────────────────────────────────┘ │ ← SquawkCard (red border)
│                                      │
│ ┌───────────┬──────────────┐       │
│ │  MEDIUM   │    OPEN       │       │
│ │ [MEDIUM] Engine...              │       │
│ │ 05/08/2026                     │       │
│ └────────────────────────────────┘       │
└────────────────────────────────────────┴
```

**SquawkCard anatomy:** Left = PriorityBadge (StatusChip: AOG/CRITICAL/CAUTION/NEUTRAL) + StatusBadge (OPEN/ADDRESSED/DISMISSED). Right = chevron. Below = title (titleMedium/Bold) + description (bodySmall). Footer = date (labelSmall). AOG squawks get `blocking.accent.copy(alpha=0.5)` border.

### 9E. Tasks List (`feature/tasks/viewing/`)

```
┌────────────────────────────────────────┐
│ [ ACTIVE | COMPLIED ]                │ ← DualSegmentedFilter
│ ─────────────────────────────────── │
│ ┌───────────┬───────────────┐    │   │
│ │   OVERDUE │   ✓          │    │   │
│ │ 100 Hr                            │   │
│ │ Annual + 100hr every 100h        │   │
│ │ ─────────────────────────────── │   │
│ │ DEADLINE                           │   │
│ │ 03/15/2026                        │   │
│ └────────────────────────────────────┘   │ ← TaskCard (red border)
│                                          │
│ ┌───────────┬───────────────┐        │
│ │   DUE SOON│   ✓          │        │
│ │ Annual                                   │
│ │ Annual + 12 months from last             │
│ │ ───────────────────────────────        │
│ │ DUE DATE                                 │
│ │ 05/20/2026                               │
│ └───────────────────────────────────────  │  ← TaskCard (amber border)
└───────────────────────────────────────────┘
```

### 9F. Logs List (`feature/logs/viewing/`)

```
┌──────────────────────────────────────┐
│ [ All | Annual | ...  ]             │ ← DualSegmentedFilter
│ ───────────────────────────────── │
│ ┌────────┐   14.3h          →  │   │
│ │ [ENGINE]                        │   │
│ │ Replaced left magneto per       │   │ ← MaintenanceLogCard
│ │ SB-1234. Mag drop within limits │   │
│ │ ────────────────────────────── │   │
│ │ 05/10/2026    Annual      J. Rivera │
│ └──────────────────────────────────┘  │
│ ┌────────┐    0.5h          →  │    │
│ │ [A/FROM] │                         │    │
│ │ Annual inspection completed        │    │
│ │ ──────────────────────────────    │
│ │ 04/15/2026    Annual + 100hr              │    │
│ └──────────────────────────────────┘     │
└───────────────────────────────────────┘
```

**MaintenanceLogCard anatomy:**  
- Top row: ComponentTypeBadge (pill 4dp, ENGINE→primaryContainer, AIRFRAME→surfaceContainerHigh, PROPELLER→secondaryContainer) + tach hours (dataSmall+onSurfaceVariant) + chevron
- Body: Full work description (bodyMedium+onSurface)
- Divider: outlineVariant at 0.3 alpha
- Footer: date (dataSmall) + inspection count (primary color) + technician name

### 9G. Components Reference

| Component | Source File | Pattern |
|-----------|-----------|---|
| AircraftDashboardCard | `DashboardScreen.kt` + `AircraftDashboardCard.kt` | Card with chip + status border |
| LogCard | `MaintenanceLogCard.kt` | Card with component badge + divider |
| TaskCard | `TaskCard.kt` | Card with icon + label/value + status border |
| SquawkCard | `SquawkCard.kt` | Card with dual badges + title/desc |
| StatusChip | `StatusChip.kt` | Pill, status-tier tinted |
| ComponentTypeBadge | `MaintenanceLogCard.kt` | 4dp radius, tinted box |
| BladeChipsOverview | `BladeChipsOverview.kt` | FlowRow of Surface chips |
| AogAlertSection | `AogAlertSection.kt` | Icon + title + list + action bar |
| CriticalAlertSection | `CriticalAlertsSection.kt` | Title + list + action bar |
| LogStatsSection | `LogStatsSection.kt` | Title + card with stat cells |
| DetailSheet | `DetailSheet.kt` | BottomSheet, always expanded |
| EmptyState | `EmptyState.kt` | Centered icon+title+desc+action |
| IconLabelTabRow | `IconLabelTabRow.kt` | Horizontal tab navigation |

---

## 10. The Critical Status Hierarchy

This is the non-negotiable information priority that shapes every screen. Status flows vertically, never buried by sort order.

1. **AOG Squawks** (BLOCKING, red) — immediate operational stop; always first
2. **CRITICAL** (OVERDUE, red) — overdue compliance work
3. **CAUTION** (DUE SOON, amber) — approaching deadline
4. **POSITIVE** (COMPLIED, green) — completed/compliant
5. **NEUTRAL** (normal, slate) — low-priority status

When health is fully positive (no OVERDUE, no DUE SOON), the Configuration card expands by default. When anything is overdue, it collapses — attention goes to what matters.
