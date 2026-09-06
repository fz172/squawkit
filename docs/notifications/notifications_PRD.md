# PRD: Notifications

**Status:** ✅ Shipped — 2026-08-19 → 08-26
**Last updated:** 2026-09-05

> **Implementation status.** Shipped in `feature/notifications` (ten submodules) across epics P0–P6
> (#454–#460, closed 2026-08-19 → 08-26): urgency escalation (N2) on Android and iOS, collaboration
> notifications (N1) with a backend fan-out on Android, iOS, web push (V1.1), and sync-driven in-tab
> alerts on the web, plus a per-category settings screen and developer-options plumbing. Two decisions
> made during the build: **every write sends its own N1 notification** — no session coalescing, throttle,
> or ceiling — and **AOG is not its own urgency tier**; a raise to High or AOG reports as `PRIORITY_RAISED`.
> Since Phase 2 of the multi-domain pivot, tier titles and bodies and the grounded channel's display name
> come from the Thing's lexicon (#661–#663). See [notifications_design.md](notifications_design.md) for
> the as-built detail.
**Areas:** `feature/notifications` (new) · `feature/login` · `feature/sharing` · `feature/tasks` ·
`feature/squawk` · `feature/sync` · `core/appinfo` · `backend/firebase/functions`
**Related docs:** [notifications_design.md](notifications_design.md) ·
[aircraft_sharing_PRD.html](../sharing/aircraft_sharing_PRD.html) ·
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
- **Urgency escalation (N2), computed entirely on-device.** When a record's urgency moves *up* the
  ladder — a squawk priority raised, a task crossing into Due Soon or Overdue — the user is
  notified,
  whether the cause was someone's edit or simply the passage of time. A **once-daily local scan**
  over data already on the device does the detection; no server, no network, works offline and
  signed-out.
- **User control (N3).** A first-class **Notifications** settings screen where a pilot chooses which
  classes of notification they receive — including honest handling of the OS-level permission state.
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
- ✕ **Quiet hours, digests, and per-notification snooze.** Deferred (§13) — V1 ships N1 immediately
  with coalescing, and N2 once a day.
- ✕ **Notifications for unshared aircraft collaboration.** With one collaborator there is no one to
  tell. Urgency escalation (N2) *does* apply to solo aircraft — see §6.2.
- ✕ **Re-litigating due-status or priority semantics.** This PRD consumes `DueStatus` and
  `SquawkPriority` as they exist; it does not change how they are computed.

---

## 3. Users & User Stories

| ID   | Role       | Story                                                                                                                                                 |
|:-----|:-----------|:------------------------------------------------------------------------------------------------------------------------------------------------------|
| US.1 | Owner      | As an owner, I want to know when my mechanic signs a log entry on my aircraft, so I learn about completed work without asking.                        |
| US.2 | Owner      | As an owner, I want to be told the moment a squawk on my aircraft is raised to AOG, because that decides whether I fly tomorrow.                      |
| US.3 | Owner      | As an owner, I want to know when an inspection goes overdue, even if nobody touched the app that day.                                                 |
| US.4 | Technician | As a technician, I want to know when an owner files a new squawk on an aircraft I maintain, so I can plan the visit.                                  |
| US.5 | Any user   | As a user, I want to choose which kinds of notifications I get, so the app stays useful instead of noisy.                                             |
| US.6 | Any user   | As a user, I want tapping a notification to open exactly the record it is about.                                                                      |
| US.7 | Any user   | As a user who denied the OS permission, I want the app to tell me plainly that notifications are off and how to turn them on.                         |

---

## 4. Notification Taxonomy

Two classes ship in V1. They differ in *why* they fire, and that difference is what the settings UI
exposes.

| Class                           | Fires because                                                                            | Delivery                                                 | Timing                                | Audience                                  | Default | Priority                              |
|:--------------------------------|:-----------------------------------------------------------------------------------------|:---------------------------------------------------------|:--------------------------------------|:------------------------------------------|:--------|:--------------------------------------|
| **N1 — Collaboration activity** | Someone *else* created, edited, or deleted a record on a shared aircraft                 | **Server push** (FCM/APNs) on Android/iOS; **local, sync-driven** on web while a tab is open (§7.6) | ~a minute for an isolated edit; up to the coalescing window (§5.4, 5–30 min) during a burst | Every other collaborator on that aircraft | On      | Normal                                |
| **N2 — Urgency escalation**     | A record's urgency moved *up* the ladder — from any cause, including the passage of time | **Local notification**, from a once-daily on-device scan | Next scan — up to 24h later           | The user, on each of their own devices    | On      | High (AOG / Overdue), Normal (others) |

The split in *delivery mechanism* is deliberate, and it is what keeps the whole feature cheap:

- **N1 must be prompt and is inherently multi-user**, so it needs a server that knows the share
  roster. It cannot be local: your device never sees another user's write until sync delivers it,
  and
  it cannot know who else is on the aircraft.
- **N2 is not time-critical and is inherently single-user** — it is a statement about data the
  device
  already holds. An inspection that goes overdue at midnight is just as overdue at 08:00. Computing
  it locally means no server rule engine, no scheduled Cloud Function, no per-user timezone
  plumbing,
  and no server-side fan-out cost.

**Urgency changes made by a collaborator still reach you promptly** — a priority change is an edit,
and escalation edits bypass coalescing (§5.4), so it arrives as an N1 push within the minute. The
daily scan is what catches the cases a push cannot: time-driven crossings, solo aircraft, and any
device that was offline. §7.3 covers the (accepted) overlap between the two.

**Preconditions.** They differ by class, and the settings screen says so plainly rather than showing
dead toggles (§8.4):

| Class  | Requires                                                                                        |
|:-------|:------------------------------------------------------------------------------------------------|
| **N1** | OS notification permission · signed in with a real (non-anonymous) account · cloud sync enabled |
| **N2** | OS notification permission — **nothing else**                                                   |

N1's preconditions are not policy, they are physics: an anonymous or sync-off user's data never
reaches the server, so there is nothing to fan out. N2 has no such constraint, which means the
fully-offline single pilot — the user who most needs "your annual is overdue" — gets it.

---

## 5. N1 — Collaboration Activity

### 5.1 What counts as an update

Every record type a collaborator can write:

| Record               | Notifiable events                                                                         |
|:---------------------|:------------------------------------------------------------------------------------------|
| **Aircraft**         | Aircraft details edited (tail number, model, times, component serials)                    |
| **Squawk**           | Created · edited · priority changed · addressed by a log · dismissed · reopened · deleted |
| **Maintenance task** | Created · edited · force-complied · deleted                                               |
| **Maintenance log**  | Created · edited · deleted                                                                |

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
N4589T · Tasks
Dave Chen added a task: Annual Inspection
```

```
N4589T · Logbook
Dave Chen signed a log entry: 100-hour inspection — Engine
```

```
N4589T · Squawks
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
**(recipient, aircraft, record type, actor)** into a buffer with two timers, not one fixed window:

```
N4589T · Tasks
Dave Chen made 5 changes to tasks
```

Coalescing collapses the *notification*, never the data — each individual change is already in the
synced record. A coalesced notification deep-links to the record type's list for that aircraft
rather than to a single record.

**There is no way to know the actor is "done."** The server only sees writes; it cannot see whether
another card is about to be edited ten seconds from now. So this doesn't try to detect completion —
it infers it from silence, the same debounce pattern a search-as-you-type box uses, and bounds the
uncertainty with a hard ceiling so a long working session still updates people periodically:

- **Quiet timer (target: 5 minutes).** Every matching write resets it. The batch flushes once 5
  minutes pass with no further write for that key — this is what collapses "five edits ninety
  seconds apart" into one notification.
- **Max-wait ceiling (target: 30 minutes), from the *first* write in the batch.** This is what stops
  the quiet timer from being gamed by genuinely continuous editing: a technician steadily working a
  task list for an hour still gets a notification every ~30 minutes, not silence until they finally
  stop. Whichever timer elapses first flushes the batch.

**Mechanism.** The fan-out trigger (§9.2) no longer sends directly — it upserts a small buffer
document at `notification_batches/{recipient}_{aircraftId}_{recordType}_{actorUid}` inside a
transaction (`firstWriteAt` set only if absent, `lastWriteAt` always bumped, a change counter and a
capped sample of record titles for the eventual body). A scheduled sweep — `onSchedule`, the same
Cloud Scheduler mechanism as `scheduledStorageSweep` (`backend/firebase/functions/src/storage/storageSweepTriggers.ts`)
— runs roughly once a minute, reads batches past either timer, sends the coalesced push, and deletes
the buffer inside the same transaction it read it from, so a sweep that overlaps the next trigger
write can't double-send or drop the tail end of a burst.

**Escalations bypass the buffer.** A write that also crosses an N2 threshold (§6.1) — a squawk raised
to AOG, a priority bump — sends immediately as its own specific notification (§6.5's body, not §5.4's
summary), never folded into a batch. AOG is precisely the case where "wait up to 30 minutes to see if
they're still typing" is the wrong tradeoff (§12) — this is a hard rule, not a tuning knob.

## 6. N2 — Urgency Escalation

### 6.1 The urgency ladders

Two independent ladders. Notify on **any upward step**; stay silent on downward steps and on
sideways changes.

**Squawk priority** (`SquawkPriority`, already ordered in the proto):

| Rank | Value    |
|:-----|:---------|
| 1    | `LOW`    |
| 2    | `MEDIUM` |
| 3    | `HIGH`   |
| 4    | `AOG`    |

Any increase in rank escalates. `AOG` is the top of the ladder and always delivers at high priority.
A **reopened** squawk (Addressed/Dismissed → Open) is treated as an escalation from "resolved" at
its
stored priority — it regressed to an open defect, and that is exactly the case a silent app hides.

**Task due status** (`DueStatus`):

| Rank | Value                |
|:-----|:---------------------|
| 0    | `COMPLIED`, `NORMAL` |
| 1    | `DUE_SOON`           |
| 2    | `OVERDUE`            |

> ⚠️ **`DueStatus` ordinal order is not urgency order.** The enum declares
> `NORMAL, DUE_SOON, OVERDUE, COMPLIED`, so `COMPLIED` has the *highest* ordinal and the lowest
> urgency. The implementation must map to an explicit urgency rank; comparing ordinals would treat
> completing an inspection as the most urgent event in the app.

Escalations that must fire (`NORMAL → DUE_SOON`, `NORMAL → OVERDUE`, `DUE_SOON → OVERDUE`) and
transitions that must stay silent (`OVERDUE → COMPLIED`, `DUE_SOON → NORMAL`, `AOG → HIGH`,
priority unchanged). A de-escalation is good news and belongs in N1 activity, not in an urgency
alert.

### 6.2 Audience

**The device's own user, on every device they use.** N2 is not fanned out to anyone: each device
independently scans the data it holds and notifies its own user. Two consequences worth stating:

- **A user's phone and tablet each notify them once.** Correct for local notifications, and the
  reason dismissal is not synchronized (§7.2).
- **Escalations on a shared aircraft still reach every collaborator** — not by fan-out, but because
  the shared aircraft's records sync to each collaborator's device, where each one's own scan sees
  them. A mechanic logging 4.2 hours that pushes three task cards past their 100-hour interval
  produces an overdue notification on the *owner's* phone at its next scan, and on the mechanic's
  too: they did not decide that consequence and are very likely unaware of it.

**N2 applies to solo aircraft.** A pilot with one aircraft and nobody to share it with still needs
to
know their annual went overdue. This is the single most valuable notification in the app for a
single-user account, and scoping it to shares would waste it.

### 6.3 One scan covers both causes

An escalation has two possible causes:

| Cause            | Example                                                                                                                                             |
|:-----------------|:----------------------------------------------------------------------------------------------------------------------------------------------------|
| **Write-driven** | Priority raised; hours logged pushing a task overdue; a force-due date edited — whether by this user or a collaborator whose write has since synced |
| **Time-driven**  | A due date passes at midnight with nobody touching the app                                                                                          |

A daily scan handles both without distinguishing them, because it does not process *events* — it
compares **current computed urgency** against **the last urgency this device reported**, per record:

```
for each aircraft on this device:
  for each task:   rank = urgencyOf(TaskDueManager.dueStatus(task))
  for each squawk: rank = urgencyOf(squawk.priority, squawk.state)
  if rank > lastReportedRank[recordId]:  notify
  lastReportedRank[recordId] = rank      # always, up or down
```

Everything falls out of that comparison rather than needing its own rule:

- **Time-driven crossings** need no special handling — the due status is simply recomputed against
  today's date, exactly as the UI already does it.
- **De-escalations are silent but not forgotten.** The watermark moves *down* too, so a task that is
  complied and later comes due again notifies again.
- **A device that was off for a week reports the truth, not the history.** It compares current state
  against its stale watermark, so a task that went overdue and was complied while the device was
  dark produces nothing. A replayed event log would have produced a wrong, alarming notification.

**When the scan runs.** Once daily, targeting **08:00 local time** — no timezone negotiation, since
the device simply uses its own clock and zone. Nobody is woken because a calendar page turned. The
scan also runs on **app foreground** (cheap, and the reliable path on platforms where background
execution is best-effort — §7.5), debounced so foregrounding six times a day scans once.

### 6.4 First run notifies nothing

On a fresh install, a restore, or the first time an aircraft appears on the device (including one
newly shared in), the scan **seeds the watermark silently and sends nothing**. A pilot who installs
the app and imports a fleet with nine already-overdue inspections must not receive nine
notifications about things that were true before the app existed. They see the overdue badges in the
UI, which is where that belongs; notifications are for *changes* the user has not seen yet.

### 6.5 Content

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

### 6.6 Exactly-once, and batching a scan's findings

**Exactly-once is a property of the watermark, not a separate mechanism.** A task that goes overdue
notifies on the scan that first sees it and stays quiet on every scan after, because the watermark
now equals its rank. There is no idempotency table to maintain and no cross-device coordination to
get wrong — the watermark is local state on the device that did the notifying.

One scan can turn up several crossings at once (a log entry with 4.2 hours can push a whole
inspection group overdue). A scan emits **at most one notification per aircraft per urgency tier**:

```
⚠ Overdue · N4589T
3 inspections are now overdue
```

Single crossings keep the specific body from §6.5 — the summary form only appears when there is
genuinely more than one. Tapping a summary opens that aircraft's task list filtered to the tier.

---

## 7. Cross-cutting Delivery Rules

### 7.1 Never notify the actor about their own edit

Enforced from `writer_uid` on the synced envelope, not from anything client-supplied. The one
deliberate exception is the indirect N2 crossing in §6.2.

### 7.2 Multi-device

A user signed in on phone, tablet, and web gets the notification on **every** registered device with
notifications enabled — for N1 because the fan-out sends to every registered token, and for N2
because each device scans independently. Read/dismiss state is **not** synchronized across devices
in
V1 (that is an inbox feature — §13), so dismissing on the phone leaves the tablet's copy in place.

The asymmetry to be aware of: a device that has been dark for a week receives N1 pushes that FCM
retained, but produces N2 notifications only for crossings still true *now* (§6.3). The two classes
report different things on purpose — activity is history, urgency is state.

### 7.3 N1 and N2 can both fire for the same event — and that's fine

A collaborator raising a squawk to AOG is both an N1 activity event (an edit) and an N2 escalation.
The two are produced by different systems at different times — a push within the minute, a local
scan up to a day later — and **V1 makes no attempt to suppress the second one**: "Sarah raised *Left
brake dragging* to AOG" tonight, then "Squawk *Left brake dragging*'s priority is now High" from
tomorrow's scan, can both land on the same device.

This was a deliberate simplification, not an oversight. The alternative — an N1 push advancing the
record's urgency watermark so the next scan finds it current and stays quiet — requires the push
handler to write into N2's local state, on every platform, with its own race to reason about (sync
lagging the push). That is real cross-system complexity to buy silence on a case that is, on
inspection, cheap to accept: a repeat notification about an aircraft that just got more urgent is
never the wrong instinct, and a pilot re-reading "priority is now High" the next day is not the
failure mode this feature exists to prevent (§2, quiet-by-construction is about *bursts*, not about
two systems independently confirming the same fact). If dogfooding surfaces this as actually
annoying, watermark advance on push is the well-understood fix to reach for — deliberately deferred,
not designed away.

