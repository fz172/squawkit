# PRD: Notifications

**Status:** 📋 Proposed — not implemented
**Last updated:** 2026-08-17
**Areas:** `feature/notifications` (new) · `feature/sharing` · `feature/tasks` · `feature/squawk` ·
`feature/sync` · `core/appinfo` · `backend/firebase/functions`
**Related docs:** [aircraft_sharing_PRD.html](../sharing/aircraft_sharing_PRD.html) ·
[aircraft_sharing_design.html](../sharing/aircraft_sharing_design.html) ·
[user_squawking_prd.md](../squawks/user_squawking_prd.md) ·
[squawk_design.md](../squawks/squawk_design.md) ·
[storage_r1_design.md](../storage/storage_r1_design.md) ·
[subscription_PRD.html](../subscription/subscription_PRD.html)

---

## 1. Problem & Background

SquawkIt is now a **multi-user** app. Aircraft sharing shipped: an owner invites a technician or a
co-owner, and from then on several people write to the same aircraft's tasks, squawks, and
maintenance logs, which physically live under the host's tree
(`users/{hostUid}/aircraft/{acId}/…`) and reach every member through the sync engine. Squawks carry
a priority ladder up to **AOG**. Maintenance tasks carry a computed **due status** that walks from
Normal → Due Soon → Overdue as the calendar advances and engine hours accumulate.

Every one of those state changes is silent. Today the app tells a pilot something changed only if
the pilot happens to open the app and look at the right screen:

- **A technician's work is invisible to the owner.** A mechanic signs a log entry, closes a squawk,
  or raises a squawk to AOG on a Tuesday afternoon; the owner finds out the next time they open the
  aircraft — which may be the morning of a flight, or never.
- **An owner's changes are invisible to the technician.** The owner adds a task card, edits the
  aircraft's hours, or files a new squawk before a scheduled visit; the shop has no signal to plan
  around it.
- **Urgency crosses a line with nobody watching.** An annual goes overdue at midnight. A 100-hour
  inspection goes overdue the moment a log entry pushes engine time past the interval. A squawk gets
  raised from Medium to AOG. These are precisely the transitions that change whether the aircraft is
  legal to fly, and they are exactly as quiet as everything else.

The result is a collaboration product with no out-of-app channel. Sharing gave several people write
access to one aircraft; notifications are what make that a *workflow* instead of a shared filing
cabinet the participants poll by hand.

There is currently **no notification infrastructure of any kind** in the codebase: no FCM/APNs
integration, no device-token registry, no notification preferences, no `POST_NOTIFICATIONS`
permission handling, no in-app inbox. This PRD defines the product from zero.

---

## 2. Goals & Non-Goals

### Goals

- **Collaboration awareness (N1).** When anyone changes an aircraft, squawk, task, or maintenance
  log on a **shared** aircraft, every *other* collaborator on that aircraft is notified.
- **Urgency escalation (N2).** When a record's urgency moves *up* the ladder — a squawk priority
  raised, a task crossing into Due Soon or Overdue — the people responsible for that aircraft are
  notified, whether the cause was someone's edit or simply the passage of time.
- **User control (N3).** A first-class **Notifications** settings screen where a pilot chooses which
  classes of notification they receive, and can mute a single aircraft — including honest handling of
  the OS-level permission state.
- **Never notify someone about their own action.** The actor is always excluded.
- **Quiet by construction, not by tuning.** Bursts coalesce; the same escalation never fires twice;
  a batch import does not produce twenty notifications.
- **Tap lands on the record.** Every notification deep-links to the aircraft/record it describes.
- **Free for everyone.** No new paywall — see §9.6.

### Non-Goals

- ✕ **Email or SMS delivery.** Push and the system tray only. (Export already owns outbound email;
  reusing it for notifications is a separate decision.)
- ✕ **A full in-app notification inbox / activity feed.** Notifications are transient in V1; the
  record itself is the durable state. An activity log per aircraft is a strong follow-up, not V1.
- ✕ **Chat, comments, or @mentions.** There is no messaging surface to notify about.
- ✕ **Quiet hours, digests, and per-notification snooze.** Deferred (§13) — V1 ships immediate
  delivery with coalescing.
- ✕ **Notifications for unshared aircraft collaboration.** With one collaborator there is no one to
  tell. Urgency escalation (N2) *does* apply to solo aircraft — see §6.2.
