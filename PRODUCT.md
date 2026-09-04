## Design Context

### Users
People who maintain something they own and want a reliable record of it: an aircraft owner
logging a condition inspection, a homeowner who has just learned the water heater wants a flush,
someone tracking oil changes on a car, a boat, a bike. The founding audience is aviation and it
stays first-class — every screen must still read as it did to a pilot or mechanic — but the app
now serves seven kinds of Thing (airplane, car/motorcycle, bike, boat, home, custom) from one
codebase. The job to be done is the same in every domain: dependable record-keeping and
never missing a due date, with as little friction as possible.

Each Thing speaks its own vocabulary. An airplane has squawks, an AOG state, tail numbers and
tach time; a home has attention items, chores and no meters at all. The words, the fields, the
meters and the component tree come from the Thing's template, never from the code — see
`AGENTS.md` (Lexicon and capabilities). Copy that belongs to the whole account rather than to
one Thing stays neutral.

### Brand Personality
**Dependable, Precise, Calm**
The interface must feel trustworthy above all else — users are relying on it to track
safety-critical dates and back up records securely. It should feel like a well-made instrument,
not an app trying to impress. Modern without being flashy; professional without being cold.

### Reference & Anti-Reference
**Reference**: Modern note-taking apps (Notion, Bear, Apple Notes) — clean surfaces, generous
whitespace, content-first layouts. The user is an owner with a job to log, not a power-user
navigating a toolbar.

**Anti-reference**: Spreadsheets, Excel, complex editor UIs. Never expose raw complexity on a
primary screen. Multi-step operations belong in tabs or wizard-style flows, never inline.

### Aesthetic Direction
**Refined Minimalism**
High-quality typography and intentional whitespace carry the UI. Layout is uncluttered; density
is earned, not assumed. Complexity is revealed progressively — primary views show only what
matters now, detail and advanced actions emerge on demand.

The instrument palette is the brand and is shared by every domain — it is the app's heritage,
not a per-preset theme:
- **Primary**: Aviation Blue — instrument panel / Garmin G1000 reference
- **Accent**: Instrument Amber — advisory annunciators, used sparingly (≤10% of color moments)
- **Status**: Forest green (ready — "Airworthy" on an airplane, "Ready" on a car, "Good" on a
  home), dark amber (caution) — semantic, not decorative
- Dynamic color is disabled; the palette is the brand

**Typography**
- Space Grotesk for all headlines and titles — precision without coldness
- JetBrains Mono for technical data (identifiers such as tail numbers, VINs and hull IDs;
  serials; meter readings such as tach time and odometer) — character alignment is semantic
- System sans for body and labels — readability in data-dense contexts

**Motion**
Smooth, continuous transitions — no jumps or snaps. Animations should feel like pages turning,
not views teleporting. Keep motion purposeful: guide attention, confirm actions, never decorate.

### Information Hierarchy
Safety-critical status (OVERDUE, DUE SOON, and the template's down state — AOG, Off the road,
Urgent) is always surfaced at the top of any list or overview. Secondary data is available but
not competing. Exploration is opt-in with a minimal learning curve — no hidden gestures, no
unlabeled icons.

### Design Principles

1. **Dependability First**: Every interaction should reinforce trust. Confirmations for
   destructive actions, clear success states, offline-aware feedback. The user must never
   wonder whether their data was saved.

2. **Clarity over Density**: Visual hierarchy over information packing. One primary action per
   screen. Status-critical information (overdue, due soon) always wins prominence.

3. **Minimal Friction**: Fast, intuitive data entry. Wizard or tab flows for complex operations
   — never expose multi-step complexity on a single form. Every field and button earns its place.

4. **Progressive Disclosure**: Keep primary views simple. Reveal advanced details and actions
   only when the user navigates deeper. No collapsed accordions on first load.

5. **Reliable Visual Language**: Consistent icons, spacing tokens, and color semantics across
   every screen. Predictability builds confidence. When in doubt, match existing patterns rather
   than introducing new ones.

6. **The Template Speaks**: Nouns, field labels, meter names, empty-state copy and status words
   come from the selected Thing's template. Never hard-code "aircraft", "tail number" or
   "airframe hours" into a screen every preset renders; never put a per-Thing noun on an
   account-level surface. A screen that reads right for a home and an airplane at once is the
   test.