### 7.4 Ordering and lateness

Notifications are best-effort and may arrive out of order or late (a device offline for a day gets
what FCM retained). The **record is always the source of truth** — a notification body is a snapshot
of the moment it was generated, and tapping through shows current state. Bodies are therefore
written
to survive being stale ("raised to AOG", not "is currently AOG").

### 7.5 Platform matrix

The two classes have different platform stories, because one needs a push transport and the other
needs a background scheduler — except on web, where N1 borrows N2's story instead.

| Platform    | N1                                        | N2 — local scan                           | Notes                                                                                                                                                                                                              |
|:------------|:-------------------------------------------|:------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Android** | ✅ V1 — push (FCM)                          | ✅ V1 — WorkManager periodic work          | `POST_NOTIFICATIONS` runtime permission on API 33+; notification channels per class. `WorkManagerUploadScheduler` is the existing precedent for periodic work in `androidMain`.                                    |
| **iOS**     | ✅ V1 — push (APNs via FCM)                 | ✅ V1 — `BGTaskScheduler` app-refresh task | `UNUserNotificationCenter` for both authorization and local delivery. iOS grants background refresh **opportunistically**, so the daily scan is best-effort; the foreground scan (§6.3) is what makes it reliable. |
| **Web**     | ✅ V1 — **open-tab only, sync-driven, no push** (§7.6); ⏳ V1.1 for closed-tab | ✅ V1 — on app open only                   | Neither class touches FCM/APNs on web in V1 — both piggyback on the entity sync stream that is already running whenever a tab is open.                                                                            |