- ✕ **Re-litigating due-status or priority semantics.** This PRD consumes `DueStatus` and
  `SquawkPriority` as they exist; it does not change how they are computed.

---

## 3. Users & User Stories

| ID | Role | Story |
|:---|:---|:---|
| US.1 | Owner | As an owner, I want to know when my mechanic signs a log entry on my aircraft, so I learn about completed work without asking. |
| US.2 | Owner | As an owner, I want to be told the moment a squawk on my aircraft is raised to AOG, because that decides whether I fly tomorrow. |
| US.3 | Owner | As an owner, I want to know when an inspection goes overdue, even if nobody touched the app that day. |
| US.4 | Technician | As a technician, I want to know when an owner files a new squawk on an aircraft I maintain, so I can plan the visit. |
| US.5 | Technician | As a technician working several aircraft, I want to mute the aircraft I am not currently responsible for, without turning notifications off entirely. |
| US.6 | Any user | As a user, I want to choose which kinds of notifications I get, so the app stays useful instead of noisy. |
| US.7 | Any user | As a user, I want tapping a notification to open exactly the record it is about. |
| US.8 | Any user | As a user who denied the OS permission, I want the app to tell me plainly that notifications are off and how to turn them on. |

---

## 4. Notification Taxonomy

Two classes ship in V1. They differ in *why* they fire, and that difference is what the settings UI
exposes.

| Class | Fires because | Audience | Default | Priority |
|:---|:---|:---|:---|:---|
| **N1 — Collaboration activity** | Someone *else* created, edited, or deleted a record on a shared aircraft | Every other collaborator on that aircraft | On | Normal |
| **N2 — Urgency escalation** | A record's urgency moved *up* the ladder — from any cause, including time | Everyone responsible for the aircraft (all collaborators; the owner alone on a solo aircraft) | On | High (AOG / Overdue), Normal (others) |

The classes overlap on purpose. A mechanic raising a squawk from Medium to AOG produces **one**
notification, not two: N2 wins and N1 is suppressed for that record in that window (§7.3). N2 is the
more urgent framing of the same fact, and the recipient should get the framing that matters.

**Preconditions for any notification.** A user receives notifications only when they are
**signed in with a real (non-anonymous) account**, have **cloud sync enabled**, and have granted the
**OS notification permission**. Anonymous and sync-off users are fully local by design — nothing
about their data reaches the server, so nothing can be fanned out. The settings screen states this
plainly rather than showing dead toggles (§8.4).

---

## 5. N1 — Collaboration Activity

### 5.1 What counts as an update

Every record type a collaborator can write:

| Record | Notifiable events |
|:---|:---|
| **Aircraft** | Aircraft details edited (tail number, model, times, component serials) |
| **Squawk** | Created · edited · priority changed · addressed by a log · dismissed · reopened · deleted |
| **Maintenance task** | Created · edited · force-complied · deleted |
| **Maintenance log** | Created · edited · deleted |

Deletions are included: a removed log entry or task card is a material change to a shared record
set, and the person who *didn't* delete it needs to know.

### 5.2 Audience

All members of the aircraft's share — the hosting owner and every technician/co-owner — **minus the
actor**. Membership is the ACL's `memberRoles` on
`aircraft_shares/{hostUid}/aircraft/{acId}`; the actor is the record's `writer_uid`, which security
rules already force to equal the writing caller, so it cannot be spoofed.

Role does **not** filter the audience in V1: an owner and a technician both see all activity on an
aircraft they share. (Role-scoped notification profiles — "technicians only care about squawks" — is
a V2 idea listed in §13.)

**Unshared aircraft produce no N1 notifications at all.** The only writer is the user themselves.

### 5.3 Content

Notifications name the aircraft, the change, and who made it:

```
Tasks · N4589T
Dave Chen added a task: Annual Inspection
```
```
Logbook · N4589T
Dave Chen signed a log entry: 100-hour inspection — Engine
```
```
Squawks · N4589T
Sarah Patel dismissed “Nose gear shimmy” (duplicate)
```

Rules for the body text:

- **Aircraft identity first** — a technician with six aircraft needs the tail number before anything
  else.
- **Actor by display name**, from the share member doc (`displayName`, already published for the
  technician picker). Falls back to "A collaborator" when no name is published.
