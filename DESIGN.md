---
name: SquawkIt
description: Maintenance records for everything you own — aircraft first, then cars, bikes, boats and homes. Safety-critical dates, zero friction.
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

# Design System: SquawkIt

## 1. Overview

**Creative North Star: "The Logbook"**

Every screen is a page in a maintenance record kept to a regulatory standard, whatever the Thing. Nothing decorates; everything documents. The aviation blue is ink on vellum. Space Grotesk is the hand that signs the form. The interface does not hurry the user — it makes each entry feel considered and permanent. When a pilot opens SquawkIt after a cross-country, or a homeowner logs the furnace service at the end of a long day, the interface meets them with calm authority, not engagement-driven friction.

The light theme is not a safe default. It is the deliberate choice: a logbook is a paper artifact, and this screen extends that physical register. The palette is derived from glass cockpit references — the Garmin G1000 panel blue, advisory amber annunciators, the green-and-amber visual language of IFR compliance charts. They are the visual vocabulary of the founding domain, and they stay the brand for every domain that followed: a car and a house get the same instrument palette, not a theme of their own. What changes per Thing is the **words** — the template supplies them (§8) — never the colors.

The system explicitly rejects the SaaS dashboard aesthetic. No hero-metric grids with gradient accents, no identical icon-cards, no at-a-glance information packing that sacrifices trust for density. Complexity is revealed progressively; primary views show only what matters right now.

