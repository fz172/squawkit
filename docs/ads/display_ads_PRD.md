# PRD: Display Ads (Free Tier)

> **Implementation status.** **Proposed — nothing has shipped.** No `feature/ads` module exists yet,
> no ad SDK is integrated, and the in-app comparison table
> ([`ProPaywallContent.kt`](../../feature/subscription/viewing/src/commonMain/kotlin/dev/fanfly/wingslog/feature/subscription/viewing/ProPaywallContent.kt))
> does not yet carry the Ad-free row specified in §9. The comparison table in
> [`subscription_PRD.html`](../subscription/subscription_PRD.html) §3 has been updated to match this
> PRD; the app-side row ships with the feature, not before it (advertising "ad-free" while no one
> sees ads is a lie in the other direction).

> **Tier naming.** Shipped user-facing copy calls the tiers **Core** (free) and **Heavy** (paid) —
> renamed from "Free" / "SquawkIt Pro" in #369, *display copy only*. The code keeps
> `Subscription.Status.FREE` / `PRO`, the `subscription_col_free` / `subscription_col_pro` resource
> ids, and the `SquawkIt Pro` RevenueCat entitlement id. This PRD uses **Core / Heavy for anything a
> pilot reads** and the enum names when talking about gating. The subscription PRD's HTML table
> still carries the pre-rename copy, so §9 mirrors it as-is rather than half-renaming someone else's
> doc.

**Owner:** Product · **Status:** Proposed · **Date:** 2026-07-28
**Revised:** 2026-07-29 — session cap 10 → 5 (D1); AdMob on Android/iOS in v1 (D6); web scheduled as
phase 2 on Ad Manager rather than dropped (D5, §8.2); fixed `BANNER` sizes (D7); no daily cap (D8);
free aircraft limit 1 → 2, implemented in this change (D9)
**Related:** [Subscription PRD](../subscription/subscription_PRD.html) ·
[Subscription Design](../subscription/subscription_design.html) ·
[Product overview](../product/PRD.md)

---

## 1. Summary

Introduce **display ads on the free tier only**, rendered as ordinary cards interleaved into the
three record lists a pilot actually lives in — **squawks**, **maintenance tasks**, and **maintenance
logs** — at a cadence of **one ad every 10 items**, with a single trailing ad for lists shorter than
10 items and **no ad at all when a list is empty**. Volume is bounded by a hard **cap of 5 ad units
per session** across all surfaces, and wide layouts render **thin** units — two side by side, or one
centered — so a full-width slot never becomes a billboard. The paid tier (**Heavy**) remains
completely ad-free, and "Ad-free" becomes a line in the tier comparison table, giving the paywall a
second, continuously visible argument.

v1 ships on **Android and iOS via AdMob**; the web host renders a no-op slot and gets ads in **phase
2** on Google Ad Manager (§8.1, §8.2). Because every placement rule below is host-independent common
code, phase 2 adds one platform `actual` and nothing else.

Ads are a **revenue floor for users who will never subscribe**, not a replacement for the
subscription. Every design decision below resolves in favor of the record, not the ad: ads never
appear above safety-critical status, never interrupt a flow, never occupy an empty state, and never
take a full screen.

---

## 2. Problem & Background

The [Subscription PRD](../subscription/subscription_PRD.html) commits to a free tier that is
*deliberately generous*: a complete two-aircraft logbook with cloud backup and multi-device sync
free for every signed-in account. That is the right product call and it is also a permanent,
uncapped cost — every free account consumes Firestore reads/writes, sync bandwidth, and Cloud
Functions time indefinitely.

Today that cost has exactly one offset: conversion to Pro. Most free users in a consumer utility app
never convert, and for that majority the app is pure expense. Display ads monetize the
non-converting free population at a per-user rate that is small but non-zero and, critically,
**scales with engagement rather than with intent to pay**.

There is a second effect worth stating plainly: an ad in the list is a standing, honest reminder
that a paid tier exists. We expect ads to *raise* free→Pro conversion, not depress it — and §12
makes conversion a tracked guardrail rather than an assumption.

---

## 3. Goals

- Monetize the free tier with **in-list display ads** on squawks, maintenance tasks, and maintenance
  logs.
- Keep the free tier's *function* fully intact — ads add a card, they never remove or gate a
  capability.
- Hold total exposure to **5 ad units per session** and thin, card-sized creatives at every layout
  tier.
- Make **"Ad-free"** a first-class line of the Pro value proposition, in the docs table and in the
  app.
- Ship behind a **build-time capability gate** so the feature can land dark and be enabled per
  platform.
- Enforce ad-free for Pro **reactively** — the moment entitlement resolves to Pro, ads disappear
  without a restart; on downgrade at period end they return.
- Respect consent (GDPR/UMP, iOS ATT) and never expose aircraft or maintenance data to an ad
  network.

## 4. Non-Goals

- **Ads on any paid tier.** Pro is ad-free, permanently and without qualification.
- **Interstitials, pop-ups, rewarded ads, video, or app-open ads.** In-list display cards only.
- **Web ads in v1.** Web is **phase 2**, not cancelled: the decision that web carries ads is made,
  and the product is Google Ad Manager (§8.1, D5/D6). It is out of the *first release* because its
  blocker is an account approval outside our control (W1) and because it needs a second ad product
  and a second CMP. The Kotlin/JS host ships a no-op ad slot in v1.