- **Record title verbatim** — never a paraphrase. If a squawk is titled "Left brake dragging," that
  is what the notification says.
- **No numbers a recipient could misread as authoritative** — no computed hours or due dates in N1
  bodies. Those belong to N2, where the crossing is the point.

### 5.4 Coalescing

Editing five task cards in a row must not produce five notifications. N1 notifications coalesce per
**(recipient, aircraft, record type, actor)** over a short window (**target: 5 minutes**):

```
Tasks · N4589T
Dave Chen made 5 changes to tasks
```

Coalescing collapses the *notification*, never the data — each individual change is already in the
synced record. A coalesced notification deep-links to the record type's list for that aircraft
rather than to a single record.

---

## 6. N2 — Urgency Escalation

### 6.1 The urgency ladders

Two independent ladders. Notify on **any upward step**; stay silent on downward steps and on
sideways changes.

**Squawk priority** (`SquawkPriority`, already ordered in the proto):

| Rank | Value |
|:---|:---|
| 1 | `LOW` |
| 2 | `MEDIUM` |
| 3 | `HIGH` |
| 4 | `AOG` |

Any increase in rank escalates. `AOG` is the top of the ladder and always delivers at high priority.
A **reopened** squawk (Addressed/Dismissed → Open) is treated as an escalation from "resolved" at its
stored priority — it regressed to an open defect, and that is exactly the case a silent app hides.

**Task due status** (`DueStatus`):

| Rank | Value |
|:---|:---|
| 0 | `COMPLIED`, `NORMAL` |
| 1 | `DUE_SOON` |
| 2 | `OVERDUE` |

> ⚠️ **`DueStatus` ordinal order is not urgency order.** The enum declares
> `NORMAL, DUE_SOON, OVERDUE, COMPLIED`, so `COMPLIED` has the *highest* ordinal and the lowest
> urgency. The implementation must map to an explicit urgency rank; comparing ordinals would treat
> completing an inspection as the most urgent event in the app.

Escalations that must fire (`NORMAL → DUE_SOON`, `NORMAL → OVERDUE`, `DUE_SOON → OVERDUE`) and
transitions that must stay silent (`OVERDUE → COMPLIED`, `DUE_SOON → NORMAL`, `AOG → HIGH`,
priority unchanged). A de-escalation is good news and belongs in N1 activity, not in an urgency
alert.

### 6.2 Audience

All collaborators on a shared aircraft, **including the actor** when the escalation was caused by
something other than a direct edit to that record. Two cases matter:

- **Direct edit** — a mechanic raises a squawk to AOG. The mechanic knows; the *others* are notified.
- **Indirect crossing** — a mechanic logs 4.2 hours, which pushes three task cards past their
  100-hour interval. The mechanic did not decide that and is very likely unaware of it; **they are
  notified too**, alongside everyone else.

**N2 applies to solo aircraft.** A pilot with one aircraft and nobody to share it with still needs
to know their annual went overdue. This is the single most valuable notification in the app for a
single-user account, and scoping it to shares would waste it.

### 6.3 Time-driven vs. write-driven crossings

An escalation has two possible causes, and the product needs both:

| Cause | Example | Detectable at |
|:---|:---|:---|
| **Write-driven** | Priority raised; hours logged pushing a task overdue; a force-due date edited | The moment the record syncs |
| **Time-driven** | A due date passes at midnight with nobody touching the app | Only by evaluating time |

Time-driven crossings are the harder half and cannot be dropped: "your annual is overdue" is a
notification whose whole value is that it arrives *without* anyone opening the app. §9.3 covers how
this is detected, and the pieces already in the codebase that make it tractable.

