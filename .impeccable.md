## Design Context

### Users
Aircraft owners and mechanics managing fleet details, maintenance logs, and inspection compliance.
Their context is professional — logging a 100-hour inspection after a long day, checking whether
an annual is due before a flight. The job to be done is reliable record-keeping and compliance
tracking with as little friction as possible.

### Brand Personality
**Dependable, Precise, Calm**
The interface must feel trustworthy above all else — users are relying on it to track safety-
critical dates and back up records securely. It should feel like a well-made instrument, not an
app trying to impress. Modern without being flashy; professional without being cold.

### Reference & Anti-Reference
**Reference**: Modern note-taking apps (Notion, Bear, Apple Notes) — clean surfaces, generous
whitespace, content-first layouts. The user is a pilot or mechanic, not a power-user navigating
a toolbar.

**Anti-reference**: Spreadsheets, Excel, complex editor UIs. Never expose raw complexity on a
primary screen. Multi-step operations belong in tabs or wizard-style flows, never inline.

### Aesthetic Direction
**Refined Minimalism**
High-quality typography and intentional whitespace carry the UI. Layout is uncluttered; density
is earned, not assumed. Complexity is revealed progressively — primary views show only what
matters now, detail and advanced actions emerge on demand.

Aviation palette identity is deliberate and must be preserved:
- **Primary**: Aviation Blue — instrument panel / Garmin G1000 reference
- **Accent**: Instrument Amber — advisory annunciators, used sparingly (≤10% of color moments)
- **Status**: Forest green (airworthy), dark amber (caution) — semantic, not decorative
- Dynamic color is disabled; the aviation palette is the brand

**Typography**
- Space Grotesk for all headlines and titles — precision without coldness
- JetBrains Mono for technical data (tail numbers, serials, tach times) — character alignment is semantic
- System sans for body and labels — readability in data-dense contexts

**Motion**
Smooth, continuous transitions — no jumps or snaps. Animations should feel like pages turning,
not views teleporting. Keep motion purposeful: guide attention, confirm actions, never decorate.

### Information Hierarchy
Safety-critical status (OVERDUE, DUE SOON) is always surfaced at the top of any list or
overview. Secondary data is available but not competing. Exploration is opt-in with a minimal
learning curve — no hidden gestures, no unlabeled icons.

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