Two capability notions, and they are not the same thing:

- **`AppCapability.isPushSupported`** — gates *push transport specifically*: FCM/APNs delivery that
  works with the app backgrounded or killed. True on Android and iOS in V1; false on `jsMain` until
  the service-worker + web-push work lands in V1.1. It does **not** gate whether N1 exists on a
  platform — §7.6 is proof web has N1 in V1 without it.
- **Local notification support** — gating N2, and now also web's N1 (§7.6). Available wherever the
  OS permits; on web it needs only the Notifications API (no service worker, no push service).

Both follow the build/platform-capability mechanism in
[AGENTS.md](../../AGENTS.md#gating-three-mechanisms-kept-separate), so the settings screen and the
plumbing read the same runtime values.

**iOS honesty note.** If `BGTaskScheduler` proves too unreliable in the field to call this a "daily"
scan, the fallback is not a server — it is scanning on foreground only, and saying so. A user who
opens the app every few days still learns about an overdue inspection far sooner than today, where
they must navigate to the aircraft and read a badge.

### 7.6 Web N1: sync-driven, open-tab only

Web has no push transport in V1 (no service worker, no web-push registration — that is what V1.1
adds). But an open tab isn't an inactive client: it is already running the same entity sync engine
every other platform runs (`feature/sync/data` has `jsMain` actuals today, e.g.
`ForegroundWebBlobScheduler.kt`), so it already receives a collaborator's write on a shared aircraft
the instant sync delivers it — independently of FCM, because that is just how sync works. N1 on web
in V1 is this observation turned into a feature, not new server surface:

1. **Detection.** The client watches its own entity sync stream for shared aircraft. An incoming
   record write whose `writer_uid` (rules-enforced on the envelope, §9.1) isn't the signed-in user is
   exactly an N1 event — the same test the server-side fan-out trigger applies, just run locally.
2. **Actor name.** The envelope carries a uid, not a display name, so "Sarah did that" needs a second
   read: a **one-shot fetch** of the aircraft's share-member roster (the same doc
   `SharingManagerImpl.observeShareState(acId)` already reads for the Manage Access screen and
   technician picker) at the moment a foreign write is detected, not a live subscription kept open
   per shared aircraft. A standing per-aircraft listener would need its own lifecycle — opened when
   the user has any shared aircraft, torn down on sign-out or when a share ends, kept from leaking
   across account switches — for a value (the actor's name) needed only at the instant a notification
   fires. A one-shot read pays a small per-notification latency instead, and mirrors exactly what the
   server's fan-out function already does (§9.1 point 2: one document read for the audience). Simpler
   client, no listener bookkeeping, same fallback to "A collaborator" (§5.3) if the read comes back
   empty.
3. **Display.** `new Notification(...)`, no service worker required — the page itself can show one
   while it has focus or is simply open in a background tab.

**What this does not cover.** A closed tab, or one the browser has fully suspended, runs no
JavaScript and sees nothing until reopened — the same limitation N2-web already accepts. Closed-tab
delivery needs the real web-push stack (service worker, VAPID, a `push_devices` entry) and stays
scoped to V1.1 (§13).

**Coalescing (§5.4) is a client-side concern here, not the server buffer's.** The server-side
debounce-and-sweep never sees this path — an open web tab that gets edited five times in a row would
need its own local quiet-timer/max-wait pair to avoid five notifications. Reusing the same 5-minute /
30-minute constants in a small `commonMain`-shareable timer is the obvious follow-up; V1 can also ship
without it and accept a burst on web specifically, since the burst case is the same one already being
solved server-side for Android/iOS and dogfooding will show whether it matters enough on web to bring
forward.

---

## 8. Permission Priming & Settings UI

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

  DUE STATUS CHANGE
  A squawk or inspection becomes more urgent.
  Checked once a day on this device.
  ─────────────────────────────────────────
  Aircraft grounded (AOG)                  [ ● ]
  Squawk priority raised                   [ ● ]
  Inspection overdue                       [ ● ]
  Inspection due soon                      [ ● ]

  COLLABORATION
  Someone changes a shared aircraft.
  Needs a signed-in account and cloud sync.
  ─────────────────────────────────────────
  Aircraft details                         [ ● ]
  Squawks                                  [ ● ]
  Maintenance tasks                        [ ● ]
  Maintenance logs                         [ ● ]

  ─────────────────────────────────────────
  Collaboration notifications need a signed-in
  account and cloud sync.
  [Sign in]  /  [Turn on sync]
```

The two groups are not equivalent, and the screen should not pretend otherwise: **urgency
notifications work for everyone with the OS permission granted**, including anonymous and
sync-off users, because the check runs on the device against data already there. Only the
collaboration group depends on an account.

### 8.3 Settings table

| Setting                    | Scope        | Default | Behavior                                                                                                                                                           |
|:---------------------------|:-------------|:--------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Notifications** (master) | Account      | On      | Off silences everything, server-side. Every group below is disabled and dimmed while off.                                                                          |
| ~~Aircraft grounded (AOG)~~ | — | — | **Removed 2026-08-26 — see design doc §9.4.** AOG is not its own setting; it reports through "Squawk priority raised" below, no confirm dialog, not high priority. |
| Squawk priority raised     | Account      | **On**  | Any priority escalation, including to AOG, and reopened squawks                                                                                                    |
| Inspection overdue         | Account      | **On**  | `→ OVERDUE`. High priority.                                                                                                                                        |
| Inspection due soon        | Account      | **On**  | `→ DUE_SOON`                                                                                                                                                       |
| Aircraft details           | Account      | **On**  | N1 for the `Aircraft` record                                                                                                                                       |
| Squawks                    | Account      | **On**  | N1 for squawk create/edit/dismiss/reopen/delete                                                                                                                    |
| Maintenance tasks          | Account      | **On**  | N1 for task create/edit/delete                                                                                                                                     |
| Maintenance logs           | Account      | **On**  | N1 for log create/edit/delete                                                                                                                                      |

**Everything defaults on.** A collaboration feature nobody knows exists is worse than one that is
briefly chatty, and turning any class off is one tap away. The coalescing rules in §5.4 are what
keep "all on" tolerable — a fleet manager's day should be a handful of notifications, not a hundred.

**No per-aircraft suppression.** A technician juggling several aircraft mutes by turning off a
*class*, not by managing a per-aircraft mute list — one more list to remember is exactly the kind of
mental overhead this screen exists to avoid. Per-aircraft muting is the natural next lever if
category toggles turn out not to be enough (§13).

### 8.4 Permission and precondition states

The screen must be honest about *why* nothing is arriving — and about which half is affected:

| State                            | Affects            | UI                                                                                                                                                      |
|:---------------------------------|:-------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Permission not yet requested** | Both               | Toggles active; flipping the master on triggers the OS prompt inline                                                                                    |
| **Permission denied**            | Both               | Persistent banner + **Open settings** deep link to the OS app-settings page. Toggles stay editable so the user's choices survive fixing the permission. |
| **Signed out or anonymous**      | Collaboration only | Footer explains the requirement with a **Sign in** action. The urgency group stays fully live and is *not* dimmed.                                      |
| **Cloud sync off**               | Collaboration only | Footer explains the requirement with a **Turn on sync** action linking to Backup & Sync. Urgency unaffected.                                            |

Dimming the urgency group for a signed-out user would be a straightforward bug: it works fine for
them, and they are the users for whom it matters most.

**The denied-permission banner is informational, not an error state.** No red, no destructive
iconography, no "this needs fixing" framing — notifications are a convenience on top of a logbook
that works fine without them (§1), and the app functions completely otherwise. Neutral surface color,
a plain-language sentence, and a deep link where the platform actually offers one:
`ACTION_APPLICATION_DETAILS_SETTINGS` on Android, `UIApplication.openSettingsURLString` on iOS. **Web
has no such API** — a page cannot open the browser's own site-settings UI — so the web banner drops
the button and just names where to look ("notification settings for this site," phrased generically
since the exact path differs by browser).

**Never show a bare OS permission dialog with no explanation in front of it.** The onboarding primer
(§8.6) is the first ask, and it earns the cold-start slot precisely because it is not bare — a card
explaining *why* stands in front of the system dialog, the same pattern the ads-consent explainer
already uses. For the population that never sees the primer (§8.6, existing installs), the fallback
is still contextual: the master toggle, or right after the user creates or accepts a share invite —
the moment when "someone else can now change your aircraft" makes the ask self-explanatory.

### 8.5 Developer Options

Developer builds get **Send test notification** entries (one per class) in Developer Options, gated
on `AppCapability.isDeveloperOptionsSupported` like the rest of that screen. Verifying delivery
should not require a second account and a real AOG squawk.

### 8.6 Onboarding priming

A new step in `AuthFlow` (`feature/login/src/commonMain/.../onboarding/`): **Welcome → Notification
Primer → Ads Consent Explainer → onComplete**. It follows the same shape as the existing Ads Consent
Explainer detour in `AuthFlow.kt`'s `proceedPastOnboarding()` — a plain-language card explaining
*why* ("hear about changes on shared aircraft, and when something goes overdue") with a single
**Continue** that triggers the real OS permission dialog, inserted immediately before the ads-consent
check.

**Platform behavior:**

| Platform | Primer shown? | What "Continue" triggers |
|:---------|:---------------|:--------------------------|
| **Android** | Yes | `POST_NOTIFICATIONS` runtime prompt (API 33+; no-op, auto-granted below that) |
| **iOS** | Yes | `UNUserNotificationCenter.requestAuthorization` — the one dialog that covers both local (N2) and push (N1) delivery |
| **Web** | Yes | `Notification.requestPermission()` — the browser API, which covers both open-tab N1 (§7.6) and open-tab N2 in V1. Closed-tab delivery still needs the separate web-push registration added in V1.1, but that's additive infrastructure, not a second permission prompt |

**Gated like ads consent, not shown unconditionally.** The step has no preference flag of its own —
it reads the OS permission's current state (undetermined / granted / denied) the same way
`AdConsentManager.isConsentRequired()` reads the CMP's cached state, and only renders when that state
is *undetermined*. A returning user who already decided (on this device, in a previous session, or
because they upgraded from a version that asked contextually per §8.4) never sees it again. This is
also why the step is safe to insert unconditionally into the funnel rather than gating it on
"first-time signup only" — sign-in success routes through `proceedPastOnboarding()` for new and
returning users alike, and the background check makes it a no-op for anyone already resolved.

**Not a blocking gate.** Declining does not stop onboarding — the flow proceeds to ads consent (or
completes) exactly as if the user had granted. A denial here is expected to be common and is handled
entirely by §8.4's settings-screen banner, not by re-litigating it during onboarding.

**Existing installs.** Only accounts that pass through `AuthFlow` after this ships see the primer —
an existing signed-in user who upgrades mid-session never re-runs onboarding. For that population the
first ask stays exactly what §8.4 already specifies: the master toggle, or accepting a share invite.
The primer shortens the gap for new users only; it does not replace the contextual fallback.

---

## 9. Architecture Implications

Product-level shape only. The full design — paths, protos, function signatures, rules — belongs in a
companion design doc (`notifications_design.md`).

### 9.1 What the existing codebase already provides

The split in §4 means the two classes lean on different existing machinery, and both already exist.

**For N1 (server):**

1. **The server can read entity payloads.** `payload` is opaque proto bytes, but the Cloud Functions
   build generates the same protos (`src/generated/proto/`) and `onRecordDeleted` / `blobRefs.ts`
   already decode `MaintenanceLog`, `MaintenanceTask`, and `Squawk` payloads. Notification triggers
   are the same move: decode, compare, fan out.
2. **The audience is already a single document read.** `memberRoles` is denormalized onto the share
   ACL root precisely so an authorization check is one `get()`. Fan-out uses the same read.
3. **The actor is already unforgeable.** `writer_uid` on the envelope is rules-enforced to equal the
   caller, so actor suppression needs no new trust.

**For N2 (device):**

4. **The urgency computation already exists and is already local.** `TaskDueManagerImpl` computes
   `DueMetadata` from the local `EntityStore` for every task the UI renders, and squawk priority is
   a
   field read. The scan calls the same manager the screens call — there is no second implementation
   of an airworthiness calculation, on the device or anywhere else.
5. **Periodic background work is already solved per platform.** `WorkManagerUploadScheduler`
   (`androidMain`) and the iOS background `URLSession` blob scheduler are the precedents for
   platform-actual schedulers behind a common interface.
6. **Foreground is already observable.** `core/lifecycle`'s `AppForegroundObserver` gives the
   on-open scan its trigger without new plumbing.

### 9.2 New pieces

| Piece                            | Shape                                                                                                                                                                                                                                                 | Why                                                                                                                                                                                                                                                                                      |
|:---------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Device token registry** (N1)   | Plain-field docs at `users/{uid}/push_devices/{installationId}` — token, platform, app version, per-device enabled flag, `updatedAt`                                                                                                                  | The server must read tokens to send. Same rationale as the sharing ACL exception: server-readable fields cannot ride the opaque entity path.                                                                                                                                             |
| **Fan-out trigger** (N1)         | Firestore trigger over the entity paths — writes/updates a coalescing buffer doc, does not send                                                                                                                                                       | Decode, resolve audience, buffer. §5.4                                                                                                                                                                                                                                                    |
| **Coalescing sweep** (N1)        | `onSchedule` Cloud Scheduler function, ~1 min interval, following the `scheduledStorageSweep` precedent                                                                                                                                                | Flushes buffers past the quiet timer or the max-wait ceiling and sends the actual push. §5.4                                                                                                                                                                                              |
| **Notification preferences**     | New synced entity + `CollectionKind` + proto (`settings/notification_settings.proto`)                                                                                                                                                                 | Preferences are user data: local-first, offline-editable, synced across devices like every other setting. Adding a `CollectionKind` is a zero-migration change (the `collection` column is `TEXT`). The N1 fan-out decodes the payload to honor the user's choices.                      |
| **Urgency watermark store** (N2) | **Device-local table only** — `(recordId → lastReportedRank, lastScanAt)`. Follows the `sync_config` precedent of local state the sync engine never touches.                                                                                          | ⚠️ This must **not** be a `CollectionKind`. Entities sync, and a synced watermark would let one device's scan silence another's — the phone reports the crossing, the tablet never mentions it. Per-device state is the correct semantics, and it keeps the whole of N2 off the network. |
| **Urgency scanner** (N2)         | `commonMain` logic in `feature/notifications/datamanager`, calling `TaskDueManager` + the squawk store                                                                                                                                                | The entire N2 feature, in shared code                                                                                                                                                                                                                                                    |
| **Web N1 detector** (N1, `jsMain` only) | Watches the entity sync stream for foreign writes on shared aircraft; on a hit, one-shot reads the share-member roster for the actor's name and calls the Notifications API. §7.6                                                             | Web's entire N1 story in V1 — no token, no trigger, no sweep involved                                                                                                                                                                                                                     |
| **`feature/notifications`**      | Canonical module set (`model` / `datamanager` / `sharedassets` / `settings`)                                                                                                                                                                          | Scanner, watermarks, token registration, permission state, preference reads/writes, tap-routing                                                                                                                                                                                          |
| **Platform actuals**             | **Messaging:** `androidMain` FCM SDK · `iosMain` Swift bridge (the `platformAdConsentModule` precedent) · `jsMain` no-op until V1.1 (open-tab N1 doesn't use this path — §7.6). **Scheduling + local display:** WorkManager / `BGTaskScheduler` + `UNUserNotificationCenter` / Notifications API | GitLive's KMP Firebase wrapper covers neither messaging nor local notifications                                                                                                                                                                                                          |

### 9.3 Why local detection is the right call, and what it costs

Detecting escalations on the server was the single hardest part of this feature, and moving N2
on-device deletes the problem rather than solving it. Due status is computed from the rule set, the
last complying log, and the current clock — and **no write happens when a due date simply passes**,
so a server would need either a replicated rule engine in TypeScript or a client-published digest to
diff. Both were real work with real failure modes; the first risks two airworthiness calculations
drifting apart, which is a wrong answer about legality.

Doing it locally means:

- **One implementation of due status, forever** — the scan calls `TaskDueManager`, the same code the
  UI calls. There is nothing to keep in step.
- **No server component at all for N2** — no scheduled function, no digest document, no write
  contention over who publishes it, no per-user timezone handling, no fan-out cost, no new
  security-rule surface.
- **It works where the server cannot reach**: offline, anonymous, and sync-off users all get urgency
  notifications (§4).

The honest costs, all of which follow from the user's decision that same-day is good enough:

| Cost                                            | Consequence                                                                                                                                                                                                       |
|:------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Up to 24h latency**                           | An inspection that goes overdue at 00:30 is reported at 08:00. Accepted by design.                                                                                                                                |
| **A dark device reports nothing**               | A phone that is off, or an app never opened, produces no notification until it runs again. A server sweep would have pushed regardless.                                                                           |
| **iOS background execution is opportunistic**   | The daily cadence is best-effort on iOS (§7.5); foreground scanning is the backstop.                                                                                                                              |
| **Watermarks are per-device**                   | A new device starts from a seeded watermark (§6.4) and therefore never reports crossings that predate it. This is intended — it is also what prevents a "welcome to your new phone, here are 9 alarms" first run. |
| **Collaborator-caused crossings wait for sync** | The owner's device cannot see the mechanic's 4.2 hours until it syncs. In practice the N1 push arrives first anyway, so the user is rarely uninformed.                                                            |

### 9.4 Cost and performance

**Server:** one trigger invocation per synced write on a shared aircraft, one ACL read, a token read
per recipient, an FCM send per device — for N1 only. The design doc should still set a per-aircraft
rate ceiling so a bulk import cannot turn into thousands of sends; `feature/stresstest` is compiled
into every build and *will* be pointed at a shared aircraft.

**Device:** one scan per day plus debounced foreground scans, each reading local SQLDelight rows and
running the same computation the dashboard already runs on every open. The work is bounded by fleet
size, and a fleet is tens of aircraft, not thousands. It should run on the storage dispatcher, off
the main thread, and must never block app startup — a scan that delays first paint has already cost
more than it is worth.

### 9.5 Privacy & security

- **N1 bodies carry user content** (tail numbers, squawk titles, names) to Apple/Google push
  services. This is a new trust boundary — Firestore content stays within Firebase today. It should
  be stated in the privacy policy, and it is the reason bodies stay short and carry no attachments
  or
  certificate data.
- **N2 crosses no trust boundary at all.** The scan reads local data and posts a local notification;
  nothing leaves the device. Worth noting explicitly in the privacy policy, since "the app tells me
  my annual is overdue" would otherwise read like a server watching the user's records — and for a
  signed-out, sync-off pilot, nothing about their fleet has ever left the phone.
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

- **`AppCapability.isPushSupported`** — platform support for the *push transport specifically*
  (§7.5), not for N1 as a class: web has N1 in V1 without it (§7.6). N2, and web's N1, gate on local
  notification support instead, a separate platform question.
- **Developer Options** — test-send actions, plus a **Run urgency scan now** action. A feature whose
  normal cadence is once a day is untestable without a manual trigger.
- **No `SubscriptionManager` gate.** Hosting a share is Pro, but *accepting* an invite never is, so
  paywalling notifications would leave an invited technician unable to hear about the aircraft they
  were invited to maintain. N2 in particular is a safety notification that costs nothing to serve —
  it runs on the user's own device against their own data, so there is not even an infrastructure
  argument for gating it. Notifications follow the cloud-sync precedent: part of the app's
  dependability promise, free for everyone. Digests and quiet hours are the plausible Pro surface
  later, not the core alerts.

---

## 10. Rollout

**N2 ships first.** It has no server component, no token registry, and no dependency on sharing, so
it can be built, shipped, and validated while the backend work for N1 has not started — and it
benefits every user, including the solo pilot who never shares anything. Sequencing the harder,
narrower half first would be backwards.

| Phase                                         | Contents                                                                                                                                                                      | Exit criteria                                                                                                                               |
|:----------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------|
| **P1 — Foundations**                          | `feature/notifications` module; permission handling per platform; local-notification display; preferences entity; settings UI (all platforms); Developer Options test actions | A dev build can request permission and post a local notification; preferences persist and sync                                              |
| **P2 — N2 urgency (Android + iOS)**           | Watermark store; scanner over `TaskDueManager` + squawks; WorkManager / `BGTaskScheduler` scheduling; foreground scan; first-run seeding; per-tier batching; deep links       | Crossings fire exactly once; de-escalations silent; a fresh install notifies nothing; **no backend change was required to ship this phase** |
| **P3 — Web N1 (open-tab)**                    | Entity-sync foreign-write detector; one-shot actor-name read off the share-member roster; Notifications API display (§7.6)                                                    | A web user with a shared aircraft open in one tab sees a collaborator's edit from another account; **no backend, no token registry**        |
| **P4 — N1 collaboration (backend + Android)** | Token registry; fan-out trigger; coalescing; actor suppression                                                                                                                | Two accounts sharing an aircraft see each other's changes; neither sees their own                                                            |
| **P5 — N1 on iOS**                            | APNs certificates, entitlements, delivery                                                                                                                                     | Parity with Android on N1                                                                                                                   |
| **P6 — Polish + web push**                    | Closed-tab web N1 and web N2 via service worker + web push (V1.1)                                                                                                              | `isPushSupported` flips true on `jsMain`                                                                                                     |

Dogfood across P2–P5 on the team's own aircraft. The noise floor has to be felt rather than reasoned
about — P2 is where the daily-scan cadence and batching get tuned, P4 where the coalescing window
does.

---

## 11. Success Metrics

| Metric                                                                              | Target                                                                                                                                                         |
|:------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Shares with ≥1 notification class enabled 30 days after joining                     | > 80%                                                                                                                                                          |
| Master-toggle opt-out rate                                                          | < 10%                                                                                                                                                          |
| Per-class opt-out rate (any single class)                                           | < 25%                                                                                                                                                          |
| Notification tap-through rate                                                       | > 15%                                                                                                                                                          |
| Median time from AOG escalation to the owner opening the aircraft                   | < 2 hours (from "next app open", unbounded today)                                                                                                              |
| Self notifications reported (actor notified about their own edit)                   | 0 — always a bug (§7.1); N1/N2 overlap on the same event (§7.3) is expected and excluded from this metric                                                     |
| Urgency notifications delivered by the **background** scan (not the foreground one) | Measurable and non-zero per platform — this is the number that says whether the daily cadence actually runs, and the one that decides the iOS fallback in §7.5 |
| Solo (unshared) accounts receiving ≥1 urgency notification                          | Measurable — N2's reach beyond collaboration is the argument for building it first                                                                             |

Instrument via the existing analytics
plan ([analytics_design.html](../analytics/analytics_design.html),
proposed) rather than inventing a parallel event pipeline. Note that N2 telemetry is the one place
this feature *does* touch the network for a signed-out user; whether an anonymous, sync-off pilot's
scan reports anything at all is a privacy call for the design doc, and the safe default is that it
does not.

---

## 12. Risks

| Risk                                                                                                                                                 | Mitigation                                                                                                                                                                   |
|:-----------------------------------------------------------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Noise drives a global opt-out.** One chatty week and the master toggle goes off forever.                                                           | Coalescing (§5.4); per-tier batching (§6.6); per-class granularity; dogfood tuning before GA; exactly-once escalations                                                        |
| **The daily scan doesn't run.** iOS background refresh is opportunistic; Android OEM battery managers kill periodic work; a phone can simply be off. | Foreground scan as the reliable path (§6.3); measure background-vs-foreground delivery (§11) and fall back to foreground-only with honest copy if the numbers are bad (§7.5) |
| **First run dumps a pile of alarms.** Nine already-overdue inspections on install.                                                                   | Silent watermark seeding (§6.4) — the one rule that most determines whether the feature feels considerate or broken                                                          |
| **A synced watermark silences a second device.** If the watermark is ever made a `CollectionKind`, one device's scan mutes the other's.              | Explicitly device-local storage (§9.2) — worth a comment in the code, since "make it an entity" is the reflex this codebase trains                                           |
| **Notification says something the recipient can no longer see.** Revocation racing a send.                                                           | Audience re-derived from the ACL at send time; tap-through degrades to "no longer available"                                                                                 |
| **Fan-out storm from bulk writes.** Stress-test generator against a shared aircraft.                                                                 | Per-aircraft rate ceiling; coalescing before send, not after                                                                                                                 |
| **Permission asked at the wrong moment and denied forever.**                                                                                         | Explained onboarding primer (§8.6) or contextual re-ask (§8.4); never a bare OS dialog                                                                                        |
| **iOS APNs operational burden** (certificates, entitlements, background modes).                                                                      | Sequenced as its own phase (P5) rather than bundled with the product logic                                                                                                   |

---

## 13. Out of Scope (V1)

Listed because each is a plausible next step, not because it was overlooked:

- **In-app notification inbox / per-aircraft activity feed** with cross-device read state — the
  natural V2, and the durable counterpart to transient push.
- **Per-aircraft mute.** Category toggles (§8.3) are the only lever in V1 — see the rationale there.
- **Quiet hours** and **daily/weekly digests**, with AOG and Overdue bypassing both.
- **Role-scoped profiles** — e.g. technicians default to squawks and tasks only.
- **Email notifications** (the export mailer exists; whether to reuse it is a separate decision).
- **Per-record follow/unfollow** ("notify me about this squawk only").
- **Reminders that are not escalations** — "annual due in 30 days," recurring nags while overdue.
- **Notification actions** — dismiss a squawk or acknowledge from the tray.
- **Closed-tab web delivery** — the service-worker + web-push stack (V1.1, §7.6). Open-tab web N1
  and N2 both ship in V1.

---

## 14. Open Questions

| #   | Question                                                                                                                  | Recommendation                                                                                                                                                                                                                          |
|:----|:--------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Q1  | ~~Escalation detection: server-side rule engine or published urgency digest?~~                                            | **Resolved — neither.** N2 is detected entirely on-device by a daily scan (§6.3, §9.3). No server component, no digest document, no second airworthiness engine.                                                                        |
| Q2  | Are preferences account-level or per-device?                                                                              | **Account-level**, synced, with a per-device enabled flag on the token doc so one device can be silenced without changing the account's choices. Note the deliberate asymmetry: preferences sync, urgency **watermarks do not** (§9.2). |
| Q3  | Coalescing window length (N1)?                                                                                            | Start at **5 minutes**, tune during dogfood (P4)                                                                                                                                                                                        |
| Q4  | What hour does the daily scan target?                                                                                     | **08:00 device-local.** No timezone plumbing needed — the device uses its own clock.                                                                                                                                                    |
| Q5  | ~~Should AOG be un-mutable?~~                                                                                             | **Moot as of 2026-08-26 — AOG is not its own setting** (design doc §9.4). The confirm-gated toggle this question produced was removed; AOG now shares "Squawk priority raised" like any other escalation.                              |
| Q6  | Do deletions notify?                                                                                                      | **Yes** (§5.1) — a removed log entry is material                                                                                                                                                                                        |
| Q7  | Does an owner get N1 for their *own* aircraft edits on a solo aircraft?                                                   | **No** — never notify the actor; and with no collaborators there is no audience                                                                                                                                                         |
| Q8  | Notification channel granularity on Android?                                                                              | **One channel per class** (Collaboration, Urgency) so OS-level controls mirror the in-app settings — Grounded was folded into Urgency 2026-08-26                                                                                        |
| Q9  | Should the scan also run right after a sync lands new remote data, instead of waiting for the next daily/foreground scan? | **Not in V1.** It narrows the latency gap for collaborator-caused crossings, but those already arrive as an N1 push. Revisit if the N1-off / N2-on population turns out to be large.                                                    |
| Q10 | Does the scan run on a device where the user is signed out?                                                               | **Yes** — N2 has no account precondition (§4), and the scheduler has no reason to consult auth state                                                                                                                                    |