- **A single ad network across all hosts.** Mobile uses AdMob, web will use Ad Manager — there is no
  AdMob SDK for a browser, so two products is the floor, not a choice. Unifying them (e.g. moving
  mobile onto Ad Manager too) is a possible phase-3 consolidation, not a goal (§8.2).
- **Ads anywhere outside the three record lists** — no ads on dashboards, detail sheets, forms,
  wizards, pickers, settings, export flows, the technician list, search results, or the AOG /
  critical-alert sections.
- **Removing ads as a standalone one-time purchase.** Ad removal is a Pro benefit, not a separate
  SKU.
- **Using logbook content for targeting.** Contextual/network targeting only; see §10.
- **Server-side ad mediation or a house-ad system.** v1 uses AdMob direct on mobile (D6); whether
  mediation is worth adding is deferred to Q7, after real fill-rate data.

---

## 5. Principles & Guardrails

These are binding constraints, not aspirations. A change that violates one is a bug.

| # | Guardrail |
|---|---|
| G1 | **Safety-critical status always outranks an ad.** Overdue / due-soon / AOG content is never displaced, pushed below the fold by an ad, or separated from its group by one. |
| G2 | **Never the first card.** A list's first rendered card is always a record. |
| G3 | **Never in an empty, error, or loading state.** Zero records → zero ads (§6.4). |
| G4 | **Never two ads adjacent**, and never more than one ad in a typical viewport — the interval of 10 guarantees this at every supported layout tier. |
| G5 | **No layout shift.** A slot occupies zero height until it has a filled ad; it never renders a skeleton, spinner, or reserved gap that moves records under the user's finger. |
| G6 | **Clearly labeled.** Every ad card carries a "Sponsored" label and is visually distinct from a record card. An ad must never be mistakable for a squawk, task, or log. |
| G7 | **Never blocks an action.** No ad is modal, dismissable-only, or placed under a tap target. Scrolling past an ad is the only interaction required. |
| G8 | **Status colors are reserved.** The ad card uses neutral surface/outline tokens only — never the amber/red status palette, which means "your aircraft needs attention" and nothing else. |
| G9 | **Bounded volume.** At most **5 ad units per session**, globally across all surfaces, and no timed refresh (§6.7). The cadence is a ceiling on placement; the cap is a ceiling on exposure — and at 5 units the cap, not the cadence, is what a heavy user actually hits. |
| G10 | **No billboards.** Ad height never exceeds the record card beside it. On wide layouts the slot uses its width for a second thin unit or centered padding — never for a taller, stretched creative (§7.1). |

---

## 6. Placement Specification

This is the normative part of the PRD. Implementations must match it exactly; §6.6 is the test
matrix.

### 6.1 Surfaces in scope

| Surface | Where | Layout today |
|---|---|---|
| **Squawks list** | `SquawkTab` (aircraft dashboard) | `AdaptiveCardList` of `SquawkCard`, separate Open / Closed sub-views |
| **Maintenance tasks list** | `MaintenanceTasksTab` → `ComplianceSection` | `AdaptiveCardList` of `TaskCardItem`, grouped by status |
| **Maintenance logs list** | `MaintenanceLogListContent` | `LazyColumn` of log cards (compact); `MaintenanceLogTable` at MEDIUM+ |

No other surface renders an ad in v1.

### 6.2 Cadence

Let `n` be the number of **rendered** items in the list (i.e. after search and filters are applied),
and let the interval be **10**.

1. `n == 0` → **no ad.** The empty state stays clean.
2. `0 < n < 10` → **exactly one ad, as the last card**, after the final item.
3. `n >= 10` → an ad **after every 10th item** — after items 10, 20, 30, … — and **no trailing ad
   for the remainder**.

Rule 3 means `n == 10` produces one ad which is also the last card (rules 2 and 3 agree at the
boundary), and `n == 25` produces ads after items 10 and 20 — the trailing 5 items get nothing. The
short-list rule exists so that a small logbook still shows *an* ad; it is deliberately **not**
applied to trailing remainders, which would double ad density on long lists for no revenue reason
worth the annoyance.

### 6.3 Reference algorithm

A pure, testable function in `feature/ads/model` — no Compose, no platform types:

```kotlin
/** One rendered row: either a record or an ad slot. */
sealed interface ListRow<out T> {
  data class Item<T>(val value: T) : ListRow<T>
  /** [slotIndex] is 0-based and stable for a given list size — use it as the Lazy list key. */
  data class Ad(val slotIndex: Int) : ListRow<Nothing>
}

/**
 * Interleaves ad slots per the display-ads PRD §6.2:
 * empty -> none; shorter than [interval] -> one trailing ad; otherwise one after every
 * [interval]-th item, with no ad for a trailing remainder.
 */
fun <T> withAdSlots(items: List<T>, interval: Int = 10): List<ListRow<T>>
```

Callers wrap their list only when ads are enabled (§8); when disabled they render `items` directly,
so the ad-free path costs nothing.

**Stable keys.** `Ad(slotIndex)` is the Lazy list key for the slot. Slot identity must not change as
items load in, or Compose will tear down and re-request a filled ad.

**Index mapping.** Existing index-based behavior operates on the *item* list, never the display
list. The logs list scrolls to a target log by index (`scrollToLogId` pinning in
`MaintenanceLogListContent`); with ads inserted, that lookup must map entity id → **display** index.
Getting this wrong scrolls a pilot to the wrong log, so it is called out as a functional requirement
(F8), not a detail.

### 6.4 Empty, error, and loading states

