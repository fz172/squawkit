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
4dp corner radius (`badgeCornerRadius`) — reads as a stamp, not a pill. Tinted background from the relevant container color, matched on-container text. Three semantic variants: primary (addressed), surface-variant (dismissed/inactive), semantic (OVERDUE = error, DUE SOON = caution-amber).

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