**Key Characteristics:**
- Authoritative, archival, unhurried
- Instrument-panel color semantics carried consistently across every surface and every kind of Thing
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
- **Airworthy Green** (#276B39 / dark: #81C784): "In the green" — compliant, ready, go. The word it sits beside is the template's `ready_status` ("Airworthy", "Ready", "Good"); the color is the same everywhere. Passing inspections, compliant task indicators, success states. Always as text or icon on a neutral surface, never as a fill.
- **Caution Amber** (#8B5E00 / surface: #FFECB3 / dark text: #FFCA28, dark surface: #514500): Advisory caution — action required, not immediate. The semantic amber paired with Airworthy Green. Matches the mental model of the amber annunciator panel light.

### Named Rules
#### Color Mapping (from `Color.kt`)

| M3 Role | Light | Dark | Usage |
|----------|-----|------|-------|
| Primary | `#1A5FAE` (AviationBlue40) | `#A7C8FF` (AviationBlue80) | Filled buttons, focus, active state, the Thing's primary identifier (tail number, VIN, hull ID) |
| Primary Container | `#D5E3FF` (AviationBlue90) | `#004785` (AviationBlue30) | Card backgrounds, chip fills, selected badges |
| On Container | `#001849` (AviationBlue10) | — | On-primary text |
| Secondary | `#525E72` (BlueGray40) | `#BAC8E0` (BlueGray80) | Secondary actions, inactive chrome |
| Secondary Container | `#D6E4F5` (BlueGray90) | `#3A4557` (BlueGray30) | Secondary fills |
| Tertiary (light) | `#7A5200` (Amber40) | `#FFBA4E` (Amber80) | Advisory — ≤10% of color moments |
| Tertiary Container (light) | `#FFDFA6` (Amber90) | `#514500` (Amber30 dark) | Advisory background |
| Positive text | `#276B39` (StatusOkLight) | `#81C784` (StatusOkDark) | Ready (`ready_status`) — text/icon only |
| Positive container | `#E3F2E8` (StatusOkContainerLight) | `#1B4D2B` (StatusOkContainerDark) | Positive status chip bg |
| Caution text | `#8B5E00` (StatusWarningLight) | `#FFCA28` (StatusWarningDark) | Due soon — text/icon |
| Caution container | `#FFECB3` (StatusWarningContainerLight) | `#514500` (StatusWarningContainerDark) | Caution status chip bg |
| Blocking/Error | M3 `error` / `errorContainer` | M3 `error` / `errorContainer` | The template's down state (AOG / Off the road / Urgent), overdue |

**The Advisory Rule.** Instrument Amber (tertiary) appears on ≤10% of any given screen. Its power comes from scarcity. A screen full of amber has no amber.

**The Semantic Lock Rule.** Airworthy Green and Caution Amber are semantic signals, not decorative colors. They may not appear for brand moments, empty state illustrations, or visual interest. If a color looks like a status, it is a status.

## 3. Typography

**Display / Headline / Title:** Space Grotesk (Black, Bold, SemiBold, Medium)
**Body / Labels:** System sans (SF Pro on iOS, Roboto on Android — native rendering quality in data-dense contexts)
**Technical Data:** JetBrains Mono (Bold, Medium)

**Character:** Space Grotesk signals precision without coldness — geometric but not sterile, appropriate for a professional who trusts their instruments. System sans keeps body text native and legible at any density. JetBrains Mono carries character-alignment semantics: when a value is in mono, it is a measurement or identifier, never copy.

### Hierarchy
- **Display** (Black, 36sp/40sp): Dashboard hero data — next due dates, the Thing's primary identifier. Once per screen maximum.
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
| displaySmall | Black | 36sp | 40sp+0.0 | Hero display (primary identifier) |
| heroDisplay | Black | 36sp | 40sp+0.0 | The Thing's title — make/model, or its name (Space Grotesk) |
| dataLarge | JetBrains Mono Bold | 16sp | 24sp+0.0 | Meter readings — tach time, odometer, engine hours |
| dataMedium | JetBrains Mono Medium | 14sp | 20sp+0.0 | Identifiers in cards |
| dataSmall | JetBrains Mono Medium | 12sp | 16sp+0.0 | Inline technical data |

### Named Rules
**The Mono Rule.** JetBrains Mono is reserved for technical data: identifiers (tail numbers, VINs, hull IDs, frame numbers), serial numbers, and meter readings (tach/Hobbs time, odometer, engine hours). It never appears in UI chrome (buttons, labels, navigation, body copy).

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
Material 3 `SingleChoiceSegmentedButtonRow` (two segments, full width). Used as the primary filter mechanism on list screens — Open/Closed defects, Due/History tasks. No custom styling beyond M3 defaults.

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
| BLOCKING | The template's down state (AOG, Off the road, Urgent) | `error` | `errorContainer` |
| CRITICAL | Overdue, high-priority | `error` | `errorContainer` |
| CAUTION | Due soon, medium | `#8B5E00` / `#FFCA28` (dark) | `#FFECB3` / `#514500` (dark) |
| POSITIVE | Compliant, current | `#276B39` / `#81C784` (dark) | `#E3F2E8` / `#1B4D2B` (dark) |
| NEUTRAL | Low-priority, inactive | `onSurfaceVariant` | `surfaceVariant` |

### Component Border Accent Rule

Overdue/DueSoon cards get a 1dp left-border accent at `statusTone.accent.copy(alpha = 0.5f)`. Down-state defects get `blocking.accent`. Normal cards get `outlineVariant`. Component type badges use context-specific fills (ENGINE → primaryContainer, AIRFRAME → surfaceContainerHigh, PROPELLER → secondaryContainer) — and appear only on the airplane preset, whose parts the frozen `ComponentType` enum names; every other preset files records against the Thing itself (`usesComponentTypes`).

## 6. Do's and Don'ts

### Do:
- **Do** use `WingslogTypography.dataLarge` / `dataMedium` / `dataSmall` (JetBrains Mono) for every identifier, serial number and meter reading. Character alignment is semantic.
- **Do** keep Instrument Amber (tertiary) to ≤10% of any screen's color moments. Its rarity is the signal.
- **Do** surface OVERDUE and DUE SOON status at the top of every list. Safety-critical items are never buried by sort order.
- **Do** use `Spacing.screenPadding` (24dp) as the horizontal inset for all screen-level content, consistently.
- **Do** reserve Airworthy Green (#276B39) and Caution Amber (#8B5E00) for compliance and status semantics only.
- **Do** render button labels UPPERCASE + Bold. It signals commitment.
- **Do** prefer tonal surface hierarchy (`surfaceContainer`, `surfaceContainerHigh`) over manual shadows to express depth.
- **Do** use `DetailSheet` (bottom sheet, always fully expanded) for record details — not a full-screen push for a quick read.
- **Do** disable dynamic color. The instrument palette is the brand, for every kind of Thing. Wallpaper-derived colors erase the instrument-blue identity.
- **Do** take every noun, field label, meter name and empty-state sentence from `LocalThingLexicon` / `LocalThingTemplate`. A string that says "aircraft" or "tail number" in `strings.xml` is a bug on six of the seven presets; a per-Thing noun on an account-level surface (login, settings, subscription) is a bug on all of them.

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

The entity hierarchy (from protobuf sources in `core/model/src/commonMain/proto/thing/`) determines the natural page navigation and content organization. **Every view groups by this shape.** What a screen *calls* each level, and which levels it shows at all, comes from the Thing's own template — the same shape renders as an airplane with an engine and a propeller, or as a house with no components and no meters.

```
Account
  └── Thing[] (thing/thing.proto) — one per aircraft, car, bike, boat, home, or custom thing
        id, name
        template: ThingTemplate         ← the DNA, copied whole at creation (template.proto)
        │   id, version, display_name, icon
        │   lexicon      — thing / squawk / task / log / component / technician nouns,
        │                  ready_status ("Airworthy" · "Ready" · "Good"), down_status ("AOG" ·
        │                  "Off the road" · "Urgent"), per-preset empty_states copy
        │   capabilities — components, meters, compliance, technicians, sections, priorities
        │   spec_fields  — Make / Model / Tail number … VIN … Hull ID … Address / Year built
        │   component_slots — Engine ×N → Propeller → Blade … Chassis / Tire … (home: none)
        │   meters       — airframe_hours / engine_hours … odometer … (home: none)
        │   starter_tasks — the recommended schedule offered at creation (PRD §4.9)
        │   certifications — A&P … ASE … Electrician
        spec[]           — the values the spec_fields collected, keyed by field
        components[]     — the instantiated slot tree (thing/component.proto)
        │
        ├── MaintenanceLog[] (thing/maintenance_log.proto)
        │     id, timestamp, technician_id, work_description
        │     component_type (airplane only), meter_readings[] keyed by the template's meters
        │     inspection_ids[] (links to MaintenanceTask), attachments[]
        │
        ├── MaintenanceTask[] (thing/maintenance_task.proto)
        │     id, title, notes, component (airplane only)
        │     rules[]: TimeRule | MeterRule(meter_key, interval) | OnConditionRule | LinkedRule | ImmediateRule
        │     force_due_date, force_due_meter
        │     type: ROUTINE_INSPECTION | SERVICE_BULLETIN | AIRWORTHINESS_DIRECTIVE  (compliance capability)
        │     is_one_time (moves to history after first log)
        │
        ├── Squawk[] (thing/squawk.proto) — "squawk", "issue", or "attention item" per the lexicon
        │     id, title, description
        │     priority: LOW | MEDIUM | HIGH | AOG   (the top tier is labelled from down_status)
        │     status: OPEN | ADDRESSED | DISMISSED, addressed_by_log_id, attachments[]
        │
        └── MaintenanceOverview (thing/maintenance_overview.proto)
              log counts and the latest reading of each declared meter
```

### How this shapes the UX

There is no fleet list screen. The **adaptive shell** (`core/ui/adaptive/AdaptiveAppShell.kt`, driven by `feature/shell`) holds one selected Thing at a time and renders four **sections** for it; the Thing is chosen in the **switcher** — sidebar rows on wide tiers, a picker behind the top bar on compact ones. Each section is a per-Thing entity type. The Dashboard **does not list entities** — it aggregates them.

| Section | Source | Layout |
|-----|---|---|
| Dashboard (`feature/thing/dashboard`) | Aggregated | Vertical flow: hero → data card → alerts → stats (compact); hero → data card → two-column rail (wide) |
| Squawks (`feature/squawk/viewing`) | `SquawkWithStatus` | Vertical card list + segmented filter (Open/Closed) — named from the lexicon |
| Tasks (`feature/tasks/viewing`) | `MaintenanceTaskWithStatus` | Vertical card list + segmented filter (Due/History) |
| Logs (`feature/logs/viewing`) | `MaintenanceLog` | Vertical card list + segmented filter |

Which sections exist is a template capability (`capabilities.sections`); the labels are the lexicon's `short_plural` nouns.

**Dashboard layout priority (rule-driven, not alphabetical):**

1. **Hero** — the Thing's title and, where the template marks one, its primary identifier (`OverviewHero`)
2. **Data card** — collapsible spec and component tree (`ThingDataCard`, `ThingSpecBlock`, `ComponentChips`); Edit and Manage Access for owners
3. **Down-state alert** — open top-priority defects (`AogAlertSection`) — **only if any exist**
4. **Critical alerts** — overdue / due-soon tasks (`CriticalAlertSection`) — **only if any exist**
5. **Maintenance summary** — log stats (`LogStatsSection`) once a log exists; otherwise `LogOnboardingCard`, whose copy is the template's `log_onboarding_hint`

When there are no overdue tasks, the data card expands by default. When work is overdue it collapses — attention goes to what matters first.

A Thing whose DNA this build cannot interpret renders `DegradedThingContent` instead of the sections (`template_system_design.md` §6.2).

---

## 9. Screen Layouts

### 9A. The shell and the switcher (`core/ui/adaptive/AdaptiveAppShell.kt`, `feature/shell/AdaptiveShellRoute.kt`)

The selected Thing **is** the page. There is no hero-metrics fleet grid; the switcher is a list of Things with two lines each — the name, and beneath it make and model or whatever spec field the template marks `is_identifier`.

```
COMPACT                                   MEDIUM and up
┌───────────────────────────┐             ┌──────────┬──────────────────────────────┐
│ ▾ Sling TSi   [Avatar]    │ ← top bar   │ ✈ Sling  │  Sling TSi          N532SL   │
│   (tap: switcher sheet)   │             │   TSi    │  ┌──── DATA ─────────────┐   │
│                           │             │ 🚗 Golf  │  │ …                     │   │
│   … section content …     │             │ 🏠 Home  │  └───────────────────────┘   │
│                           │             │ ──────── │  … section content …         │
│                           │             │ Dashboard│                              │
│ ┌───────────────────────┐ │             │ Squawks  │                              │
│ │ ⌂  🐛  ✓  📜  ⚙      │ │ ← nav pill  │ Tasks    │                              │
│ └───────────────────────┘ │             │ Logs     │                              │
└───────────────────────────┘             │ Settings │                              │
                                          └──────────┴──────────────────────────────┘
```

Rows carry the template's icon (`thingIcon`). A fleet with no Thing at all shows `FleetEmptyState` (`feature/fleet/viewing`) — add a Thing, or enter an invite code — and **Add** opens `PickThingTypeSheet`: a grid of the presets this build can render, which decides what the create form asks for.

### 9B. Creating a Thing (`feature/thing/update`, `feature/tasks/update/starter`)

Four steps, of which the last two are skippable (PRD §8.1):

1. **What is it?** — `PickThingTypeSheet`
2. **Identity** — the template's spec fields, labelled and validated by the template (`SpecFieldsSection`); a home asks for an address, an airplane for make, model, serial and tail number
3. **Components** — the slot tree, pre-filled (`ComponentTreeSection`); absent when `capabilities.components` is off
4. **Starter pack** — `StarterPackRoute`: the template's recommended schedule as per-item checkboxes, with **Skip** as a first-class button

The shell switches to the new Thing when the form closes.

### 9C. Dashboard (`feature/thing/dashboard/compose/tabs/OverviewTab.kt`)

```
┌──────────────────────────────────────────────┐
│ Sling TSi        N532SL                      │ ← OverviewHero (title + identifier, heroDisplay)
│                                              │
│ ┌──────── AIRCRAFT DATA (collapsible) ──────┐│ ← ThingDataCard; heading = the lexicon's thing noun
│ │ Make  Sling     Model  TSi     S/N  …      ││ ← ThingSpecBlock: the template's spec fields
│ │ ENGINE 1         Rotax 915       S/N  …    ││ ← ComponentChips: the slot tree, one row per
│ │ Propeller        Airmaster       S/N  …    ││   component (a home has none of this section)
│ └─────────────────────────────────────────────┘│
│                                              │
│ ┌─── ✈ AOG ALERT ────────────────┐          │ ← AogAlertSection (only if any); title is
│ │ [●] O2 pressure leaking         │          │   down_status_long — "Aircraft on Ground",
│ │            VIEW SQUAWKS         │          │   "Off the road", "Needs urgent attention"
│ └──────────────────────────────────┘          │
│                                              │
│ ┌─── MAINTENANCE DUE ──────────────────────┐│ ← CriticalAlertSection (only if overdue / due soon)
│ │ [●] 100-hour inspection  OVERDUE 03/15    ││
│ │ [●] Annual                 DUE 14 DAYS     ││
│ └────────────────────────────────────────────┘│
│                                              │
│ MAINTENANCE SUMMARY                          │ ← LogStatsSection: one cell per declared meter
│ ┌──────────────┐ ┌──────────────┐            │   plus the log count — or LogOnboardingCard
│ │ 1432.5 hrs   │ │ 1428.1 hrs   │            │   until the first log
│ │ AIRFRAME     │ │ ENGINE       │            │
│ └──────────────┘ └──────────────┘            │
└──────────────────────────────────────────────┘
```

On MEDIUM and wider tiers the same content lays out as hero → data card → a two-column rail (`DashboardLowerGrid`): recent logs and open defects side by side.

**Dashboard rules:**
- Down-state alerts above all — immediate operational stop, in the template's word for it
- Critical alerts below — compliance work requiring attention
- Health determines card expansion: no overdue → expand the data card; overdue → collapse it
- Every heading, label and empty line comes from the template; `Spacing.screenPadding` = 16dp on all content

### 9D. Squawks section (`feature/squawk/viewing/`)

```
┌──────────────────────────────────────┐
│ [  OPEN   |   CLOSED  ]              │ ← DualSegmentedFilter
│ ┌──────────────────────────────────┐ │
│ │ [AOG] [OPEN]                  →  │ │ ← SquawkCard (blocking border on the down tier)
│ │ O2 pressure leaking to cabin     │ │
│ │ 05/10/2026                       │ │
│ └──────────────────────────────────┘ │
│ ┌──────────────────────────────────┐ │
│ │ [MEDIUM] [OPEN]               →  │ │
│ │ Left brake dragging              │ │
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

**SquawkCard anatomy:** Left = PriorityBadge (`StatusChip`, tinted by tier; the top tier reads `down_status`) + StatusBadge (OPEN/ADDRESSED/DISMISSED). Right = chevron. Below = title (titleMedium/Bold) + description (bodySmall). Footer = date (labelSmall). Down-tier defects get `blocking.accent.copy(alpha=0.5)` border. Which priorities the form offers is `capabilities.priorities`.

### 9E. Tasks section (`feature/tasks/viewing/`)

```
┌────────────────────────────────────────┐
│ [ DUE (3)  |  HISTORY (12) ]           │ ← DualSegmentedFilter
│ ┌────────────────────────────────────┐ │
│ │ [OVERDUE]                          │ │
│ │ Oil change                         │ │
│ │ Every 5,000 mi or 6 months         │ │ ← rule summary in the template's meter unit
│ │ ────────────────────────────────── │ │
│ │ DUE   03/15/2026 · 84,500 mi       │ │
│ └────────────────────────────────────┘ │ ← TaskCard (error border)
│ ┌────────────────────────────────────┐ │
│ │ [DUE SOON]                         │ │
│ │ Annual                             │ │
│ └────────────────────────────────────┘ │ ← TaskCard (caution border)
└────────────────────────────────────────┘
```

An empty Due list shows `EmptyState` with the template's `task_hint`, and — when the Thing's DNA carries a starter pack and nothing has been added yet — an **Add recommended tasks** action that reopens `StarterPackRoute`.

### 9F. Logs section (`feature/logs/viewing/`)

```
┌──────────────────────────────────────┐
│ ┌──────────────────────────────────┐ │
│ │ [ENGINE]    1428.1 hrs        →  │ │ ← MaintenanceLogCard; the badge only on the airplane,
│ │ Replaced left magneto per        │ │   the reading in the first declared meter the log
│ │ SB-1234. Mag drop within limits  │ │   recorded (odometer on a car; nothing on a home)
│ │ ────────────────────────────────  │ │
│ │ 05/10/2026    Annual    J. Rivera│ │
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

**MaintenanceLogCard anatomy:**
- Top row: ComponentTypeBadge (airplane only; ENGINE→primaryContainer, AIRFRAME→surfaceContainerHigh, PROPELLER→secondaryContainer) + meter reading (dataSmall+onSurfaceVariant) + chevron
- Body: work description (bodyMedium+onSurface)
- Divider: outlineVariant at 0.3 alpha
- Footer: date (dataSmall) + linked task count (primary color) + technician name

### 9G. Components Reference

| Component | Source File | Pattern |
|-----------|-----------|---|
| AdaptiveAppShell | `core/ui/adaptive/AdaptiveAppShell.kt` | Tier-adaptive chrome: nav pill / rail / sidebar + switcher |
| FleetEmptyState | `feature/fleet/viewing/FleetEmptyState.kt` | Empty account: add a Thing or redeem an invite |
| PickThingTypeSheet | `feature/thing/update/PickThingTypeSheet.kt` | Bottom sheet, preset grid |
| StarterPackRoute | `feature/tasks/update/starter/StarterPackRoute.kt` | Checklist form with Add / Skip |
| ThingDataCard | `feature/thing/dashboard/compose/ThingDataCard.kt` | Collapsible spec block + component chips |
| DegradedThingContent | `feature/thing/dashboard/compose/DegradedThingContent.kt` | Read-only fallback for uninterpretable DNA |
| MaintenanceLogCard | `feature/logs/viewing/log/compose/MaintenanceLogCard.kt` | Card with optional component badge + divider |
| TaskCard | `feature/tasks/viewing/TaskCard.kt` | Card with icon + label/value + status border |
| SquawkCard | `feature/squawk/viewing/SquawkCard.kt` | Card with dual badges + title/desc |
| StatusChip | `core/ui/.../StatusChip.kt` | Pill, status-tier tinted |
| AogAlertSection | `feature/squawk/viewing/AogAlertSection.kt` | Icon + title + list + action bar |
| CriticalAlertSection | `feature/tasks/viewing/CriticalAlertSection.kt` | Title + list + action bar |
| LogStatsSection | `feature/thing/dashboard/compose/LogStatsSection.kt` | Title + card with one stat cell per meter |
| DetailSheet | `core/ui/.../DetailSheet.kt` | Bottom sheet on compact, end drawer above |
| EmptyState | `core/ui/.../EmptyState.kt` | Centered icon+title+desc+action |
| GroupedRows | `core/ui/.../GroupedRows.kt` | Settings-style grouped rows, checkbox rows |
| DualSegmentedFilter | `core/ui/.../DualSegmentedFilter.kt` | Two-segment list filter |

---

## 10. The Critical Status Hierarchy

This is the non-negotiable information priority that shapes every screen, in every domain. Status flows vertically, never buried by sort order.

1. **Down-state defects** (BLOCKING, red) — AOG on an airplane, Off the road on a car, Urgent on a home; immediate operational stop, always first
2. **CRITICAL** (OVERDUE, red) — overdue work
3. **CAUTION** (DUE SOON, amber) — approaching deadline
4. **POSITIVE** (COMPLIED, green) — completed / ready, in the template's `ready_status` word
5. **NEUTRAL** (normal, slate) — low-priority status

The tiers and their colors are fixed; only the words on them come from the template. When health is fully positive (no OVERDUE, no DUE SOON), the data card expands by default. When anything is overdue, it collapses — attention goes to what matters.
