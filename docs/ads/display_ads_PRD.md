# PRD: Display Ads (Free Tier)

> **Implementation status.** **Proposed — nothing has shipped.** No `feature/ads` module exists yet, no ad SDK
> is integrated, and the in-app comparison table
> ([`ProPaywallContent.kt`](../../feature/subscription/viewing/src/commonMain/kotlin/dev/fanfly/wingslog/feature/subscription/viewing/ProPaywallContent.kt))
> does not yet carry the Ad-free row specified in §9. The comparison table in
> [`subscription_PRD.html`](../subscription/subscription_PRD.html) §3 has been updated to match this PRD;
> the app-side row ships with the feature, not before it (advertising "ad-free" while no one sees ads is a lie
> in the other direction).

**Owner:** Product · **Status:** Proposed · **Date:** 2026-07-28
**Related:** [Subscription PRD](../subscription/subscription_PRD.html) · [Subscription Design](../subscription/subscription_design.html) · [Product overview](../product/PRD.md)

---

## 1. Summary

Introduce **display ads on the free tier only**, rendered as ordinary cards interleaved into the three
record lists a pilot actually lives in — **squawks**, **maintenance tasks**, and **maintenance logs** —
at a cadence of **one ad every 10 items**, with a single trailing ad for lists shorter than 10 items and
**no ad at all when a list is empty**. SquawkIt Pro remains completely ad-free, and "Ad-free" becomes a
line in the tier comparison table, giving the paywall a second, continuously visible argument.

Ads are a **revenue floor for users who will never subscribe**, not a replacement for the subscription.
Every design decision below resolves in favor of the record, not the ad: ads never appear above
safety-critical status, never interrupt a flow, never occupy an empty state, and never take a full screen.

---

## 2. Problem & Background

The [Subscription PRD](../subscription/subscription_PRD.html) commits to a free tier that is *deliberately
generous*: a complete single-aircraft logbook with cloud backup and multi-device sync free for every
signed-in account. That is the right product call and it is also a permanent, uncapped cost — every free
account consumes Firestore reads/writes, sync bandwidth, and Cloud Functions time indefinitely.

Today that cost has exactly one offset: conversion to Pro. Most free users in a consumer utility app never
convert, and for that majority the app is pure expense. Display ads monetize the non-converting free
population at a per-user rate that is small but non-zero and, critically, **scales with engagement rather
than with intent to pay**.

There is a second effect worth stating plainly: an ad in the list is a standing, honest reminder that a paid
tier exists. We expect ads to *raise* free→Pro conversion, not depress it — and §11 makes conversion a
tracked guardrail rather than an assumption.

---

## 3. Goals

- Monetize the free tier with **in-list display ads** on squawks, maintenance tasks, and maintenance logs.
- Keep the free tier's *function* fully intact — ads add a card, they never remove or gate a capability.
- Make **"Ad-free"** a first-class line of the Pro value proposition, in the docs table and in the app.
- Ship behind a **build-time capability gate** so the feature can land dark and be enabled per platform.
- Enforce ad-free for Pro **reactively** — the moment entitlement resolves to Pro, ads disappear without a
  restart; on downgrade at period end they return.
- Respect consent (GDPR/UMP, iOS ATT) and never expose aircraft or maintenance data to an ad network.

## 4. Non-Goals

- **Ads on any paid tier.** Pro is ad-free, permanently and without qualification.
- **Interstitials, pop-ups, rewarded ads, video, or app-open ads.** In-list display cards only.
- **Ads on the web target** in v1 (see §7 — the Kotlin/JS host gets a no-op ad slot).
- **Ads anywhere outside the three record lists** — no ads on dashboards, detail sheets, forms, wizards,
  pickers, settings, export flows, the technician list, search results, or the AOG / critical-alert sections.
- **Removing ads as a standalone one-time purchase.** Ad removal is a Pro benefit, not a separate SKU.
- **Using logbook content for targeting.** Contextual/network targeting only; see §10.
- **Server-side ad mediation or a house-ad system.** v1 uses a single network (§12 open question).

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

---

## 6. Placement Specification

This is the normative part of the PRD. Implementations must match it exactly; §6.6 is the test matrix.

### 6.1 Surfaces in scope