**Delivery timing for time-driven crossings:** batched and delivered once per day at a
**civilized local hour (target: 08:00 in the recipient's timezone)**, not at the midnight instant of
the crossing. Nobody needs to be woken up because a calendar page turned.

### 6.4 Content

N2 bodies lead with the state, and *do* carry the number, because the number is the point:

```
⚠ Overdue · N4589T
Annual Inspection is overdue — was due Aug 12
```
```
⚠ AOG · N4589T
Sarah Patel raised “Left brake dragging” to AOG — aircraft grounded
```
```
Due soon · N4589T
100-Hour Inspection due in 8.2 hours (engine 1,241.8)
```

Visual language follows the existing status tiers in `core:ui` (`StatusTier.CRITICAL` /
`CAUTION`) so an overdue push reads like the overdue badge the user already knows — see
[DESIGN.md](../../DESIGN.md).

### 6.5 Exactly-once per crossing

An escalation notification must never repeat for the same crossing. Each escalation carries an
**idempotency key** of `(aircraftId, recordId, newUrgencyRank, crossingCause)`; a key already
delivered is dropped. A task that goes overdue does not re-notify daily while it stays overdue. It
notifies again only if it de-escalates (complied) and later crosses upward again.

---

## 7. Cross-cutting Delivery Rules

### 7.1 Never notify the actor about their own edit

Enforced from `writer_uid` on the synced envelope, not from anything client-supplied. The one
deliberate exception is the indirect N2 crossing in §6.2.

### 7.2 Multi-device

A user signed in on phone, tablet, and web gets the notification on **every** registered device with
notifications enabled. Read/dismiss state is **not** synchronized across devices in V1 (that is an
inbox feature — §13).

### 7.3 N1/N2 mutual exclusion

When a single write produces both an N1 activity event and an N2 escalation for the same record and
recipient, only N2 is delivered.

### 7.4 Ordering and lateness

Notifications are best-effort and may arrive out of order or late (a device offline for a day gets
what FCM retained). The **record is always the source of truth** — a notification body is a snapshot
of the moment it was generated, and tapping through shows current state. Bodies are therefore written
to survive being stale ("raised to AOG", not "is currently AOG").

### 7.5 Platform matrix

| Platform | V1 | Notes |
|:---|:---|:---|
| **Android** | ✅ Push (FCM) | `POST_NOTIFICATIONS` runtime permission on API 33+; notification channels per class |
| **iOS** | ✅ Push (APNs via FCM) | `UNUserNotificationCenter` authorization prompt |
| **Web** | ⏳ V1.1 | Browser push needs a service worker and a separate permission flow; the settings UI ships on web from V1 and states that delivery is not yet available there |

New `AppCapability` field: **`isPushSupported`** — the build/platform gate, per the three-mechanism
gating rule in [AGENTS.md](../../AGENTS.md#gating-three-mechanisms-kept-separate). Web's actual
returns `false` until V1.1, and the settings screen reads the same flag, so one runtime value
governs both the plumbing and the UI copy.

---

## 8. Settings UI

### 8.1 Entry point

A new **Notifications** row in the existing Settings screen (`feature/settings/SettingsScreen.kt`),
sitting with the other account-level rows (Backup & Sync, Subscription, Technicians). It opens a
dedicated screen following the `feature/sync/settings` precedent — a feature-owned settings module
with its own ViewModel and Koin module, reached through the shared nav graph in `feature/shell`.

Subtitle text reflects live state: *"Collaboration and urgency alerts"* when on,
*"Off — turn on to hear about changes"* when the master toggle is off, *"Blocked in system
settings"* when the OS permission is denied.

### 8.2 Screen structure

```
Notifications
─────────────────────────────────────────────
  [!] Notifications are blocked in system settings
      SquawkIt can’t send notifications until you
      allow them for this app.        [ Open settings ]

  Notifications                            [ ● ]
  Master switch for everything below.

  COLLABORATION
  Someone changes a shared aircraft
  ─────────────────────────────────────────
  Aircraft details                         [ ● ]
  Squawks                                  [ ● ]
  Maintenance tasks                        [ ● ]
  Maintenance logs                         [ ● ]

  URGENCY
  A squawk or inspection becomes more urgent
  ─────────────────────────────────────────
  Aircraft grounded (AOG)                  [ ● ]
  Squawk priority raised                   [ ● ]
  Inspection overdue                       [ ● ]
  Inspection due soon                      [ ● ]

  PER-AIRCRAFT
  ─────────────────────────────────────────
  Muted aircraft                    2 muted  ›

  ─────────────────────────────────────────
  Notifications need cloud sync and a signed-in
  account. [Sign in]  /  [Turn on sync]
```

### 8.3 Settings table

| Setting | Scope | Default | Behavior |
|:---|:---|:---|:---|
| **Notifications** (master) | Account | On | Off silences everything, server-side. Every group below is disabled and dimmed while off. |
| Aircraft details | Account | **On** | N1 for the `Aircraft` record |
| Squawks | Account | **On** | N1 for squawk create/edit/dismiss/reopen/delete |
| Maintenance tasks | Account | **On** | N1 for task create/edit/delete |
| Maintenance logs | Account | **On** | N1 for log create/edit/delete |
| Aircraft grounded (AOG) | Account | **On** | Any escalation to `AOG`. High priority. Turning it off shows a confirm: *"You won't be told when an aircraft is grounded."* |
| Squawk priority raised | Account | **On** | Escalations below AOG, and reopened squawks |
| Inspection overdue | Account | **On** | `→ OVERDUE`. High priority. |
| Inspection due soon | Account | **On** | `→ DUE_SOON` |
| Muted aircraft | Per aircraft | Unmuted | Silences *all* classes for one aircraft. Also settable from the aircraft screen's overflow menu, which is where a technician actually is when they decide to mute. |

**Everything defaults on.** A collaboration feature nobody knows exists is worse than one that is
briefly chatty, and the mute controls are one tap away. The coalescing rules in §5.4 are what keep
"all on" tolerable — a fleet manager's day should be a handful of notifications, not a hundred.

### 8.4 Permission and precondition states

The screen must be honest about *why* nothing is arriving. Four states:

| State | UI |
|:---|:---|
| **Permission not yet requested** | Toggles active; flipping the master on triggers the OS prompt inline |
| **Permission denied** | Persistent banner + **Open settings** deep link to the OS app-settings page. Toggles stay editable so the user's choices survive fixing the permission. |
| **Signed out or anonymous** | Footer explains the requirement with a **Sign in** action |
| **Cloud sync off** | Footer explains the requirement with a **Turn on sync** action linking to Backup & Sync |

**Never ask for the notification permission at cold start.** Request it in context: when the user
turns the master toggle on, or right after they create or accept a share invite — the moment when
"someone else can now change your aircraft" makes the ask self-explanatory.

### 8.5 Developer Options

Developer builds get **Send test notification** entries (one per class) in Developer Options, gated
on `AppCapability.isDeveloperOptionsSupported` like the rest of that screen. Verifying delivery
should not require a second account and a real AOG squawk.

---

## 9. Architecture Implications

Product-level shape only. The full design — paths, protos, function signatures, rules — belongs in a
companion design doc (`notifications_design.md`).

### 9.1 What the existing codebase already provides

Four properties of the current architecture make this feasible without new fundamentals:

1. **The server can read entity payloads.** `payload` is opaque proto bytes, but the Cloud Functions
   build generates the same protos (`src/generated/proto/`) and `onRecordDeleted` /
   `blobRefs.ts` already decode `MaintenanceLog`, `MaintenanceTask`, and `Squawk` payloads. Notification
   triggers are the same move: decode, compare, fan out.
2. **The audience is already a single document read.** `memberRoles` is denormalized onto the share
   ACL root precisely so an authorization check is one `get()`. Fan-out uses the same read.
3. **The actor is already unforgeable.** `writer_uid` on the envelope is rules-enforced to equal the
   caller, so actor suppression needs no new trust.
4. **Current hours are already summarized.** `MaintenanceOverview` carries
   `current_airframe_time` / `current_engine_time`, so evaluating an hours-based crossing does not
   require scanning an aircraft's whole log history.

### 9.2 New pieces

| Piece | Shape | Why |
|:---|:---|:---|
| **Device token registry** | Plain-field docs at `users/{uid}/push_devices/{installationId}` — token, platform, app version, per-device enabled flag, `updatedAt` | The server must read tokens to send. Same rationale as the sharing ACL exception: server-readable fields cannot ride the opaque entity path. |
| **Notification preferences** | New synced entity + `CollectionKind` + proto (`settings/notification_settings.proto`) | Preferences are user data: local-first, offline-editable, synced across devices like every other setting. Adding a `CollectionKind` is a zero-migration change (the `collection` column is `TEXT`). The server decodes the payload when fanning out (§9.1.1). |
| **Fan-out functions** | Firestore triggers on the entity paths + a scheduled sweep | Where N1 and N2 are actually computed |
| **`feature/notifications`** | Canonical module set (`model` / `datamanager` / `sharedassets` / `settings`) | Token registration, permission state, preference reads/writes, tap-routing |
| **Platform messaging actuals** | `androidMain` FCM SDK · `iosMain` Swift bridge (the `platformAdConsentModule` precedent) · `jsMain` no-op until V1.1 | GitLive's KMP Firebase wrapper does not cover messaging |

### 9.3 Detecting escalations — the one real design decision

Write-driven escalation is straightforward: a Firestore trigger sees before/after, decodes both,
compares urgency rank. **Time-driven escalation is the hard part**, because due status is computed
*client-side* today (`TaskDueManagerImpl`) from the rule set, the last complying log, and the current
clock — and no write happens when a due date simply passes.

Two options, with a recommendation:

- **Option A — replicate the due-rule engine server-side.** Faithful, but it duplicates
  `TimeRule` / `EngineHourRule` / `LinkedRule` / force-complied handling and last-compliance
  resolution in TypeScript. Two implementations of an airworthiness calculation that must agree
  forever is a liability, and a drift between them is a wrong answer about legality.
- **Option B (recommended) — a published urgency digest.** The client already computes
  `DueMetadata` for display. Have it publish a compact plain-field digest per aircraft carrying, per
  task, `{ nextDueDate, nextDueEngine, urgencyRank }` and, per squawk, `{ priority, state }`. Then:
  - **Write-driven** crossings are a diff of two digest revisions — no rule engine on the server.
  - **Time-driven** crossings need no rule engine either: the digest already contains
    `nextDueDate`, so a scheduled sweep asking *"is today past `nextDueDate` for a task whose last
    published rank was below Overdue?"* is a date comparison.

  Cost of Option B: the digest can go stale (a task edited offline is not evaluated until the device
  syncs), and the design must settle who publishes it when several collaborators are online, plus
  write contention on a single doc. All three are answerable; none requires a second airworthiness
  engine.

Timezone: due dates are `LocalDate` values computed in the device timezone, so the sweep needs a
timezone to compare against. Recommended: the digest carries the publishing device's zone, the sweep
runs hourly, and each recipient's crossings deliver at their local 08:00 (§6.3).

### 9.4 Fan-out cost

One trigger invocation per synced write on a shared aircraft, plus one ACL read, plus a token read
per recipient, plus an FCM send per device. Small at current scale; the design doc should still set
a per-aircraft rate ceiling so a fake-data generator run or a bulk import cannot turn into thousands
of sends. `feature/stresstest` is compiled into every build and *will* be pointed at a shared
aircraft.

### 9.5 Privacy & security

- Notification bodies carry **user content** (tail numbers, squawk titles, names) to Apple/Google
  push services. This is a new trust boundary — Firestore content stays within Firebase today. It
  should be stated in the privacy policy, and it is the reason bodies stay short and carry no
  attachments or certificate data.
- **Fan-out must re-derive the audience from the ACL at send time**, never from a cached list. A
  revoked member must stop receiving notifications immediately — the `SharedScopeJanitor` prunes
  their local data on revocation, and notifications must not outlive that.
- Device tokens are per-install and **deleted on sign-out** and on account deletion
  (`deleteMyAccount` gains this cleanup). A stale token on a shared device is a leak.
- Rules for `push_devices`: a user reads and writes only their own; no client reads another user's
  tokens.

### 9.6 Gating

Per the three-mechanism rule — build/platform capability, entitlement, developer override — this
feature touches exactly two:

- **`AppCapability.isPushSupported`** — platform support (§7.5).
- **Developer Options** — test-send actions (§8.5).
- **No `SubscriptionManager` gate.** Hosting a share is Pro, but *accepting* an invite never is, so
  paywalling notifications would leave an invited technician unable to hear about the aircraft they
  were invited to maintain. And N2 on a solo aircraft is a safety notification. Notifications follow
  the cloud-sync precedent: part of the app's dependability promise, free for everyone. Digests and
  quiet hours are the plausible Pro surface later, not the core alerts.

---

## 10. Rollout

| Phase | Contents | Exit criteria |
|:---|:---|:---|
| **P1** | Preferences entity + settings UI (all platforms) + device-token registry + Android FCM plumbing; test-send in Developer Options | A dev build can register a token and receive a test notification; settings persist and sync |
| **P2** | N1 collaboration fan-out on Android; coalescing; actor suppression; deep links | Two accounts sharing an aircraft see each other's changes; neither sees their own |
| **P3** | iOS delivery (APNs) | Parity with Android on N1 |
| **P4** | N2 escalation: urgency digest, write-driven diff, scheduled sweep, idempotency | Write-driven and time-driven crossings both fire exactly once; de-escalations stay silent |
| **P5** | Per-aircraft mute; web push (V1.1) | Mute honored server-side; web reaches parity and `isPushSupported` flips true on `jsMain` |

Dogfood on the team's own shared aircraft between P2 and P4 — the noise floor is the thing that has
to be felt rather than reasoned about, and it is what the coalescing window should be tuned against
before GA.

---

## 11. Success Metrics

| Metric | Target |
|:---|:---|
| Shares with ≥1 notification class enabled 30 days after joining | > 80% |
| Master-toggle opt-out rate | < 10% |
| Per-class opt-out rate (any single class) | < 25% |
| Notification tap-through rate | > 15% |
| Median time from AOG escalation to the owner opening the aircraft | < 2 hours (from "next app open", unbounded today) |
| Duplicate/self notifications reported | 0 |
| Overdue-inspection notifications delivered without an app open | Measurable and non-zero — this is the whole point of the time-driven sweep |

Instrument via the existing analytics plan ([analytics_design.html](../analytics/analytics_design.html),
proposed) rather than inventing a parallel event pipeline.

---

## 12. Risks

| Risk | Mitigation |
|:---|:---|
| **Noise drives a global opt-out.** One chatty week and the master toggle goes off forever. | Coalescing (§5.4); per-class and per-aircraft granularity; dogfood tuning before GA; exactly-once escalations |
| **Two due-status implementations disagree.** A server-side rule engine drifting from the client's is a wrong answer about airworthiness. | Option B (§9.3) — no second engine |
| **Stale digest misses a crossing.** No device syncs, no evaluation. | Sweep evaluates published `nextDueDate` values, so a stale digest still yields correct *date* crossings; publish on foreground and after any relevant write |
| **Notification says something the recipient can no longer see.** Revocation racing a send. | Audience re-derived from the ACL at send time; tap-through degrades to "no longer available" |
| **Fan-out storm from bulk writes.** Stress-test generator against a shared aircraft. | Per-aircraft rate ceiling; coalescing before send, not after |
| **Permission asked at the wrong moment and denied forever.** | Contextual request only (§8.4); never at cold start |
| **iOS APNs operational burden** (certificates, entitlements, background modes). | Sequenced as its own phase (P3) rather than bundled with the product logic |

---

## 13. Out of Scope (V1)

Listed because each is a plausible next step, not because it was overlooked:

- **In-app notification inbox / per-aircraft activity feed** with cross-device read state — the
  natural V2, and the durable counterpart to transient push.
- **Quiet hours** and **daily/weekly digests**, with AOG and Overdue bypassing both.
- **Role-scoped profiles** — e.g. technicians default to squawks and tasks only.
- **Email notifications** (the export mailer exists; whether to reuse it is a separate decision).
- **Per-record follow/unfollow** ("notify me about this squawk only").
- **Reminders that are not escalations** — "annual due in 30 days," recurring nags while overdue.
- **Notification actions** — dismiss a squawk or acknowledge from the tray.
- **Web push** (V1.1, §7.5).

---

## 14. Open Questions

| # | Question | Recommendation |
|:---|:---|:---|
| Q1 | Escalation detection: server-side rule engine or published urgency digest? | **Digest (Option B, §9.3)** — no second airworthiness engine |
| Q2 | Are preferences account-level or per-device? | **Account-level**, synced, with a per-device enabled flag on the token doc so one device can be silenced without changing the account's choices |
| Q3 | Coalescing window length? | Start at **5 minutes**, tune during dogfood (P2→P4) |
| Q4 | Local delivery hour for time-driven crossings? | **08:00** in the recipient's timezone |
| Q5 | Should AOG be un-mutable? | **No** — mutable, but with a confirmation dialog. A user who cannot silence an alert silences the whole app instead. |
| Q6 | Do deletions notify? | **Yes** (§5.1) — a removed log entry is material |
| Q7 | Does an owner get N1 for their *own* aircraft edits on a solo aircraft? | **No** — never notify the actor; and with no collaborators there is no audience |
| Q8 | Notification channel granularity on Android? | **One channel per class** (Collaboration, Urgency, Grounded) so OS-level controls mirror the in-app settings |