No ad is rendered when the list is empty for **any** reason: a brand-new aircraft with no records, a
search or filter that matches nothing, a load error, or the loading state before first emission.
`EmptyState` is never accompanied by an ad card. Requirement 4 of the brief, stated as an invariant:
**the ad count is a function of the rendered item count, and `f(0) = 0`.**

### 6.5 Grouped, toggled, and wide layouts

- **Grouped lists** (Compliance's status groups): the counter runs **continuously** across the
  groups in a single scroll, and `n` is the total across groups. A slot that lands on a group
  boundary renders at the **end of the preceding group** — never between a header and its first card
  (G1).
- **Toggled sub-views** (squawks Open ↔ Closed): each sub-view is its own list with its own counter
  and its own `n`. Switching the toggle re-evaluates from scratch.
- **Multi-column tiers** (`AdaptiveCardList` with `columns > 1`): the ad occupies a **full-width row
  spanning all columns**, inserted after the row containing the 10th item. Cadence stays per-item,
  not per-row — a 2-column tier still gets one ad per 10 records.
- **Wide log table** (`MaintenanceLogTable`, MEDIUM+): **ads are shown here too.** The slot renders
  as a full-width **band between table rows**, matching the table's horizontal insets, after every
  10th row. The band is chrome, not data: it carries the "Sponsored" label and never adopts the
  table's column rules, zebra striping, or row height — a pilot scanning a column of dates must
  never have to parse an ad as a row. An ad is never jammed into a data cell.

Wide slots — grid rows and table bands alike — use the thin two-up / centered format in §7.1, which
is what keeps a full-width slot from becoming a billboard.

### 6.6 Worked examples (test matrix)

Positions are the **slots** the cadence produces; the session cap in §6.7 decides how many of them
actually fill, and §7.1 decides how many ad units live inside each one.

| Rendered items `n` | Ad slots | Slot positions (after item #) |
|---|---|---|
| 0 | 0 | — (empty state, no ad) |
| 1 | 1 | 1 (last card) |
| 7 | 1 | 7 (last card) |
| 9 | 1 | 9 (last card) |
| 10 | 1 | 10 (last card) |
| 11 | 1 | 10 |
| 19 | 1 | 10 |
| 20 | 2 | 10, 20 (second is last card) |
| 25 | 2 | 10, 20 |
| 100 | 10 | 10, 20, … , 100 |

Note the last row: the cadence produces 10 slots, but the 5-unit session cap (§6.7) fills at most
the first five, so the back half of a very long list renders no ads at all. Slot *positions* are a
pure function of `n`; slot *fills* are not.

### 6.7 Session cap — at most 5 ads per session

The cadence decides *where* ads may appear; the cap decides *how many* actually do.

**No more than 5 ad units are displayed per app session**, counted **globally across all three
surfaces**, not per list. A pilot who scrolls a 400-entry logbook, switches to squawks, and comes
back sees 5 ads, not 60.

At an interval of 10, the cap binds early and often: a single 60-item list exhausts it on its own.
That is intentional — the cadence exists to space ads out *within* a list, and the cap is what holds
a working session to a handful of impressions no matter how much scrolling it contains.

| Rule | Definition |
|---|---|
| **What counts** | One **filled ad unit** counts as one against the cap, at the moment it is first displayed. A slot holding two side-by-side units (§7.1) counts as **two**. Unfilled slots, collapsed slots, and failed requests count as **zero**. |
| **Session boundary** | A session starts on cold start or on returning to the foreground after **≥ 30 minutes** in the background — the same convention as the analytics session, so ad volume and session counts stay comparable. The counter resets to 0 at each boundary and lives in an app-scoped singleton, not in a screen. |
| **At the cap** | Once 5 units are displayed, every remaining slot in the session **renders at zero height** — no request, no label, no gap. Slots already filled stay as they are; ads do not disappear from under the user. |
| **Near the cap** | With exactly one unit of headroom left, a wide slot that would render two-up falls back to **one centered unit** (§7.1) rather than overshooting the cap or rendering a half-empty band. |
| **Scroll churn** | A slot's fill is cached per slot key for the lifetime of the screen, so scrolling up and down past the same ad does **not** consume more of the cap. A slot destroyed by memory pressure (N5) and later refilled does count again — this is the one accepted source of over-count, and it is bounded by N5's live-view budget. |
| **No refresh** | Ad units **never auto-refresh** on a timer. A displayed ad is a single impression for as long as it lives. Timed refresh would burn the cap in minutes and is out of scope, in v1 and after. |

The cap is a **product guardrail, not a revenue dial**: 5 units per session is deliberately below
what the cadence alone would produce in a long working session, and raising it is a decision to be
argued from §12's retention guardrails, not tuned quietly.

---

## 7. The Ad Card

- **Shape and rhythm:** same corner radius, horizontal insets, and inter-card spacing as the record
  cards around it, using existing `Spacing` tokens — the ad sits *in* the list, it does not float
  over it.
- **Label:** a small "Sponsored" label in `onSurfaceVariant`, top-left of the card, always present
  (G6).
- **Surface:** neutral `surfaceVariant`/`outlineVariant` treatment, no status colors (G8), no
  dynamic color (per `DESIGN.md`).
- **Upgrade affordance:** a low-emphasis footer link — **"Remove ads with Heavy"** — navigating to
  the subscription screen. This is the ad card's only in-app tap target besides the ad creative
  itself, and it is what makes the ad an argument for Pro rather than only a nuisance.
- **Format:** a fixed-size display unit (`BANNER`, 320×50) — see §7.1 for how the unit(s) are laid
  out per layout tier, and §7.2 for the size decision.
- **Unfilled:** zero height, no label, no footer — the row collapses entirely (G5).

### 7.1 Keeping the aspect ratio honest on wide layouts

A full-width slot on a tablet or desktop window is 700–1400 dp across. Stretching one display unit
to fill it produces a billboard — the single most out-of-place thing that could appear in a
maintenance record list. The slot therefore always hosts **thin** units, and uses the width for
*count*, not for height.

| Layout tier | Slot contents | Rationale |
|---|---|---|
| **COMPACT** (< 700 dp) | **One** `BANNER` unit (320×50), centered in the card width. | The classic banner size, the deepest demand pool, and only 50 dp tall — no record card is shorter than that. |
| **MEDIUM** (700–1039 dp) | **One unit, centered** in the band, neutral padding either side. | Not wide enough for two readable units; centering keeps the band from looking like a stretched banner. |
| **EXPANDED / LARGE** (≥ 1040 dp) | **Two units side by side**, each centered in half the band minus the grid gutter — aligned to the same gutter `AdaptiveCardList` uses at `cardColumns == 2`. | Two normal-width ads read as content in a grid; one stretched ad reads as a takeover. |

### 7.2 Ad unit sizes — fixed, not adaptive (D7)

v1 requests **fixed** AdMob sizes rather than inline adaptive units:

| Size | Dimensions | Where |
|---|---|---|
| `BANNER` | 320 × 50 dp | COMPACT, and the default everywhere |
| `LARGE_BANNER` | 320 × 100 dp | Permitted **only** where the neighbouring record card is measurably taller than 100 dp (see the G10 note below) |

Fixed sizes are chosen because they make §7.1's guarantees **structural rather than enforced**: a
320 dp-wide creative physically cannot stretch into a billboard, and a known 50 dp height satisfies
the 120 dp cap (N9) and G10 by construction, with no runtime clamping of a network-chosen height.
The trade-off is accepted knowingly — adaptive units generally yield better because they request the
best-fitting size, so §12's fill-rate and revenue numbers are the input to revisiting this, not a
guess made now.

Consequences worth stating, because they differ from the adaptive assumption this section previously
carried:

- **Units are centered, never stretched.** A fixed 320 dp unit in a ~500 dp half-band leaves neutral
  padding either side. That is the intended look, not a layout bug — the padding is what stops the
  band reading as a takeover.
- **`MEDIUM`/`EXPANDED` do not get a wider creative.** Width buys *count* (§7.1), never size.
- **⚠ `LARGE_BANNER` needs a measurement before use.** G10 says ad height never exceeds the record
  card beside it, and record cards have **no fixed height** — `SquawkCard` is a `Card` + `Column`
  sized by content, so a short squawk (title, chip, one-line description, date) lands in roughly the
  90–110 dp range. `LARGE_BANNER` at exactly 100 dp therefore sits *at* that boundary and would
  violate G10 next to a short card. **Ship `BANNER` first; adopt `LARGE_BANNER` only after measuring
  real card heights on each surface at COMPACT.** If cards measure under ~110 dp, `LARGE_BANNER` is
  out on phones regardless of its revenue advantage — G10 is a guardrail, not a preference.

Additional rules:

- **Thin means thin.** A slot is capped at **120 dp** tall and must never exceed the height of the
  record card beside it. Both sizes above clear the numeric cap; only G10's card-relative comparison
  can rule one out. The COMPACT "30% of viewport height" bound is now moot — a 50 dp unit never
  approaches it — but stays in N9 as a backstop if adaptive units are ever revisited.
- **Partial fill recenters.** If a two-up slot gets only one unit filled — no fill, or one unit of
  cap headroom left (§6.7) — the filled unit renders **centered**, exactly as the MEDIUM case. A
  band is never shown half-empty.
- **No fill collapses.** Both units unfilled → the whole row is zero height (G5).
- **The band is one slot.** "Sponsored" is labeled **once** per slot, not once per unit, and the
  "Remove ads with Heavy" link appears once, at the band's trailing edge.
- **Two units, two impressions.** A two-up slot counts as two against the session cap and emits two
  `ad_impression` events (§12), distinguished by a `unit_position` param.

---

## 8. Tier Gating

Ads are gated by the same entitlement machinery as every other tier-sensitive capability — the
`SubscriptionManager` gate in `feature/subscription/datamanager`, reading the server-authoritative
entitlement out of the local store.

```kotlin
/** True only for the free tier, on a build/platform where ads are supported. Default-OFF. */
fun showsAds(): Flow<Boolean>
```

Three properties matter:

1. **Free only.** `showsAds()` is `true` only while the effective `Subscription.Status` is `FREE`.
   Any paid tier — now or in future — is ad-free. Because the gate reads the status enum rather than
   a boolean, a future tier is ad-free automatically.
2. **Default-OFF, the mirror of the feature gates.** The existing gates are *default-open*: while
   `AppCapability.isSubscriptionSupported` is `false` there is no paywall and every premium
   capability reads available. Ads invert that: when subscriptions are not supported, or ads are not
   supported for this build/platform, `showsAds()` is `false`. A build with no way to buy Pro must
   never show ads a user cannot remove.
3. **Reactive.** It is a `Flow`, driven by the same entitlement stream as the rest of the gates: the
   moment a purchase resolves, in-flight ad slots disappear without a restart; on expiry, they
   return.

**New capability flag.** `AppCapability` gains `isAdsSupported`, following the staged-rollout
pattern of `isSubscriptionSupported` / `isAircraftSharingSupported`: a build-time gate, on in dev
and dogfood, off in the shipping release until GA. It is flipped **per host**, so Android, iOS, and
(in phase 2) web each go GA on their own schedule. It is `false` on the Kotlin/JS host for all of
v1. Developer Options gains a **Force ads** toggle (developer builds only) so ad placement can be
exercised without a real free account.

### 8.1 Hosts and ad products

Every host eventually carries ads, in two phases, with **two ad products** — because no AdMob SDK
exists for a browser, this is a constraint rather than a preference.

| Phase | Host | Ad product | Client seam |
|---|---|---|---|
| **v1** | **Android** | AdMob (Google Mobile Ads SDK), fixed `BANNER` (§7.2) | `AdView` in `androidMain` |
| **v1** | **iOS** | AdMob (`GADBannerView`), fixed `BANNER` (§7.2) | `GADBannerView` in `iosMain`, plus ATT/UMP (§10, P3) |
| **v1** | **Web** | *None* — no-op slot, renders nothing | `jsMain` actual returns an empty composable |
| **Phase 2** | **Web** | **Google Ad Manager** (GPT tag, not AdMob) | Ad Manager tag in `jsMain`; see §8.2 |

What matters for v1 is that **everything normative in this PRD is host-independent**: the cadence
(§6.2), the empty-list rule (§6.4), the 5-unit session cap (§6.7), the thin-unit layout rules
(§7.1), and the tier gate above all describe placement, not networks. Phase 2 adds a new `actual`
behind the N4 seam and changes nothing else in this document. That is the point of specifying
placement separately from the ad product.

**Why AdMob for mobile rather than Ad Manager everywhere.** Ad Manager can serve all three hosts
from one ad server, and it was considered. It was rejected for v1 because the *client* work is
nearly identical (same Google Mobile Ads SDK; `AdManagerAdView` vs `AdView` and a different
ad-unit-id format) while the *setup* is not: Ad Manager requires trafficking line items, priority
tiers, and backfill configuration, where AdMob is an ad unit id and default-on optimization. Paying
that ad-ops cost on Android and iOS in v1 buys a consolidation benefit that only pays off once web
ships — which is gated on W1, an approval we do not control. Mobile takes the low-overhead path now
and keeps consolidation available later (§8.2).

### 8.2 Phase 2 — web via Ad Manager

Decided (D5, D6), scheduled after mobile GA. Recorded here so phase 2 starts from a spec rather than
a rediscovery. None of the following is v1 work.

| ID | Phase-2 requirement |
|---|---|
| W1 | **Account approval is the gating dependency, and it is a business task, not an engineering one.** AdSense review expects the monetized pages to be crawler-reachable; the SquawkIt web app is sign-in-only, a well-known rejection cause — which is why the product is **Ad Manager**, the standard route for logged-in inventory. Phase 2 does not start until an account is approved for the sign-in-gated surface. Owner and start date for this application are the first thing phase 2 needs, because lead time here dominates the engineering. |
| W2 | **A separate web CMP.** Google UMP (§10, P2) is a mobile SDK. The web host needs its own consent solution for EEA/UK visitors satisfying the same P1/P2 guarantees. No ad tag loads before consent clears. |
| W3 | **The GPT tag loads lazily and never blocks the app.** Injected only once `showsAds()` first emits `true` (P6), `async`, and a blocked or failed script leaves the slot at zero height (G5, N3). Desktop ad-blocker prevalence is high; a blocked tag is a normal silent outcome, not an error state. |
| W4 | **Web is a wide layout by default,** so it is the surface most exposed to the billboard risk in §7.1. The 120 dp cap and the two-up thin format are load-bearing there: requested Ad Manager creative sizes must be thin units, never a leaderboard or half-page. |
| W5 | **Web ads reuse the v1 placement code unchanged.** `withAdSlots()`, the session counter, `AdSlotFormat`, and `AdSlotCard` are already common code; phase 2 supplies a `jsMain` `actual` and nothing more. If phase 2 finds itself changing shared placement logic, something was specified wrong in v1. |

**Possible phase 3 — consolidation.** If running two dashboards proves costly, mobile can move onto
Ad Manager: a class-name and ad-unit-id swap inside the two mobile actuals, plus the ad-ops
migration. Cheap on the client, deliberately deferred until there is evidence it is worth the ops
work.

**Guests.** An anonymous/guest account is on the free tier and sees ads under the same rules. In
practice a new guest sees none for a while, because their lists are empty (§6.4) — the onboarding
path is ad-free as a consequence of the placement rules, not as a special case.

**Shared aircraft.** Ads follow the *viewer's* tier, not the host's. A free-tier mechanic viewing a
Pro owner's shared aircraft sees ads; the Pro owner does not.

---

## 9. Subscription Comparison Table (required update)

Ad-free becomes a listed Pro benefit. The canonical table in
[`subscription_PRD.html`](../subscription/subscription_PRD.html) §3 has been updated to:

| Capability | Free | SquawkIt Pro |
|---|---|---|
| Aircraft | **2** | Unlimited |
| Maintenance logs, tasks & squawks | ✓ | ✓ |
| Export to this device (CSV / XLSX / PDF / ZIP) | ✓ | ✓ |
| Photo & file attachments | — | ✓ |
| Email a copy of exports | — | ✓ |
| Cloud backup & multi-device sync | ✓ | ✓ |
| **Ad-free experience** | **—** | **✓** |
| Share aircraft & invite others | — | ✓ |
| Future premium features | — | ✓ |

**The Aircraft row reads 2 because D9 shipped with this change.** Raising the free limit from 1 to 2
is a subscription-tier change rather than an ads change, but it is implemented here rather than
deferred. Five places carry the number and all five moved together:

| # | Location | Now |
|---|---|---|
| 1 | `SubscriptionManagerImpl.FREE_AIRCRAFT_LIMIT` (`feature/subscription/datamanager`) | `2` — the **only** enforcement point |
| 2 | `SubscriptionManagerImplTest` — asserts the free limit | `2` |
| 3 | `SubscriptionUiStateTest` — fake `aircraftLimit()` | `2` |
| 4 | `subscription_aircraft_free` in `feature/subscription/viewing` `strings.xml` | `"2"` — the user-facing cell |
| 5 | [`subscription_PRD.html`](../subscription/subscription_PRD.html) — R3, the §3 table, pricing rows, personas, paywall trigger #1 | `2` |

Three properties of the change worth recording here, since this PRD is what motivated it:

- **The limit is enforced client-side only.** No Cloud Function or Firestore rule checks aircraft
  count, so raising it was purely a client change — and equally, the limit has never been
  server-authoritative. Pre-existing, not introduced by D9, but it is now load-bearing for a number
  that is one constant away from being wrong.
- **It moves the primary conversion trigger.** The subscription PRD lists *"Add 2nd+ aircraft"* as
  paywall trigger #1, and its Fleet-operator persona converted specifically to "log more than one
  aircraft at all." At a limit of 2 that trigger becomes the **3rd** aircraft, so a two-plane owner
  — owner plus a partnership share, a common GA pattern — never hits it. Ad revenue plus
  attachments, email export, sharing, and ad removal carry conversion for that persona instead. Both
  the trigger and the persona rows in `subscription_PRD.html` were updated to say so rather than
  left contradicting the new limit.
- **User-facing prose deliberately omits the count.** The comparison subhead reads "Core is a
  complete logbook, backed up and synced" — the number lives only in the Aircraft table cell
  (`subscription_aircraft_free`). This is why the list above is five places and not more: keep the
  count out of sentences and the next limit change stays a one-cell edit instead of a copy sweep.
  Row 4 is the only user-visible occurrence, by design.

Row order mirrors the shipped HTML table; the *Ad-free* row is inserted after *Cloud backup &
multi-device sync* and before *Share aircraft & invite others*. Mid-table rather than appended,
deliberately: "Future premium features" is the table's closing catch-all line, and a concrete,
shipped benefit should not sit below it. Column headers above are the doc's pre-rename copy; the
in-app table's columns already read **Core** and **Heavy** (see the tier-naming note at the top),
and this row inherits those headers unchanged.

**In-app parity (ships with the feature, not before).** The same row is added to the
`ComparisonTable` composable in `ProPaywallContent.kt`:

```kotlin
CompareRow(stringResource(Res.string.subscription_feature_ads), Cell.No, Cell.Yes)
```

with a new string `subscription_feature_ads` = **"Ad-free experience"**, inserted after
`subscription_feature_backup`. Free renders the em-dash `Cell.No`, Pro the check. The row must not
appear in a build where `isAdsSupported` is `false` — the comparison table has to describe the build
the pilot is holding.

---

## 10. Privacy & Consent

| ID | Requirement |
|---|---|
| P1 | **No logbook data leaves the app for ad purposes.** Aircraft tail numbers, squawk text, log entries, technician records, attachments, and account identifiers are never passed to an ad SDK as targeting signals, keywords, or custom parameters. |
| P2 | **Consent gate.** In GDPR/UK-GDPR regions, a CMP (Google UMP or equivalent) collects consent before the first ad request. Declined or unavailable consent → **non-personalized ads**; ad requests are never made in a state that the CMP has not cleared. |
| P3 | **iOS ATT.** The App Tracking Transparency prompt is requested at first ad-eligible list render, not at launch, and declining is fully supported (non-personalized ads). |
| P4 | Consent state is re-presentable from Settings ("Ad privacy settings") wherever the CMP requires it. |
| P5 | Store privacy disclosures (Play Data Safety, App Store Privacy Nutrition Labels) are updated **before** the first release that ships ads enabled. |
| P6 | The ad SDK is initialized **lazily**, only once `showsAds()` first emits `true` — a Pro user's app never starts an ad SDK at all. |

---

## 11. Requirements

### Functional

| ID | Requirement |
|---|---|
| F1 | Ad cards are interleaved into the squawks, maintenance tasks, and maintenance logs lists per §6.2. |
| F2 | An ad appears after every 10th rendered item; a list of 1–9 items gets exactly one ad as its last card. |
| F3 | An empty list — including empty-by-filter, error, and loading — renders **no** ad. |
| F4 | Ads are shown only when the effective subscription status is `FREE`; any paid tier is ad-free. |
| F5 | Purchasing Pro removes ads reactively, without an app restart; expiry restores them. |
| F6 | Ads are gated behind `AppCapability.isAdsSupported` (off in the shipping release until GA), flipped per host so Android and iOS go GA independently; `false` on the Kotlin/JS host for all of v1 (§8.1). |
| F18 | v1 renders ads on **Android and iOS via AdMob**. The Kotlin/JS host compiles the shared placement code and renders a no-op slot (§8.1). |
| F19 | Placement, cadence, cap, and layout rules live entirely in common code, so adding the phase-2 web `actual` requires no change to any of them (§8.2, W5). |
| F7 | Each ad card is labeled "Sponsored" and carries a "Remove ads with Heavy" link to the subscription screen. |
| F8 | Index-based list behaviors (notably logs jump-to-log / `scrollToLogId`) resolve against the **display** list so ads never shift a scroll target. |
| F9 | An unfilled or failed ad slot renders at zero height and never leaves a gap or placeholder. |
| F10 | Developer Options exposes a **Force ads** toggle in developer builds. |
| F11 | The subscription comparison table (doc **and** app) lists "Ad-free experience" as a Pro benefit (§9). |
| F12 | Ad load, impression, click, upsell-tap, and fill-failure are instrumented (§12). |
| F13 | **At most 5 ad units are displayed per session**, counted globally across all three surfaces; beyond the cap every slot renders at zero height and issues no request (§6.7). |
| F14 | The session counter resets on cold start and on foregrounding after ≥ 30 minutes in the background, and lives in an app-scoped singleton. |
| F15 | Wide slots render **two thin units side by side** at EXPANDED/LARGE and **one thin centered unit** at MEDIUM; a partially filled two-up slot recenters its single filled unit (§7.1). |
| F16 | Ads render in the wide log table as a full-width band between rows, visually distinct from data rows (§6.5). |
| F17 | Ad units never auto-refresh on a timer. |

### Non-Functional

| ID | Requirement |
|---|---|
| N1 | Scroll performance: no measurable frame-time regression on lists of 200+ items with ads enabled versus disabled. |
| N2 | No layout shift: inserting a filled ad must not move items already on screen (fill happens off-screen or on first composition of the slot). |
| N3 | Offline: with no network the slot stays collapsed; no error UI, no retry storm. Ad failures are never surfaced to the user. |
| N4 | The ad SDK is not a dependency of any `model` or `datamanager` module — it lives behind an `expect`/`actual` UI seam in `feature/ads/viewing` (AdMob on Android/iOS, no-op on JS in v1). The seam is what lets the phase-2 Ad Manager tag drop in as a third `actual` without touching the placement spec. |
| N5 | Memory: at most a small bounded number of ad views are alive per screen; slots scrolled far off-screen release their ad view. |
| N6 | No ad SDK initialization, network call, or consent prompt occurs for a Pro user or an ads-unsupported build (P6). |
| N7 | Accessibility: the ad card is a single focus group announced as "Advertisement"; the upgrade link is separately focusable and labeled. A two-up band announces two units in reading order within that group. |
| N8 | A slot's fill is cached per slot key for the life of the screen, so scroll churn triggers neither a new request nor a second impression (§6.7). |
| N9 | Ad height is capped at 120 dp on MEDIUM and wider, and at the lesser of the unit height and 30% of viewport height on COMPACT (G10). With D7's fixed sizes both bounds are satisfied by construction (50 dp / 100 dp); the cap remains normative as a backstop if adaptive units are revisited (§7.2). |

---

## 12. Analytics & Success Metrics

Events, via the existing `AnalyticsManager.logEvent`:

| Event | Params |
|---|---|
| `ad_slot_filled` | `surface` (squawks/tasks/logs), `slot_index`, `unit_position` (`single`/`left`/`right`) |
| `ad_impression` | `surface`, `slot_index`, `unit_position`, `session_count` (1–5, this unit's ordinal in the session) |
| `ad_click` | `surface`, `slot_index`, `unit_position` |
| `ad_fill_failed` | `surface`, `reason` |
| `ad_upsell_tapped` | `surface` — the "Remove ads with Heavy" link |
| `ad_session_cap_reached` | `surface` — emitted once per session when the 5th unit displays; tells us how often the cap actually binds |

**Primary:** ad revenue per free DAU; fill rate; impressions per free session (bounded at 5 by F13 —
a distribution pinned at 5 means the cap is the binding constraint, which is a product conversation,
not a config change; at an interval of 10 we should *expect* it to pin for engaged users).
**Secondary:** `ad_upsell_tapped` → subscribe conversion — the ad card as a paywall entry point.
**Guardrails (a regression here overrides revenue):**
- Free→Pro conversion must not fall; the hypothesis is that it rises.
- Free-tier D7/D30 retention and sessions/user must not drop more than **2%** relative.
- Crash-free sessions and list scroll jank must not regress (N1).
- Store rating and "ads" complaint volume in reviews are watched for the first two releases.

---

## 13. Rollout

1. **Dark landing.** `feature/ads` merges with `isAdsSupported = isDeveloperBuild`; the shipping
   release is unaffected.
2. **Dogfood.** Placement, density, and the empty-state rule are validated on real logbooks; the
   comparison table row appears in dogfood builds only.
3. **GA gating.** Ads GA **cannot precede subscription GA** — `isSubscriptionSupported` must be
   `true` first, or free users would see ads with no way to remove them (§8, property 2).
4. **Staged enable (v1)** per host: **Android** first, then **iOS** after ATT flow validation. Both
   on AdMob. Web stays off.
5. **Phase 2 — web on Ad Manager**, after mobile GA has produced real revenue and retention numbers
   (§8.2). Its long-lead item is the W1 account approval, which can be started in parallel with
   mobile work; the engineering is small next to the approval wait.
6. **Kill switch — build-time, decided.** There is no remote config in the codebase today, and a
   build-time flag flip plus an expedited release is **accepted for v1**. No remote-config work is
   scheduled by this PRD. The exposure a slow kill switch buys is bounded by the guardrails that are
   already structural: ads are free-tier only, capped at 5 units per session, and never modal.

---

## 14. Decisions & Open Questions

### Decided

| # | Decision |
|---|---|
| D1 | **Session cap: 5 ad units**, global across surfaces, no timed refresh (§6.7, F13). Lowered from 10 on 2026-07-29 — one ad per 10 items read as too dense in review, and cutting the cap rather than widening the interval keeps short-list behavior unchanged while halving what a heavy session actually sees. |
| D2 | **Build-time kill switch is sufficient** for v1; no remote config (§13.6). |
| D3 | **The wide log table gets ads** — a full-width band between rows, not an ads-free surface (§6.5, F16). |
| D4 | **Wide slots use thin units** — two side by side at EXPANDED/LARGE, one centered at MEDIUM (§7.1, F15). |
| D5 | **Web carries ads — as phase 2, not v1** (decided 2026-07-29). The earlier "no web ads" non-goal is superseded: web is scheduled, not cancelled, with **Google Ad Manager** as the product since no browser AdMob SDK exists. v1 ships a no-op JS slot; phase 2 adds one `actual` (§8.2). |
| D6 | **AdMob for Android and iOS in v1** (decided 2026-07-29). Ad Manager can serve all three hosts from one server and was considered, but its trafficking/line-item overhead would be paid on mobile now for a consolidation benefit that only lands once web ships. Client migration cost later is a class-name and ad-unit-id swap, so the option stays cheap to exercise (§8.1, §8.2 phase 3). Supersedes the original Q1 (ad network choice); the narrower mediation question survives as Q7. |
| D7 | **Fixed ad sizes — `BANNER` (320×50), with `LARGE_BANNER` (320×100) permitted only where a measurement clears G10** (decided 2026-07-29, resolving Q2). Fixed rather than inline adaptive, so the no-billboard and height guarantees hold by construction instead of by runtime clamping; the yield trade-off is revisited from §12 data, not guessed. `LARGE_BANNER` is gated on Q8 because record cards are content-sized and may be shorter than 100 dp (§7.2). |
| D8 | **No cross-session (daily) frequency cap** (decided 2026-07-29, resolving Q4). The 5-unit per-session cap is the only volume bound; a pilot with many sessions a day may see 5 per session with no daily ceiling. Rationale: sessions are already separated by ≥ 30 minutes of background time (§6.7), so a high daily count means genuinely repeated engagement rather than one long scroll being milked. §12's retention and rating guardrails are what would reopen this. |
| D9 | **Free aircraft limit rises from 1 to 2** (decided 2026-07-29, resolving Q5). Ad revenue on the free tier makes a second aircraft affordable, and 2 covers the common owner-plus-partnership case that 1 excludes. Not an ads change, but implemented alongside this PRD — see the §9 note for all five places carrying the number, the client-side-only enforcement, and the effect on paywall trigger #1. |

### Open

| # | Question | Owner |
|---|---|---|
| Q7 | Whether AdMob mediation is worth adding on mobile once fill-rate data exists, or whether AdMob direct demand is sufficient. Deferred until §12 reports real fill rates. | Eng/Product |
| Q8 | Measured record-card heights at COMPACT on all three surfaces, which decide whether `LARGE_BANNER` is usable at all under G10 (§7.2). Blocks nothing in v1 — `BANNER` ships regardless. | Design/Eng |

**Phase 2 (web) open questions** — not blocking v1:

| # | Question | Owner |
|---|---|---|
| Q3 | Who owns the Ad Manager account application for the sign-in-gated web app, and when does it start? This is the long-lead item in phase 2 (W1); starting it early is nearly free and starting it late sets the phase-2 date. | Product |
| Q6 | Ad Manager creative sizes satisfying W4's thin-unit constraint — the size list for the two-up half-band, parallel to Q2 for mobile. | Design/Eng |

---

## 15. Implementation Sketch (non-binding)

Follows the canonical feature module pattern in [AGENTS.md](../../AGENTS.md):

```
feature/ads/
  model/          ListRow, withAdSlots(), AdSurface enum, AdSlotFormat (Single / TwoUp)
                    — pure Kotlin, unit-tested against §6.6
  datamanager/    AdsGate     (SubscriptionManager.showsAds() + AppCapability.isAdsSupported)
                  AdSessionCounter (app-scoped single: 5-unit cap, 30-min background reset)
  viewing/        AdSlotCard (common: label, band layout, upsell link) + expect/actual AdView
                    androidMain: Google Mobile Ads fixed BANNER (320x50, §7.2)
                    iosMain:     GADBannerView + ATT/UMP
                    jsMain:      v1:      no-op (renders nothing)
                                 phase 2: Ad Manager GPT tag (lazy, async, thin) + web CMP
```

- `SubscriptionManager` gains `showsAds()`; `AppCapability` gains `isAdsSupported`.
- Koin module registered in `core/di/CommonAppModules.kt`, per the module rules. `AdSessionCounter`
  is a Koin `single` — the cap is global, so exactly one instance may exist per app.
- `AdSlotFormat` is resolved from `LocalLayoutTier` plus the counter's remaining headroom, so §7.1's
  "recenter when only one unit is available" falls out of one function rather than being handled at
  each call site.
- `feature/ads/model` carries **no** feature or UI dependencies, so the three list surfaces can
  depend on it without pulling in an ad SDK; only `feature/ads/viewing` touches the SDK (N4).
- Unit tests cover: every row of §6.6; grouped/toggled counter behavior; the session cap (reset
  boundaries, two-up consuming two, headroom-of-one falling back to centered); and `AdSlotFormat`
  per layout tier.