| Surface | Where | Layout today |
|---|---|---|
| **Squawks list** | `SquawkTab` (aircraft dashboard) | `AdaptiveCardList` of `SquawkCard`, separate Open / Closed sub-views |
| **Maintenance tasks list** | `MaintenanceTasksTab` → `ComplianceSection` | `AdaptiveCardList` of `TaskCardItem`, grouped by status |
| **Maintenance logs list** | `MaintenanceLogListContent` | `LazyColumn` of log cards (compact); `MaintenanceLogTable` at MEDIUM+ |

No other surface renders an ad in v1.

### 6.2 Cadence

Let `n` be the number of **rendered** items in the list (i.e. after search and filters are applied), and
let the interval be **10**.

1. `n == 0` → **no ad.** The empty state stays clean.
2. `0 < n < 10` → **exactly one ad, as the last card**, after the final item.
3. `n >= 10` → an ad **after every 10th item** — after items 10, 20, 30, … — and **no trailing ad for the
   remainder**.

Rule 3 means `n == 10` produces one ad which is also the last card (rules 2 and 3 agree at the boundary),
and `n == 25` produces ads after items 10 and 20 — the trailing 5 items get nothing. The short-list rule
exists so that a small logbook still shows *an* ad; it is deliberately **not** applied to trailing
remainders, which would double ad density on long lists for no revenue reason worth the annoyance.

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

Callers wrap their list only when ads are enabled (§8); when disabled they render `items` directly, so the
ad-free path costs nothing.

**Stable keys.** `Ad(slotIndex)` is the Lazy list key for the slot. Slot identity must not change as items
load in, or Compose will tear down and re-request a filled ad.

**Index mapping.** Existing index-based behavior operates on the *item* list, never the display list. The
logs list scrolls to a target log by index (`scrollToLogId` pinning in `MaintenanceLogListContent`); with
ads inserted, that lookup must map entity id → **display** index. Getting this wrong scrolls a pilot to the
wrong log, so it is called out as a functional requirement (F8), not a detail.

### 6.4 Empty, error, and loading states

No ad is rendered when the list is empty for **any** reason: a brand-new aircraft with no records, a search
or filter that matches nothing, a load error, or the loading state before first emission. `EmptyState` is
never accompanied by an ad card. Requirement 4 of the brief, stated as an invariant: **the ad count is a
function of the rendered item count, and `f(0) = 0`.**

### 6.5 Grouped, toggled, and wide layouts

- **Grouped lists** (Compliance's status groups): the counter runs **continuously** across the groups in a
  single scroll, and `n` is the total across groups. A slot that lands on a group boundary renders at the
  **end of the preceding group** — never between a header and its first card (G1).
- **Toggled sub-views** (squawks Open ↔ Closed): each sub-view is its own list with its own counter and its
  own `n`. Switching the toggle re-evaluates from scratch.
- **Multi-column tiers** (`AdaptiveCardList` with `columns > 1`): the ad occupies a **full-width row
  spanning all columns**, inserted after the row containing the 10th item. Cadence stays per-item, not
  per-row — a 3-column tier still gets one ad per 10 records.
- **Wide log table** (`MaintenanceLogTable`, MEDIUM+): the ad renders as a **full-width band between table
  rows**, matching the table's horizontal insets, after every 10th row. If the band cannot be made to look
  deliberate in the table, the acceptable fallback is **no ads in the table view** — never a cramped ad
  jammed into a data cell.

### 6.6 Worked examples (test matrix)

| Rendered items `n` | Ads | Ad positions (after item #) |
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

---

## 7. The Ad Card

- **Shape and rhythm:** same corner radius, horizontal insets, and inter-card spacing as the record cards
  around it, using existing `Spacing` tokens — the ad sits *in* the list, it does not float over it.
- **Label:** a small "Sponsored" label in `onSurfaceVariant`, top-left of the card, always present (G6).
- **Surface:** neutral `surfaceVariant`/`outlineVariant` treatment, no status colors (G8), no dynamic color
  (per `DESIGN.md`).
- **Upgrade affordance:** a low-emphasis footer link — **"Remove ads with SquawkIt Pro"** — navigating to
  the subscription screen. This is the ad card's only in-app tap target besides the ad creative itself, and
  it is what makes the ad an argument for Pro rather than only a nuisance.
- **Format:** an inline adaptive display unit, height driven by the network's adaptive sizing, capped so a
  single ad never exceeds **~30% of the viewport height** on a phone.
- **Unfilled:** zero height, no label, no footer — the row collapses entirely (G5).

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

1. **Free only.** `showsAds()` is `true` only while the effective `Subscription.Status` is `FREE`. Any paid
   tier — now or in future — is ad-free. Because the gate reads the status enum rather than a boolean, a
   future tier is ad-free automatically.
2. **Default-OFF, the mirror of the feature gates.** The existing gates are *default-open*: while
   `AppCapability.isSubscriptionSupported` is `false` there is no paywall and every premium capability reads
   available. Ads invert that: when subscriptions are not supported, or ads are not supported for this
   build/platform, `showsAds()` is `false`. A build with no way to buy Pro must never show ads a user
   cannot remove.
3. **Reactive.** It is a `Flow`, driven by the same entitlement stream as the rest of the gates: the moment
   a purchase resolves, in-flight ad slots disappear without a restart; on expiry, they return.

**New capability flag.** `AppCapability` gains `isAdsSupported`, following the staged-rollout pattern of
`isSubscriptionSupported` / `isAircraftSharingSupported`: a build-time gate, on in dev and dogfood, off in
the shipping release until GA. It is `false` on the Kotlin/JS host in v1. Developer Options gains a
**Force ads** toggle (developer builds only) so ad placement can be exercised without a real free account.

**Guests.** An anonymous/guest account is on the free tier and sees ads under the same rules. In practice a
new guest sees none for a while, because their lists are empty (§6.4) — the onboarding path is ad-free as a
consequence of the placement rules, not as a special case.

**Shared aircraft.** Ads follow the *viewer's* tier, not the host's. A free-tier mechanic viewing a Pro
owner's shared aircraft sees ads; the Pro owner does not.

---

## 9. Subscription Comparison Table (required update)

Ad-free becomes a listed Pro benefit. The canonical table in
[`subscription_PRD.html`](../subscription/subscription_PRD.html) §3 has been updated to:

| Capability | Free | SquawkIt Pro |
|---|---|---|
| Aircraft | 1 | Unlimited |
| Maintenance logs, tasks & squawks | ✓ | ✓ |
| Export to this device (CSV / XLSX / PDF / ZIP) | ✓ | ✓ |
| Photo & file attachments | — | ✓ |
| Email a copy of exports | — | ✓ |
| Cloud backup & multi-device sync | ✓ | ✓ |
| **Ad-free experience** | **—** | **✓** |
| Share aircraft & invite others | — | ✓ |
| Future premium features | — | ✓ |

Row order mirrors the shipped HTML table; the new row is inserted after *Cloud backup & multi-device sync*
and before *Share aircraft & invite others*. Mid-table rather than appended, deliberately: "Future premium
features" is the table's closing catch-all line, and a concrete, shipped benefit should not sit below it.

**In-app parity (ships with the feature, not before).** The same row is added to the `ComparisonTable`
composable in `ProPaywallContent.kt`:

```kotlin
CompareRow(stringResource(Res.string.subscription_feature_ads), Cell.No, Cell.Yes)
```

with a new string `subscription_feature_ads` = **"Ad-free experience"**, inserted after
`subscription_feature_backup`. Free renders the em-dash `Cell.No`, Pro the check. The row must not appear in
a build where `isAdsSupported` is `false` — the comparison table has to describe the build the pilot is
holding.

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
| F6 | Ads are gated behind `AppCapability.isAdsSupported` (off in the shipping release until GA) and off entirely on the web host. |
| F7 | Each ad card is labeled "Sponsored" and carries a "Remove ads with SquawkIt Pro" link to the subscription screen. |
| F8 | Index-based list behaviors (notably logs jump-to-log / `scrollToLogId`) resolve against the **display** list so ads never shift a scroll target. |
| F9 | An unfilled or failed ad slot renders at zero height and never leaves a gap or placeholder. |
| F10 | Developer Options exposes a **Force ads** toggle in developer builds. |
| F11 | The subscription comparison table (doc **and** app) lists "Ad-free experience" as a Pro benefit (§9). |
| F12 | Ad load, impression, click, upsell-tap, and fill-failure are instrumented (§12). |

### Non-Functional

| ID | Requirement |
|---|---|
| N1 | Scroll performance: no measurable frame-time regression on lists of 200+ items with ads enabled versus disabled. |
| N2 | No layout shift: inserting a filled ad must not move items already on screen (fill happens off-screen or on first composition of the slot). |
| N3 | Offline: with no network the slot stays collapsed; no error UI, no retry storm. Ad failures are never surfaced to the user. |
| N4 | The ad SDK is not a dependency of any `model` or `datamanager` module — it lives behind an `expect`/`actual` UI seam in `feature/ads/viewing` (no-op `actual` on JS). |
| N5 | Memory: at most a small bounded number of ad views are alive per screen; slots scrolled far off-screen release their ad view. |
| N6 | No ad SDK initialization, network call, or consent prompt occurs for a Pro user or an ads-unsupported build (P6). |
| N7 | Accessibility: the ad card is a single focus group announced as "Advertisement"; the upgrade link is separately focusable and labeled. |

---

## 12. Analytics & Success Metrics

Events, via the existing `AnalyticsManager.logEvent`:

| Event | Params |
|---|---|
| `ad_slot_filled` | `surface` (squawks/tasks/logs), `slot_index` |
| `ad_impression` | `surface`, `slot_index` |
| `ad_click` | `surface`, `slot_index` |
| `ad_fill_failed` | `surface`, `reason` |
| `ad_upsell_tapped` | `surface` — the "Remove ads with SquawkIt Pro" link |

**Primary:** ad revenue per free DAU; fill rate; impressions per free session.
**Secondary:** `ad_upsell_tapped` → subscribe conversion — the ad card as a paywall entry point.
**Guardrails (a regression here overrides revenue):**
- Free→Pro conversion must not fall; the hypothesis is that it rises.
- Free-tier D7/D30 retention and sessions/user must not drop more than **2%** relative.
- Crash-free sessions and list scroll jank must not regress (N1).
- Store rating and "ads" complaint volume in reviews are watched for the first two releases.

---

## 13. Rollout

1. **Dark landing.** `feature/ads` merges with `isAdsSupported = isDeveloperBuild`; the shipping release is
   unaffected.
2. **Dogfood.** Placement, density, and the empty-state rule are validated on real logbooks; the comparison
   table row appears in dogfood builds only.
3. **GA gating.** Ads GA **cannot precede subscription GA** — `isSubscriptionSupported` must be `true`
   first, or free users would see ads with no way to remove them (§8, property 2).
4. **Staged enable** per platform: Android first, iOS after ATT flow validation, web not at all in v1.
5. **Kill switch.** There is no remote config in the codebase today; the v1 kill switch is a build-time flag
   flip plus an expedited release. If that proves too slow, a remote-config-backed gate is the first
   follow-up (§14).

---

## 14. Open Questions

| # | Question | Owner |
|---|---|---|
| Q1 | Ad network: AdMob direct vs. a mediation layer. AdMob is the default assumption for a KMP app targeting Android + iOS. | Eng/Product |
| Q2 | Exact unit format and max height per layout tier (adaptive inline banner vs. medium rectangle). | Design |
| Q3 | Does the wide log **table** get full-width ad bands, or is it ads-free (§6.5 fallback)? | Design |
| Q4 | Web ads (AdSense) — a separate v2 decision, since the Kotlin/JS host has no AdMob path. | Product |
| Q5 | Frequency capping across sessions — does a pilot who opens the squawks list 20 times a day see 20 fresh ads? | Product |
| Q6 | Does an ad-supported free tier change the case for raising the free aircraft limit above 1? | Product |
| Q7 | Remote kill switch: is a build-time flag acceptable for v1 (§13.5)? | Eng |

---

## 15. Implementation Sketch (non-binding)

Follows the canonical feature module pattern in [AGENTS.md](../../AGENTS.md):

```
feature/ads/
  model/          ListRow, withAdSlots(), AdSurface enum — pure Kotlin, unit-tested against §6.6
  datamanager/    AdsGate (wraps SubscriptionManager.showsAds() + AppCapability.isAdsSupported)
  viewing/        AdSlotCard (common) + expect/actual AdView
                    androidMain: Google Mobile Ads inline adaptive banner
                    iosMain:     GADBannerView + ATT/UMP
                    jsMain:      no-op (renders nothing)
```

- `SubscriptionManager` gains `showsAds()`; `AppCapability` gains `isAdsSupported`.
- Koin module registered in `core/di/CommonAppModules.kt`, per the module rules.
- `feature/ads/model` carries **no** feature or UI dependencies, so the three list surfaces can depend on it
  without pulling in an ad SDK; only `feature/ads/viewing` touches the SDK (N4).
- Unit tests for `withAdSlots` cover every row of §6.6 plus grouped/toggled counter behavior.
