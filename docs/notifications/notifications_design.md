# Design Doc: Notifications

**PRD:** [notifications_PRD.md](notifications_PRD.md)
**Status:** 📋 Proposed — not implemented
**Last updated:** 2026-08-17
**Areas:** `feature/notifications` (new) · `core/storage` · `core/model` · `core/nav` · `core/appinfo` ·
`core/ui` · `feature/shell` · `feature/settings` · `feature/stresstest/config` · `feature/login` ·
`feature/sync/data` · `backend/firebase/functions`
**Related docs:** [notifications_PRD.md](notifications_PRD.md) ·
[storage_r1_design.md](../storage/storage_r1_design.md) ·
[squawk_design.md](../squawks/squawk_design.md) ·
[aircraft_sharing_design.html](../sharing/aircraft_sharing_design.html) ·
[subscription_design.html](../subscription/subscription_design.html) ·
[DESIGN.md](../../DESIGN.md)

---

## Implementation status — 📋 NOT STARTED

Nothing in this doc exists yet. There is no `feature/notifications` module, no FCM/APNs dependency,
no `POST_NOTIFICATIONS` declaration in any manifest, and no notification-related `CollectionKind`,
proto, table, or Cloud Function. Every file path below is a proposal.

---

## 1. Overview

The PRD splits notifications into two classes with two entirely different mechanisms. This document
keeps that split as the primary structural line, because it is also where the engineering risk
divides:

| | **N2 — urgency escalation** | **N1 — collaboration activity** |
|:--|:--|:--|
| Where it runs | Entirely on the device | Firestore trigger + FCM/APNs (web: on-device) |
| New backend surface | **None** | Token registry, fan-out trigger, activity counter |
| Depends on sharing | No | Yes |
| Correctness risk | Watermark semantics, seeding, enum-rank mapping | At-least-once delivery, audience freshness, storms |
| Ships first | ✅ P2 | P4 |

**N2 is the whole product for a solo pilot and requires no server**, so it is built first and its
machinery — the module, the preferences entity, the permission actuals, the local notifier, the
settings screen — is what N1 later reuses. N1 adds a transport, not a second feature.

Two things this design deliberately does **not** introduce:

- **No second airworthiness calculation.** The scanner calls `TaskDueManager.computeNextDue` — the
  same object `TaskCardItem` and `TaskDetailSheet` render from. There is no TypeScript due engine and
  no client-published digest, per PRD §9.3 / Q1.
- **No event log.** The scanner compares *current computed urgency* against *the last rank this
  device reported*, per record. Everything the PRD asks for (time-driven crossings, silent
  de-escalations, a device that was dark for a week) falls out of that comparison rather than needing
  its own rule.

---

## 2. What already exists, verified

Every claim in PRD §9.1, checked against the tree at `25feae0b`. Two of them need a correction.

| PRD claim | Verified | Notes |
|:--|:--|:--|
| Server can decode entity payloads | ✅ | `onRecordDeleted.ts` decodes via `src/generated/proto/`; `blobRefs.ts` gates on `schemaCanOwnBlobs` |
| Audience is one document read | ✅ | `aircraft_shares/{hostUid}/aircraft/{acId}.memberRoles` — `firestore.rules:29-32` |
| Actor is unforgeable | ✅ | `writerIsSelf()` in `firestore.rules:42-45`; stamped by `SyncWrite.writerUid` (`SyncWriter.kt:31`), carried down as `RemoteEntity.writerUid` and surfaced as `StorageEntity.writerUid` |
| Due computation exists and is local | ✅ | `TaskDueManagerImpl` — pure function of `(card, logs, allCards)` + injected `Clock`/`TimeZone` |
| Periodic background work is solved per platform | ✅ | `WorkManagerUploadScheduler` (androidMain), `UrlSessionUploadScheduler` (iosMain), both behind `UploadScheduler` in `core/storage` |
| Foreground is observable | ⚠️ **Partly** | `AppForegroundObserver` exists but is a **session-boundary counter with a 30-minute threshold**, not a foreground event stream. See §6.6. |
| `MaintenanceOverview` supplies accumulated hours | ⚠️ **Not how the scan needs it** | `TaskDueManagerImpl` derives current engine/airframe time from `max()` over the aircraft's **logs**, not from the overview. The scanner must feed it logs. See §6.3. |

Two more facts the PRD does not mention that materially shape this design:

- **`aircraft.proto` and `settings/*.proto` are not generated for Cloud Functions.** The
  `generate:proto` script in `backend/firebase/functions/package.json` names five protos explicitly:
  `request_export_delivery`, `shared_aircraft_ref`, `maintenance_log`, `squawk`, `maintenance_task`.
  The fan-out needs the tail number (from `Aircraft`) and the recipient's preferences (from a new
  settings proto), so both must be added to that list. §7.2.
- **`CollectionKind.DeveloperOptions` is an entity but is not in `SyncEngine.TOP_LEVEL_KINDS`**, so
  it pushes but never hydrates. Do not copy it as the precedent for a synced settings entity —
  §4.2 lists the five places a new kind must be registered, one of which developer options skips.

---

## 3. Module layout

```
feature/notifications/
├── model/          NotificationClass, UrgencyRank, UrgencyLadder, PendingNotification,
│                   NotificationChannel, NotificationTapTarget, PushTokenSink,
│                   NotificationSettingsExt (the §4.1 inversion, once)
├── permission/     MAY we show one. NotificationPermission (expect), PermissionState.
│                   A leaf: no dependency on any other notification module.
├── viewing/        HOW one is shown. LocalNotifier (expect), channel registration,
│                   PendingNotification -> platform notification, the FCM message
│                   receiver, NotificationTapRouter
├── datamanager/    NotificationPrefsManager, PushTokenRegistrar
├── engine/         WHAT to show, and when. UrgencyScanner, UrgencyWatermarkStore,
│                   UrgencyScanScheduler (expect), the web N1 detector, ActivityCounter
├── sharedassets/   PermissionBanner, NotificationClassRow, notification strings
│                   (settings-screen furniture; the onboarding primer shares none of it — §10.1)
├── settings/       NotificationSettingsScreen, NotificationSettingsViewModel,
│                   di/NotificationSettingsModule.kt
└── devoptions/     NotificationDeveloperOptionsExtra — one file, developer-only,
                    contributed through Koin so nothing imports it (§11)
```

**The split is the load-bearing decision here.** A single `datamanager` holding all of this would have
to depend on five other features' datamanagers — the scanner genuinely needs tasks, logs, squawks,
the fleet, and shares — and everything that only wanted to post a notification or ask whether it is
allowed to would inherit that whole graph. Concretely: `feature/login` would pull
`feature:tasks:datamanager` into its compile classpath to show one onboarding card.

So the feature separates three questions that have three different answers and three different
audiences — **may we notify** (`permission`), **how is one shown** (`viewing`), **what is worth
showing** (`engine`) — and the constraint is stated as a rule rather than left to discipline:

> **`permission` and `viewing` may depend on `core:*` (and `viewing` on
> `feature:notifications:model`). Nothing else.** Not a feature datamanager, not `feature:sync:data`,
> not each other, not `engine`. If something in either needs to know about a task or a squawk, it
> belongs in `engine`.

`engine` depends on both and calls `NotificationPermission.observe()` and `LocalNotifier.post(...)`.
The arrows never point back.

| Module | Depends on | Notes |
|:--|:--|:--|
| `model` | `core:model` (protos) | Pure types + two interfaces. No Compose, no platform code. |
| `permission` | `core:lifecycle` (Android's `CurrentActivityProvider`), `core:appinfo` | **The lightest module in the feature, and deliberately a leaf** — not even `:model`. Three consumers need to ask "may we notify?" without needing anything else the feature owns. |
| `viewing` | `core:ui`, `core:ui:theme`, `core:nav`, `:model` | Platform actuals for display. Does **not** depend on `:permission`: posting and being allowed to post are separate questions, and the caller has already answered the second. |
| `datamanager` | `core:storage`, `feature:sync:data`, `:model` | Preferences (§4.3 needs `SyncCursorStore`/`CloudSyncSetting`) and the token doc. No tasks/logs/squawks. |
| `engine` | `core:storage`, `core:lifecycle`, `feature:{tasks,logs,squawk,fleet,sharing}:datamanager`, `feature:sync:data`, `:model`, `:permission`, `:viewing`, `:datamanager` | The wide fan-in, contained to one module. Deliberately a *consumer* of the existing managers so no computation is duplicated. |
| `sharedassets` | `core:ui`, `:model`, `:permission` | `PermissionBanner` renders `PermissionState` |
| `devoptions` | `core:ui`, `feature:developeroptions:plugin`, `:engine`, `:viewing` | Developer-only. Its own module so `engine` stays Compose-free and `settings` stays `engine`-free — the same reason `feature:stresstest:config` is separate from `feature:stresstest`. §11 |
| `settings` | `core:ui*`, `:model`, `:permission`, `:datamanager`, `:sharedassets` | **Not `engine`, and not `viewing`.** The screen reads preferences and permission state; it neither scans nor posts. |

And what the rest of the app takes on:

| Consumer | Depends on | Why |
|:--|:--|:--|
| `feature:login` | `:permission` only | `NotificationPermission` for the primer (§10.1) |
| `feature:shell` | `:settings`, `:viewing` | The `Screen.Notifications` route, and collecting tap routes into the nav graph. **Not `engine` and not `devoptions`** — both reach the screen through `DeveloperOptionsExtra` / `DeveloperOptionsNavContributor` (§11.1, shipped in #511/#512) |
| `feature:settings` | `:sharedassets`, `:permission` | The settings row's live subtitle. **Not `engine`** — §11. |
| hosts | `:engine`, `:viewing`, `:permission`, `:devoptions` | Koin + platform init (channel registration, scheduler, FCM). `devoptions` appears here and nowhere else: it is registered, never imported. |

`settings/` rather than the canonical `viewing/` + `update/` pair *for the settings screen*, following
the `feature/sync/settings` precedent exactly (one screen, one ViewModel, one Koin module,
`compose.resources { publicResClass = true }`, reached from the shared nav graph). Note this feature
uses `viewing/` for something else — the notification display surface — which is a deliberate
departure from the canonical meaning and is called out in §15 D12.

**`feature:sync:data` must not depend on any `feature:notifications` module.** The web N1 detector
(§8) needs to observe the sync stream, which would invert this. §8.2 resolves it with a listener
interface owned by `core:storage`.

**Koin registration** (`core/di/CommonAppModules.kt`, alphabetical position after
`maintenanceViewingModule`):

```kotlin
platformNotificationPermissionModule, // :permission — NotificationPermission actual
notificationPrefsModule,              // :datamanager
notificationDisplayModule,            // :viewing  — common bindings
platformNotificationDisplayModule,    // :viewing  — LocalNotifier actual
notificationEngineModule,             // :engine   — scanner, watermarks, web N1 detector
platformNotificationEngineModule,     // :engine   — UrgencyScanScheduler actual
notificationSettingsModule,           // :settings
```

One module per Gradle module rather than one aggregate, so the Koin graph mirrors the compile graph
and a missing binding names the module that owns it. The two `platform*` modules are `expect val`
per host, following `platformAdConsentModule` — the established pattern for "a Koin module whose
bindings only exist per platform." Bindings use `get<ClassType>()`, never bare `get()`; a repo hook
rejects the latter.

---

## 4. Preferences

### 4.1 `settings/notification_settings.proto`

```proto
syntax = "proto3";

option java_package = "dev.fanfly.wingslog.core.model.settings";
option java_multiple_files = true;

// Account-level notification preferences. See docs/notifications/notifications_design.md §4.
//
// EVERY FIELD IS INVERTED — `*_disabled`, never `*_enabled`. proto3 has no field presence for
// scalars, so an absent or default-constructed message decodes to all-false. The PRD (§8.3) wants
// every class ON by default, so all-false must MEAN all-on. A user who has never opened the
// settings screen has no doc at all; a `*_enabled` field would silence them.
//
// This is the same convention DeveloperSettings already reserves under
// (`technician_disabled`, `attachment_upload_disabled`).
message NotificationSettings {
  // Master switch. Off silences every class, locally and server-side.
  bool all_disabled = 1;

  reserved 2; // aog_disabled — AOG is not its own tier (decided 2026-08-26); reserved, not reused.

  // --- Urgency (N2) — device-local detection, no account required ---
  bool squawk_priority_disabled = 3;     // any priority escalation, including to AOG, and reopens
  bool overdue_disabled = 4;             // -> DueStatus.OVERDUE
  bool due_soon_disabled = 5;            // -> DueStatus.DUE_SOON

  // --- Collaboration (N1) — server fan-out, needs a real account + cloud sync ---
  bool aircraft_activity_disabled = 6;   // Aircraft record edited
  bool squawk_activity_disabled = 7;     // squawk create/edit/dismiss/reopen/delete
  bool task_activity_disabled = 8;       // task create/edit/force-comply/delete
  bool log_activity_disabled = 9;        // log create/edit/delete
}
```

The Kotlin-facing type inverts back so no call site reasons in negatives:

**There is no Kotlin mirror of this message.** `NotificationSettings` *is* the type the app passes
around — `PrefsState.Resolved` carries it, the manager writes it, the ViewModel reads it. Callers get
readable positive names from extension properties, which are derived rather than copied:

```kotlin
// feature/notifications/model — NotificationSettingsExt.kt
// The ONE place the inversion in §4.1 is spelled out. Everything else reads positives.
val NotificationSettings.allEnabled: Boolean get() = !all_disabled
val NotificationSettings.squawkPriorityEnabled: Boolean get() = !squawk_priority_disabled
val NotificationSettings.overdueEnabled: Boolean get() = !overdue_disabled
// …one line per field

fun NotificationSettings.withSquawkPriority(enabled: Boolean) = copy(squawk_priority_disabled = !enabled)
// …one per field; wire generates `copy` on the message (TechnicianManagerImpl already relies on it)
```

An earlier draft had a `data class NotificationPrefs` of nine positive booleans defaulting to `true`,
on the `DeveloperFlags` precedent. That was a forked copy, and the cost outweighs the ergonomics:

- **It restates the defaults.** §4.1's whole argument is that *absent doc = default-constructed
  message = everything on* is one fact. A mirror with `= true` on every field states it a second
  time, in a place that can silently disagree with the proto.
- **It needs a round-trip test to stay honest.** `DeveloperOptionsMappingTest` exists precisely
  because "adding a field to the data class alone compiles cleanly and then silently drops the value
  on write, which looks exactly like a toggle that refuses to turn on." Extension properties cannot
  drift that way: a new proto field either gets an extension or the call site does not compile.
- **`SubscriptionManager` already has the opinion**, in its interface doc: "The types are the
  `Subscription` proto and its enums — the same model the Cloud Functions and Firestore share —
  **never a forked Kotlin copy**." Notification settings are read by Cloud Functions too (§7.4), so
  that reasoning applies here verbatim.

The ergonomics the mirror was bought for survive intact: `settings.aogEnabled` reads no worse than
`prefs.aog`, and `update { it.withAog(false) }` reads better than reconstructing a whole record.

### 4.2 Registering the `CollectionKind`

Adding a kind is a zero-migration change (the `collection` column is `TEXT`), but it touches five
places, and `CollectionKindCoverageTest` plus `AttachmentRefs`' exhaustive `when` will fail the build
if any is missed — which is the intent:

| # | File | Change |
|:--|:--|:--|
| 1 | `core/storage/.../CollectionKind.kt` | `data object NotificationSettings` (`wireName = "notification_settings"`, `schemaName = "settings.NotificationSettings"`) + add to `ALL` |
| 2 | `core/storage/.../di/StorageModule.kt` | `register(CollectionKind.NotificationSettings, WireCodec(NotificationSettings.ADAPTER))` |
| 3 | `core/storage/.../blob/AttachmentRefs.kt` | add to the `-> emptyList()` arm — it owns no blobs |
| 4 | `core/storage/.../LocalAccountMigrator.kt` | **not** an identity singleton: preferences a guest set should follow them into the upgraded account, so it migrates like normal data rather than joining `UserInfo`/`DeveloperOptions` in the drop list |
| 5 | `feature/sync/data/.../SyncEngine.kt` | add to `TOP_LEVEL_KINDS` — this is the step `DeveloperOptions` skips, and skipping it here would mean preferences push but never arrive on a second device |

Stored at `users/{uid}/notification_settings/main` — the `"main"` doc id is the house convention for
a per-user singleton, shared by `UserInfo` and `DeveloperOptions`. Covered by the existing
`users/{userId}/{document=**}` own-tree rule; no rules change.

### 4.3 `NotificationPrefsManager`, and why it is not `DeveloperOptionsManagerImpl`

`DeveloperOptionsManagerImpl` is the closest-looking file in the tree — same `EntityStoreFactory`,
same `authStateChanged.flatMapLatest`, same `"main"` doc id — and copying it would be a mistake.
**`DeveloperOptions` is not in `TOP_LEVEL_KINDS` (§4.2 row 5), so it never hydrates**, and every
simplification it is entitled to make depends on that.

The right precedent is `TechnicianManagerImpl`'s handling of `UserInfo` — the other synced
per-user singleton, at the same `"main"` id — and specifically `awaitHydratedSelfId`, whose comment
states the problem exactly: "The cloud may know a self id we haven't hydrated yet — sync pulls
`UserInfo` on sign-in. Rather than reading Firestore here (the sync engine is the only Firestore
client), wait for hydration to land the row in the local store."

#### Three consequences of hydrating, in increasing order of damage

**1. `null` stops meaning "never set."** For `DeveloperOptions`, no local row can only mean the user
has not changed anything, so resolving to defaults is immediately correct. For a hydrating kind it
*also* means "this device signed in a moment ago and `notification_settings` has not arrived yet."
A flow that answers "all on" during that window is answering a question it cannot yet answer.

**2. A write during that window silently reverts every other device.** `EntityStore.put` writes the
**whole message** with `dirty = 1`, and `SyncWriter`'s contract is explicit that implementations
"issue an overwrite, never a merge." So a user who opens the screen before hydration lands, sees
all-on, and flips AOG off has just pushed an all-defaults-except-AOG message — reverting the four
classes they switched off on their phone last week. `PullListener`'s comparator then prefers the
dirty local row over the incoming remote one, so the real settings lose. `UserInfo` never exposes
this because it carries one meaningful field, so a whole-message overwrite has no sibling to clobber;
`NotificationSettings` carries nine.

**3. The scanner cannot un-send.** A screen showing the wrong state for two seconds is #451's flash
one layer deeper. A notification posted against default preferences is permanent — a user who
switched Due Soon off gets Due Soon alerts on a fresh install for as long as hydration takes, which
is precisely the "it got noisy so I turned it all off" failure in PRD §12.

#### The interface

Resolution is part of the contract rather than something each caller re-derives:

```kotlin
sealed interface PrefsState {
  /** Signed in with sync on, and notification_settings has not hydrated yet. Do not read through. */
  data object Unresolved : PrefsState
  data class Resolved(val settings: NotificationSettings) : PrefsState
}

interface NotificationPrefsManager {
  fun observe(): Flow<PrefsState>
  /**
   * Copies onto the currently resolved value. Fails (does not write) while [PrefsState.Unresolved] —
   * a write from an unresolved state is a whole-message overwrite of settings this device has not
   * read yet.
   */
  suspend fun update(mutate: (NotificationSettings) -> NotificationSettings): Result<Unit>
}
```

`update` takes a mutator rather than a whole `NotificationSettings` for the same reason: a caller that
hands over a value it built itself can reintroduce consequence 2 from outside the manager.

#### Resolution rule

In order; the first match wins:

| Condition | State |
|:--|:--|
| No signed-in user | `Resolved(defaults)` — nothing to hydrate, and the settings screen is still usable (§6.8) |
| `CloudSyncSetting.isCloudSyncEnabled()` is false | `Resolved(local row ?: defaults)` — nothing will *ever* hydrate, so waiting would hang forever. This is the same first-line guard `awaitHydratedSelfId` uses. |
| A local row exists | `Resolved(it)` — whether it arrived by local edit or by hydration is irrelevant |
| `SyncCursorStore.get(uid, NotificationSettings, userRoot)?.hydrated == true` | `Resolved(defaults)` — hydration finished and there genuinely is no doc, so the user has never set preferences |
| Otherwise | `Unresolved`, until a row lands, the cursor flips, or `PREFS_HYDRATION_TIMEOUT` elapses → `Resolved(defaults)` |

The cursor check is what `awaitHydratedSelfId` cannot do — it has no way to distinguish "no self id"
from "not yet," so it leans on a bare timeout. Here the `sync_cursor` row makes the distinction
exactly, and the timeout is only a backstop for hydration failing outright (`failed_attempts` backs
off, so "never hydrates" is a state the app must survive, not an impossibility).

This costs `feature:notifications:datamanager` a dependency on `feature:sync:data` for
`SyncCursorStore`. That direction is fine — it is how `feature/sync/settings` already gets
`SyncEngine`. The forbidden direction is the reverse, which §8.2 avoids with a `core:storage`
interface.

#### Consumers

- **Settings screen** — `Unresolved` renders the spinner and disables the toggles, per §9.2's
  `isLoading`. Disabling matters more than the spinner: it is what makes consequence 2 unreachable.
- **`UrgencyScanner`** — `Unresolved` returns `ScanResult.PrefsUnresolved` and the scan does nothing
  at all: no notifications, no seeding, **no watermark advance**. Skipping costs at most one cycle;
  advancing watermarks against unresolved preferences would permanently swallow the crossings.

#### The server has the same ambiguity, and does not need this machinery

§7.4's trigger reads `users/{recipient}/notification_settings/main` directly from Firestore, which *is*
the source of truth — a missing doc there means "never set," full stop. An absent doc, a
default-decoded message, and all-on are the same thing (§4.1), so the server needs no equivalent
resolution step. The inverted-boolean convention is what makes the two sides agree without either
knowing about the other.

**Preferences are account-level and synced; the per-device silence switch is not.** Q2 asks for a
per-device enabled flag, and it belongs on the token doc (§7.1), not here — a synced field cannot
mean "this device."

---

## 5. Platform surfaces

Four `expect` interfaces, split across four modules by what each needs to know:

| Interface | Module | Because |
|:--|:--|:--|
| `NotificationPermission` | `permission` | Asks the OS one question about this app. Knows nothing about aircraft, and nothing about notifications either. |
| `LocalNotifier` | `viewing` | Renders a `PendingNotification`. The caller decided what goes in it. |
| `PushTokenRegistrar` | `datamanager` | Writes an account-scoped document |
| `UrgencyScanScheduler` | `engine` | Schedules the scan, so it lives with the scan |

Keeping them separate is what lets each consumer take only what it needs: N2 uses all but the
registrar, web N1 uses `LocalNotifier` + `NotificationPermission` and neither of the push pair
(PRD §7.6), the settings screen uses `NotificationPermission` alone, and so does the onboarding
primer (§10.1).

### 5.1 `NotificationPermission` — `permission`

```kotlin
enum class PermissionState { UNDETERMINED, GRANTED, DENIED, UNSUPPORTED }

interface NotificationPermission {
  /** Cheap, synchronous-ish read of the OS state. Re-read on foreground: the user can change it in system settings. */
  fun observe(): StateFlow<PermissionState>
  suspend fun refresh()
  /** Shows the real OS dialog. No-ops (and reports the current state) when not UNDETERMINED. */
  suspend fun request(): PermissionState
  /** True where the platform exposes a deep link to its own app-settings page — false on web. */
  val canOpenSystemSettings: Boolean
  fun openSystemSettings()
}
```

**`UNSUPPORTED` is a fourth state, not a variant of `DENIED`.** On web, the `Notification` API can be
genuinely absent — an insecure origin, or a browser old enough not to implement it — and that is a
capability question, not a permission one. Reporting `DENIED` for it would tell the settings screen
to render "Blocked in system settings" with an "Open settings" fix that does not exist for a missing
API. `refresh()` sets it once and it never changes for the life of the process; `request()` on an
`UNSUPPORTED` device is a no-op that reports `UNSUPPORTED` back, the same way it already no-ops on
anything but `UNDETERMINED`.

**Deliberately not an `AppCapability` flag.** §7.5 draws two capability lines — push transport, and
"local notification support" gating N2 and web's N1 — and the second one *reads* like it wants a
build-time flag too. It should not get one. `AppCapability` answers "what can this build ever do";
whether the Notifications API exists is a property of the running browser, discovered at runtime, and
`NotificationPermission` already answers exactly that kind of question for the OS-level cases. A
capability flag here would be a fourth gating mechanism in spirit, which AGENTS.md's three-mechanism
rule (`AppCapability` / `SubscriptionManager` / `DeveloperFlags`) exists to prevent.

This is the whole module. It has four consumers — the onboarding primer, the settings screen and its
banner, and the scanner's precondition check — none of which want anything else the feature owns, so
it depends on nothing the feature owns either: not `:model`, not `:viewing`. The only reason it is
under `feature/notifications/` rather than in `core/` is that OS notification permission is this
feature's concern; nothing else in the app asks for it.

That leafness is what makes the §10.1 dependency defensible. A primer card that transitively compiled
against `feature:tasks:datamanager` would be the tail wagging the dog.

| Platform | `request()` | `openSystemSettings()` | `UNSUPPORTED` when |
|:--|:--|:--|:--|
| Android | `POST_NOTIFICATIONS` via the activity from `CurrentActivityProvider` (already in `core/lifecycle/androidMain`, already used by `AuthManagerImpl`); auto-`GRANTED` below API 33 | `ACTION_APPLICATION_DETAILS_SETTINGS` | Never — `minSdk` 33 always has the permission surface |
| iOS | `UNUserNotificationCenter.requestAuthorizationWithOptions` (alert + sound + badge) | `UIApplication.openSettingsURLString` | Never |
| Web | `Notification.requestPermission()` | `canOpenSystemSettings = false` — no browser API exists. §9.3 | `"Notification" !in window`, or an insecure origin |

`minSdk` is 33 across the tree (`feature/sync/settings/build.gradle.kts:13` and siblings), so the
sub-33 auto-grant branch is dead code today. Write it anyway and comment why: the runtime prompt is
the behaviour that matters and a future `minSdk` drop must not silently start denying.

### 5.2 `LocalNotifier` — `viewing`

`PendingNotification` is the contract between the two halves of the feature: `engine` builds one,
`viewing` renders it. It carries finished display strings and a tap target, never a task or a squawk
— which is what keeps `viewing` free of every other feature.

```kotlin
enum class NotificationChannel { COLLABORATION, URGENCY }  // Q8: one per class

data class PendingNotification(
  val id: String,                 // stable — re-posting the same id replaces, never stacks
  val channel: NotificationChannel,
  val title: String,
  val body: String,
  val highPriority: Boolean,      // Overdue only
  val tapTarget: NotificationTapTarget,
)

interface LocalNotifier {
  suspend fun post(notification: PendingNotification)
  suspend fun cancel(id: String)
}
```

Android registers the two channels at Koin init (they must exist before the first post, and
re-creating an existing channel is a no-op), both at `IMPORTANCE_DEFAULT`. iOS never maps
`highPriority` to `UNNotificationInterruptionLevel.timeSensitive` — the Time Sensitive Notifications
entitlement (an App Store review item) was decided against (2026-08-26): AOG is not its own tier, so
there is no class left that would have used it. A high-priority notification still arrives on iOS; it
just never pierces Focus.

Notification ids are deterministic so a re-scan replaces rather than stacks:
`"urgency:$aircraftId:$tier"` for N2 summaries, `"urgency:$collection:$recordId"` for singles.

### 5.3 `NotificationTapTarget` and deep links — `viewing`

```kotlin
sealed interface NotificationTapTarget {
  data class Aircraft(val aircraftId: String, val tab: AircraftTab) : NotificationTapTarget
  data class Squawk(val aircraftId: String, val squawkId: String) : NotificationTapTarget
  data class Task(val aircraftId: String, val taskId: String) : NotificationTapTarget
  data class Log(val aircraftId: String, val logId: String) : NotificationTapTarget
}
```

`NotificationTapRouter` in `viewing` carries the target into the running app. Tapping is the return
leg of display, so it belongs with the notifier that posted the thing being tapped. It holds a
`StateFlow<NotificationTapTarget?>` that the shell collects — the same shape
`EmailLinkDeepLinks.pendingLink` uses, so cold start (target retained until the shell composes) and
warm tap (delivered immediately) both work without a second mechanism. It travels between process
entry point and shell as a `wingslog://notification-tap/…` URI, so it can arrive through the same
host deep-link chain as a share invite or a sign-in link.

**No tap navigates. Every tap moves shell state**, as of 2026-08-23:

| Target | Aircraft | Section | Then |
|:--|:--|:--|:--|
| `Squawk` | selected | Squawks | scroll to the card, brief highlight |
| `Task` | selected | Tasks | scroll to the card, brief highlight |
| `Log` | selected | Logs | scroll to the row, brief highlight |
| `Aircraft` (summary) | selected | from `tab` | — no single record to point at |

This replaces an earlier sketch in which the record variants pushed `Screen.EditSquawk` and friends,
and the summary needed a new `AircraftTabDeepLink` route. Two reasons it changed, the second
decisive:

- **A notification reports that something changed; it is not a request to edit it.** Opening the
  editor presumes an intent the pilot has not expressed, and puts an unsaved-changes guard between
  them and what was meant to be a glance. Landing on the record in its list shows it *and* what sits
  around it — the neighbouring overdue items that give it its meaning.
- **Aircraft selection and section are not navigation arguments in this app** and deliberately never
  will be (see `AdaptiveShellViewModel`, `docs/web/web_adaptive_layout_design.html` §6). A route
  carrying them would have to fight the shell rather than drive it, and the summary case had no
  destination at all without inventing one.

So `AdaptiveShellViewModel.onNotificationTap` applies all four variants: `selectAircraft`, then
`selectSection`, then for a record variant a `pendingScrollTargetId` the section body reads and
clears. The scroll target reaches the list through the *same* `scrollTo…Id` parameter the in-app
jumps already used (a log's Affected Tasks / Resolved Squawks), so both paths share one
implementation — including `Modifier.jumpTargetHighlight` in `core:ui`, which the in-app jumps now
get too. That wash is deliberately not a Material ripple: a ripple acknowledges a touch, and the
pilot did not touch the card the app scrolled them to.

Because the shell destination composes only after the auth graph hands off, a tap that cold-starts
the app needs no gate of its own — the target simply stays pending until there is somewhere to put
it. That also covers a signed-out recipient: the target survives sign-in and lands afterwards.

**Getting the tap from the OS to the router is the only per-platform part.** Everything above the
router is shared, so each host only has to supply delivery:

| Platform | Carried as | Delivered by |
|:--|:--|:--|
| Android | intent data on a tap `PendingIntent` (`getLaunchIntentForPackage`, so `:viewing` needs no reference to `app`) | `MainActivity.handleDeepLink`, one more link in the existing `AircraftShareDeepLinks` / `EmailLinkDeepLinks` chain |
| iOS | `content.userInfo[wingslog_tap_uri]` | `IosNotificationTapDelegate`, a `UNUserNotificationCenterDelegate` installed from `MainEntry.registerNotificationTapHandler()` |
| Web | nothing — the handler closes over the target | `Notification.onclick`, which also `window.focus()`es the tab first |

The iOS delegate is written in Kotlin/Native rather than Swift, unlike the sign-in and ads bridges:
those exist because Kotlin/Native cannot link those SDKs at all, whereas `UserNotifications` has
interop already and `IosLocalNotifier` posts through it directly. It must be installed before
`application(_:didFinishLaunchingWithOptions:)` returns, or iOS drops the response for a tap that
cold-started the app — the case that matters most, since that is what tapping from the lock screen
does. `MainEntry` holds the instance because `UNUserNotificationCenter.delegate` is a *weak*
reference.

That delegate also implements `willPresentNotification` so urgency banners appear while the app is
foregrounded. iOS suppresses them by default; Android and web do not, and an alert that silently
does not appear because the pilot has the app open is the worst case for suppression — they are
looking at the very aircraft it concerns.

PRD §6.6 ("Tapping a summary opens that aircraft's task list filtered to the tier") is satisfied
apart from the tier pre-filter, which is not yet implemented — the summary lands on the right
aircraft and the right section, unfiltered.

**Tap-through must degrade, not crash.** A revoked share, a deleted record, or a device that has not
synced yet all produce "no longer available" and land on the fleet (PRD §12).

**The router does not pre-check that the record resolves.** An earlier draft had it do so; the module
rule in §3 forbids it — resolving a squawk id means `feature:squawk:datamanager`, which `viewing` may
not depend on. That constraint improves the design rather than constraining it: the check was racy
anyway (the record can vanish between the check and the scroll), and it is no longer a question worth
asking. Once a tap lands the pilot *in the list* rather than on a pushed record screen, an
unresolvable id degrades on its own — a scroll target matching nothing scrolls nowhere and highlights
nothing, leaving them on the right aircraft's list, looking at whatever is actually there. There is
no empty state to design and no "no longer available" path to take.

### 5.4 `UrgencyScanScheduler` — `engine`

```kotlin
interface UrgencyScanScheduler {
  /** Idempotent. Called at Koin init and after every permission/preference change. */
  fun ensureScheduled()
  fun cancel()
}
```

| Platform | Implementation |
|:--|:--|
| Android | `PeriodicWorkRequest` (2h, flex 15min — WorkManager's minimum) via `WorkManager.enqueueUniquePeriodicWork(KEEP)`, no network constraint — the scan is local. `WorkManagerUploadScheduler` is the shape to copy. |
| iOS | `BGTaskScheduler` app-refresh task registered in `didFinishLaunching`, re-submitted at the end of each run (iOS does not repeat a submission). Opportunistic — §6.6. |
| Web | No-op. The foreground scan is the only scan; see §6.6. |

### 5.5 Push transport — `viewing` renders, `datamanager` registers

The FCM plumbing splits along the same seam. An incoming data-only message (§7.6) is already a
finished decision made by the server; turning its data map into a `PendingNotification` is rendering,
so the receiver lives in `viewing` and needs nothing but `model`.

Token *refresh*, though, arrives on the same platform callback and has to reach the account-scoped
`PushTokenRegistrar` in `datamanager` — the wrong direction for the rule. A one-method sink in
`model` closes it:

```kotlin
// feature/notifications/model — implemented by :datamanager, called by :viewing
fun interface PushTokenSink {
  suspend fun onTokenRefreshed(token: String)
}
```

The Android `FirebaseMessagingService` therefore lives in `viewing/androidMain`, renders
`onMessageReceived` itself, and forwards `onNewToken` to the injected sink. `viewing` never learns
what a token is for. `PushTokenRegistrar` is N1-only and lands in P4; §7.1.

---

## 6. N2 — the urgency scanner

### 6.1 Urgency ranks

The PRD's ⚠️ in §6.1 is real: `DueStatus` declares `NORMAL, DUE_SOON, OVERDUE, COMPLIED`, so
`COMPLIED.ordinal == 3` — the highest. Comparing ordinals would make finishing an annual the most
urgent event in the app.

`feature/notifications/model/UrgencyRank.kt`:

```kotlin
/**
 * Urgency rank within one ladder. NOT the enum ordinal — DueStatus.COMPLIED has the HIGHEST ordinal
 * and the LOWEST urgency. Two records are only ever comparable within the same ladder, which is why
 * the watermark key carries the CollectionKind (§6.2).
 */
@JvmInline
value class UrgencyRank(val value: Int) : Comparable<UrgencyRank> {
  override fun compareTo(other: UrgencyRank) = value.compareTo(other.value)
  companion object { val RESOLVED = UrgencyRank(0) }
}

// Exhaustive `when`, no `else`: a new DueStatus value must fail the build here rather than
// silently rank as 0 and go unreported forever.
fun DueStatus.urgencyRank(): UrgencyRank = when (this) {
  DueStatus.COMPLIED, DueStatus.NORMAL -> UrgencyRank(0)
  DueStatus.DUE_SOON -> UrgencyRank(1)
  DueStatus.OVERDUE  -> UrgencyRank(2)
}

fun SquawkWithStatus.urgencyRank(): UrgencyRank = when (status) {
  SquawkStatus.ADDRESSED, SquawkStatus.DISMISSED -> UrgencyRank(0)
  SquawkStatus.OPEN -> when (squawk.priority) {
    SquawkPriority.SQUAWK_PRIORITY_AOG     -> UrgencyRank(4)
    SquawkPriority.SQUAWK_PRIORITY_HIGH    -> UrgencyRank(3)
    SquawkPriority.SQUAWK_PRIORITY_MEDIUM  -> UrgencyRank(2)
    // An open squawk is never rank 0 — rank 0 means "resolved", and an unset priority is still
    // an open defect. Squawks written before priority was mandatory decode as UNKNOWN.
    SquawkPriority.SQUAWK_PRIORITY_LOW,
    SquawkPriority.SQUAWK_PRIORITY_UNKNOWN -> UrgencyRank(1)
  }
}
```

**Reopen needs no special case, and that is the check that the mapping is right.** PRD §6.1 asks for
a reopened squawk to be treated as an escalation from "resolved" at its stored priority. Dismissed is
rank 0; reopening restores `OPEN` at the stored priority, so the rank goes 0 → 1..4 and the plain
`rank > watermark` test fires. Any mapping that needed an if-statement here would be the wrong
mapping.

Note that `SquawkStatus` is derived, not stored (`Squawk.toWithStatus()` in
`feature/squawk/model`) — the scanner calls it rather than reading a field.

### 6.2 The watermark table

New table in `core/storage/.../db/Schema.sq`, beside `sync_config`:

```sql
-- Per-device, per-record high-water mark of urgency ALREADY REPORTED to this user (§6).
--
-- ⚠️ This is deliberately NOT a CollectionKind. Entities sync; a synced watermark would let the
-- phone's scan silence the tablet's — the phone reports the crossing, the tablet sees the mark
-- already at the current rank and stays quiet forever. Per-device is the correct semantics, and it
-- is also what keeps the whole of N2 off the network. `sync_config` is the precedent: local state
-- the sync engine never touches.
--
-- Keyed like `entity` minus the payload, so a record is unambiguous even across two hosts that
-- happen to use the same aircraft id.
CREATE TABLE urgency_watermark (
  uid         TEXT    NOT NULL,
  collection  TEXT    AS CollectionKind NOT NULL,
  scope_path  TEXT    NOT NULL,
  id          TEXT    NOT NULL,
  rank        INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL,
  PRIMARY KEY (uid, collection, scope_path, id)
);
```

Plus `selectWatermarksInScopePrefix`, `upsertWatermark`, `deleteWatermarksNotIn` (prune, §6.4), and
`deleteWatermarksForUser`.

**Lifecycle:**

| Event | Watermarks |
|:--|:--|
| Sign-out | **Deleted, as of 2026-08-22.** `deleteWatermarksForUser` runs alongside `deleteEntitiesForUser` in `DatabaseIntegrityChecker.wipeDataForUser`. Superseded the original "kept" design: leaving another account's aircraft ids and urgency ranks recoverable from the raw SQLite file after sign-out is a privacy leak on a shared/borrowed device, same reasoning §7.1 already applies to push tokens ("a stale token on a shared device leaks another account's squawk titles into the tray"). The cost is accepted: a user who signs back into the *same* account gets silently re-seeded on the next scan (§6.4's seeding rule) rather than compared against real prior state — a lost cycle, not a lost feature, and never a notification storm. |
| Integrity-check wipe | **Kept**, for the same reason `sync_config` is excluded from `wipeAllEntities` — it is user-facing state, not a cache, and this event never hands the device to a different account. |
| Account deletion | Deleted (`deleteWatermarksForUser`, alongside `deleteEntitiesForUser`). |
| Guest → account upgrade | Re-keyed with the entities, in `LocalAccountMigrator`'s existing transaction. Not re-keying would silently re-seed the whole fleet at the exact moment the user has most reason to trust the app. |

### 6.3 The scan

`UrgencyScanner` in `engine/commonMain` — the entire N2 decision, in shared code. It is the one class
that justifies `engine`'s fan-in, and everything it reads is read through an existing manager.

```
suspend fun scan(trigger: ScanTrigger): ScanResult

1. uid = auth.currentUser?.uid ?: return NoUser        // §6.7
2. state = prefsManager.observe().first()
   if (state is Unresolved) return PrefsUnresolved     // §4.3 — no notify, no seed, no watermark write
   settings = state.settings
   if (!settings.allEnabled) return Disabled
   if (permission.observe().value != GRANTED) return NoPermission
3. fleet = fleetManager.observeFleetDashboard().first() // own + shared, PRD §6.2
4. for each aircraft:
     tasks   = taskDataManager.observeTasks(acId).first()
     logs    = logManager.observeLogs(acId).first()
     squawks = squawkManager.observeSquawks(acId).first()
     scope   = scopeResolver.resolveNow(acId)

     for each task:   rank = dueManager.computeNextDue(task, logs, tasks).status.urgencyRank()
     for each squawk: rank = squawk.toWithStatus().urgencyRank()
5. diff every rank against urgency_watermark; collect crossings where rank > watermark
6. drop crossings whose tier is switched off in prefs
7. group into at most one notification per (aircraft, tier)   // §6.5
8. post them all
9. commit every rank — up and down — in ONE transaction, and prune  // §6.6, §6.4
```

Four details that are load-bearing:

**Logs, not the overview.** `TaskDueManagerImpl` derives current engine and airframe time from
`max()` over the aircraft's `MaintenanceLog` rows (lines 61-70), branching on
`ComponentType.COMPONENT_AIRFRAME`. It never reads `MaintenanceOverview`. Passing anything else, or
passing an empty log list, silently computes every hour-based task against 0 hours and reports the
entire fleet overdue. `allCards` must be the full task list too — `LinkedRule` resolves against it
(line 175).

**Scope comes from the resolver, never the uid.** `AircraftScopeResolver.resolveNow(aircraftId)` for
the watermark's `scope_path`. On a shared aircraft the records live under the host's tree, and using
the signed-in uid would key the owner's watermarks and the technician's into paths that do not match
the data — the exact mistake `EntityScope.aircraftChildUnsafe`'s doc comment was written about.

**`.first()` on each flow, on the storage dispatcher.** These are SQLDelight-backed flows;
`.first()` reads the current value and detaches. The whole scan runs on the storage IO context and
must never be awaited on the main thread or at startup — a scan that delays first paint has already
cost more than it is worth (PRD §9.4).

**One scan at a time.** `UrgencyScanner` holds a `Mutex`; a foreground scan arriving while the
scheduled one runs waits rather than double-reporting. Reentrancy is real — a `WorkManager` job and
an app launch can coincide.

### 6.4 Seeding, and one refinement to PRD §6.4

PRD §6.4 is unambiguous about a fresh install and a newly shared-in aircraft: seed silently, send
nothing. It is silent about a **new record on an aircraft the device already knows**, and the two
plausible readings differ in a way that matters.

The rule this design adopts:

| Situation | Behaviour |
|:--|:--|
| Aircraft not previously seen on this device (install, restore, newly shared in) | Seed **every** record at its current rank. Send nothing. |
| New record on a known aircraft, `writerUid` == this user | Seed at current rank. Send nothing — you filed it, you know. |
| New record on a known aircraft, `writerUid` is someone else or null | Seed at **rank 0**, so the same scan reports it if it is already urgent. |

The third row is the refinement. Without it, a mechanic filing an AOG squawk on a shared aircraft is
seeded silently on the owner's device and N2 never mentions it — the owner's only signal is the N1
push, which they may have switched off (§4.3 makes N1 and N2 independently mutable) or which may not
have been delivered. "A collaborator created something already urgent" is precisely the case this
feature exists for. `StorageEntity.writerUid` supplies the test and is rules-enforced, so it is the
same trust the server-side actor suppression rests on.

It is also cheap in noise: a collaborator's new `NORMAL` task ranks 0 against a 0 watermark and says
nothing, and a bulk import collapses into one per-tier summary (§6.5).

**Pruning.** A deleted record leaves a watermark row forever. At the end of each scan, delete
watermark rows under the scopes the scan actually visited whose ids it did not see. Scoping the
prune to visited scopes is what keeps an un-hydrated aircraft from having its history erased and
then silently re-seeded on the next scan.

### 6.5 Batching a scan's findings

Per PRD §6.6, at most one notification per `(aircraft, tier)`, where tier ∈ {Overdue, Due Soon,
Priority raised — which includes AOG}:

```
1 crossing  → the specific body from PRD §6.5
2+ crossings → the summary, e.g. "3 inspections are now overdue"
```

Bodies come from `strings.xml` in `feature/notifications/sharedassets` with plurals for the counts.
Apostrophes are written literally (`’`), never `\'` — hook-enforced, and `\'` renders as a backslash
in Compose resources.

Exactly-once needs no separate mechanism: after the scan commits, the watermark equals the rank, so
the next scan finds nothing. There is no idempotency table and no cross-device coordination.

### 6.6 Scheduling, and the `AppForegroundObserver` correction

PRD §9.1 point 6 says foreground is already observable via `AppForegroundObserver`. It is not, quite.
That class is a **session-boundary counter**: it exposes a monotonically increasing `sessionId` that
advances on cold start and after 30 minutes in the background, built for the ad session cap. It has
no "app came to the foreground" event, and it is driven from a `LifecycleResumeEffect` at the shell
root rather than from platform lifecycle.

Two options, and the second is the recommendation:

1. Add a foreground event stream to `AppForegroundObserver`. Widens a class whose doc comment
   explicitly says it is scoped minimum-viable, for a second consumer with different semantics.
2. **Collect `sessionId` in the scanner.** A new session id is exactly "the user came back after
   being away a while" — which is the trigger the scan wants, already debounced by the 30-minute
   threshold, already unit-testable with a fake clock, already driven on all three hosts. Its comment
   invites exactly this: "If the analytics taxonomy later wants a session id, it should grow from
   here rather than introduce a second observer."

So: the scanner collects `sessionId`, and on each change runs a scan **if `now - lastScanAt >= 2h`**
(`lastScanAt` in `sync_config`, via the `SyncPreferences.booleanConfig` pattern). The scheduled
background scan ignores the debounce.

**Superseded 2026-08-22: target cadence moved from once-daily (08:00 local) to at least every 2h.**
Scan is the *only* detection mechanism (§6.3) — there is no push-driven path this backstops — so a
tighter cadence is a direct latency win, not a safety net. The debounce moved from 4h to 2h to match.

| Platform | Background schedule | Session-boundary | Effective cadence |
|:--|:--|:--|:--|
| Android | `PeriodicWorkRequest` 2h / flex 15min | ✅ | Close to every 2h for an active user — the session scan and the periodic job both land inside that window. OEM battery managers may still kill periodic work; the session scan is the backstop. |
| iOS | `BGTaskScheduler`, opportunistic | ✅ | **Not every 2h in practice, regardless of the interval requested.** `BGAppRefreshTask` is entirely iOS-scheduled — it weighs engagement, battery, Low Power Mode, and a system-wide per-app budget, and `earliestBeginDate` is a lower bound iOS is free to blow past by hours. For an app opened at least daily, real-world cadence is commonly a handful of times a day at best, and can go quiet for a full day or more for a less-engaged user. **The session-boundary scan is what actually delivers close to 2h cadence on iOS** — every app open past the 30-minute session threshold scans, debounced to at most once per 2h — not the background scheduler. |
| Web | none | ✅ | Session scan only, and only while a tab is open. |

Do not build timezone plumbing for the Android periodic job: the device uses its own clock, and
`TaskDueManagerImpl` already defaults to `TimeZone.currentSystemDefault()`.

**Metric to instrument from day one** (PRD §11): what share of urgency notifications came from the
background scan versus the session scan, per platform. That single number decides whether iOS's copy
can honestly claim "every 2 hours" or has to fall back to foreground-only framing (PRD §7.5's honesty
note) — the metric was written for the daily cadence but answers exactly the same question at 2h.

### 6.7 Post, then commit

If the process dies between posting and committing, one choice re-notifies and the other loses the
notification. This design **posts first, then commits all watermarks in one transaction**: a duplicate
"your annual is overdue" is a minor annoyance; a dropped one is the failure this feature exists to
prevent. Recorded here because the opposite ordering looks tidier and someone will propose it.

### 6.8 What "works signed out" actually means

PRD §4 says N2 requires "OS notification permission — nothing else," and Q10 says the scan runs while
signed out. Both are true only in the sense the app supports: **anonymous accounts**. Local data is
addressed by `EntityScope.userRoot(uid)`, `FleetManager.observeFleetDashboard()` emits `emptyList()`
with no user, and `AircraftScopeResolver.resolveNow` throws. With no Firebase user at all there is no
fleet on the device, so the scan is correctly a no-op.

What the PRD is actually promising — and what this design delivers — is that N2 needs **no real
account, no cloud sync, and no network**: an anonymous pilot with `cloudSyncEnabled = false` and
airplane mode still gets "your annual is overdue," because every input is on the device. That is the
claim to make in the settings copy (§9.3) and in the privacy policy (§12.3). It is worth noting that
`AppCapability.isAnonymousLoginSupported` is per-platform, so the truly-no-account population is
platform-dependent.

---

## 7. N1 — server fan-out

Everything in this section is P4+ and is *not* required for P2 or P3 to ship.

### 7.1 Device token registry

`users/{uid}/push_devices/{installationId}` — plain fields, not proto bytes, because the server must
read them. Same rationale as the sharing ACL exception (`SharingManager`'s doc comment).

```ts
type PushDevice = {
  token: string;          // FCM registration token
  platform: "android" | "ios" | "web";
  appVersion: string;
  enabled: boolean;       // per-device silence switch (Q2) — the one preference that is NOT synced
  updatedAt: Timestamp;
};
```

Rules — a user reads and writes only their own, and no client ever reads another user's tokens. The
existing `users/{userId}/{document=**}` own-tree rule already grants exactly this, and the
default-deny catch-all covers everyone else. **No rules change is needed**; add a comment at the
`match /users/{userId}` block naming `push_devices` so the next reader does not assume it was
overlooked.

Two collision checks, both clean: `SyncEngine.TOP_LEVEL_KINDS` is an explicit list, so
`push_devices` is never mistaken for a synced kind; and `onRecordDeleted` matches
`users/{uid}/aircraft/{acId}/{kind}/{docId}`, one level deeper.

**`installationId` needs a stable per-install identifier that outlives token rotation.** Keying by
the token itself orphans a doc on every rotation. `sync_config` is uid-keyed, so it would mint a new
id per account on a shared device. Add a two-column device table beside it:

```sql
-- Device-scoped, uid-independent local config. Distinct from sync_config, which is per-account.
CREATE TABLE device_config (
  key   TEXT NOT NULL PRIMARY KEY,
  value TEXT NOT NULL
);
```

`PushTokenRegistrar` reads or mints a UUID there, then upserts the token doc on: sign-in, token
refresh (the FCM callback), app version change, and the per-device toggle.

**Deleted on sign-out and on account deletion.** A stale token on a shared device leaks another
account's squawk titles into the tray. Sign-out already wipes local rows per user; deleting the
token doc joins that path, and `deleteMyAccount` gains the cleanup.

### 7.2 The fan-out trigger

`backend/firebase/functions/src/notifications/onRecordWritten.ts`, an `onDocumentWritten` over
`users/{uid}/aircraft/{acId}/{kind}/{docId}` — the same path `onRecordDeleted` already watches —
plus a second trigger on `users/{uid}/aircraft/{acId}` for the Aircraft record itself.

```
1. hostUid = params.uid   // from the PATH. Unspoofable, same property firestore.rules leans on.
2. acl = get(aircraft_shares/{hostUid}/aircraft/{acId})
   if (!acl.exists || Object.keys(acl.memberRoles).length <= 1) return   // unshared: no audience
3. actorUid = after.writerUid
4. decode before/after payloads -> record title, and for squawks the priority
5. if (priority escalated) -> send immediately, bypass the buffer   // §7.5
   else                    -> bump the activity counter and send    // §7.3, §7.4
```

Step 2 is the early exit that keeps this cheap: **most writes are on unshared aircraft**, and they
cost one document read and nothing else. The `<= 1` test (not `!exists`) also covers a share whose
last member left but whose ACL doc survives.

Two additions to `backend/firebase/functions/package.json`'s `generate:proto`, both currently absent:

- `aircraft/aircraft.proto` — the notification body leads with the tail number (PRD §5.3, "aircraft
  identity first"), and the server cannot read it out of anything else; the aircraft record is opaque
  proto bytes.
- `settings/notification_settings.proto` — the trigger decodes each recipient's preferences to honor
  their per-class toggles.

### 7.3 Coalescing by replacement, not by buffering

PRD §5.4 buffers writes behind a quiet timer and a max-wait ceiling, and flushes one summary. That
shape has two problems, and the second is the one that killed it:

- **It needs a clock the server does not have.** Firestore triggers fire on writes, never on their
  absence, so "5 minutes with no further write" requires either a polling sweep or Cloud Tasks.
- **It cannot meet the PRD's own latency target.** PRD §4 promises "~a minute for an isolated edit,"
  but a trailing-edge debounce cannot know an edit was isolated until the window has passed — so
  *every* N1, including a lone squawk edit with nothing around it, would wait the full 5 minutes.

**So nothing is buffered. Every write sends, and the tray does the coalescing** — each send reuses a
deterministic notification id, so the second push through the eighth *replace* the first entry rather
than stacking beside it:

| Time | Write | Push | What the recipient's tray holds |
|:--|:--|:--|:--|
| 14:00:00 | A task 1 | id `n1:{A}:task:{dave}:1` | `N4589T · Tasks` / `Dave Chen updated a task: Annual Inspection` |
| 14:00:40 | A task 2 | same id — replaces | `Dave Chen made 2 changes to tasks` |
| 14:01:10 | B task 1 | id `n1:{B}:task:{dave}:1` | + `N771TS · Tasks` / `Dave Chen updated a task: 100-Hour Inspection` |
| 14:04:30 | A task 5 | same id — replaces | `Dave Chen made 5 changes to tasks` |

Dave's eight edits across two aircraft leave two tray entries, each accurate — and accurate at every
intermediate moment, not only once he stops. Compare the buffered design, which would have shown
Sarah nothing for nine minutes; and a leading-edge variant, which sends instantly but then silently
loses six of the eight edits because nothing wakes up to flush the tail.

The id is `n1:{aircraftId}:{recordType}:{actorUid}:{sessionSeq}` — PRD §5.4's coalescing key, moved
from a server buffer into the notification id. §5.2 already required deterministic ids so a re-scan
replaces rather than stacks; this is that mechanism doing a second job.

**The session component is load-bearing, and the whole id is wrong without it.** It is the counter's
`sessionSeq`, incremented whenever `ACTIVITY_WINDOW` elapses (§7.4) — so a *working session* owns a
tray entry, not a `(aircraft, recordType, actor)` triple forever. Without it, Dave editing one task an
hour after his morning burst reuses the same id and **overwrites** "Dave Chen made 5 changes to
tasks" with "Dave Chen updated a task: Oil Change," destroying news the recipient may never have
read. With it:

| Time | Write | Notification id | Sarah's tray |
|:--|:--|:--|:--|
| 14:00–14:04 | A tasks 1–5 | `n1:{A}:task:{dave}:1` | `Dave Chen made 5 changes to tasks` |
| 15:10 | A task 6 | `n1:{A}:task:{dave}:2` | `Dave Chen made 5 changes to tasks`<br>`Dave Chen updated a task: Oil Change` |

Two sessions, two entries, neither clobbering the other — and within each, replacement still collapses
the burst. The rule the id encodes is *replace what is still being updated, never what is finished*.

| Platform | Replacement primitive | Silent update |
|:--|:--|:--|
| Android | Same notification id in `NotificationManager.notify` | `setOnlyAlertOnce(true)` |
| iOS | Same `UNNotificationRequest` identifier | `interruptionLevel = .passive` on the update |
| Web | Same `tag` on `new Notification(...)` | Silent unless `renotify: true`, which `WebLocalNotifier` sets only when nothing is live under that tag. §8.4 |

So the recipient is buzzed once per aircraft and the entry quietly keeps up.

**`collapse_key` carries the same property over the wire.** Setting FCM `collapse_key` (and
`apns-collapse-id`) to the notification id means a device that is offline for the whole burst
receives only the *last* message on reconnect, not eight. The dedup then costs nothing even in the
worst case.

### 7.4 The counter, and what replaces the sweep

`scheduledNotificationSweep` is **deleted.** There is no timer, no Cloud Scheduler job, no Cloud
Tasks queue, and no idle polling — work happens on writes, which is the only time there is work.

What remains is a small counter doc so a body can say "5 changes" instead of "a change":

```
notification_activity/{hostUid}__{aircraftId}__{recordType}__{actorUid}
  hostUid, aircraftId, recordType, actorUid
  aircraftLabel        // resolved once — cosmetic, so staleness is harmless
  actorDisplayName     // from aircraft_shares/{host}/aircraft/{ac}/members/{actor}.displayName
  sessionSeq           // which working session — THIS is what the notification id is keyed on (§7.3)
  firstWriteAt         // session start; telemetry and debugging only
  lastWriteAt
  writeCount           // lifetime, only ever incremented — see below
  sessionBaseCount     // writeCount when the session began; the body reports the difference
  lastSentAt
```

Plus one rate-limit doc per aircraft per hour, read on every write and written only on send:

```
notification_rate/{hostUid}__{aircraftId}__{yyyymmddHH}
  sendCount
```

**`hostUid` leads both keys, and that is #204 again rather than a detail.** An aircraft id is unique
only *within one user's tree* — it is a 20-character client-generated string (`IdGenerator.kt`), and
the own-tree rule lets any account create `users/{self}/aircraft/{anyId}`. Keyed on the aircraft id
alone these documents form one global namespace, and every current and former member of a share
knows its aircraft id: create a same-id aircraft in your own tree, share it with a second account,
write for an hour, and the *victim's* aircraft trips `AIRCRAFT_HOURLY_CEILING` and goes quiet.
`hostUid` comes from the trigger path and cannot be claimed, so under it a document a writer can
influence only ever governs that writer's own tree — the same property `aircraft_shares` was
re-keyed for.

The trigger's whole job, per write:

```
1. hostUid = params.uid                        // from the PATH — unspoofable, §7.2
2. acl = get(aircraft_shares/{hostUid}/aircraft/{acId})
   if (!acl.exists || memberRoles.size <= 1) return          // unshared: the cheap early exit
3. rate = get(notification_rate/{hostUid}__{acId}__{hour})    // a READ — cheap, no contention
   if (rate.sendCount >= AIRCRAFT_HOURLY_CEILING) return     // storm: stop before touching anything
4. prev = get(notification_activity/{key})                   // plain read, NOT a transaction
   newSession = now - prev.lastWriteAt > ACTIVITY_WINDOW
   set(key, merge = true):
     writeCount   = FieldValue.increment(1)                   // never assigned — see below
     sessionSeq   = newSession ? prev.sessionSeq + 1 : prev.sessionSeq   // rolls the id (§7.3)
     sessionBaseCount = newSession ? prev.writeCount : unchanged
     lastWriteAt  = now
5. if (now - prev.lastSentAt < MIN_REPOST_INTERVAL) return    // throttle
6. audience = memberRoles minus actorUid                      // re-derived every send, §9.5
   for each recipient honoring this class in their prefs:
     send to their enabled tokens, collapse_key = notification id
7. set(key, { lastSentAt: now }); increment(rate.sendCount)
```

Four details that carry the weight the sweep used to:

- **`ACTIVITY_WINDOW` (30 min) ends a working session**, moving both `sessionBaseCount` *and*
  `sessionSeq` — the latter is what rolls the notification id so the finished session's tray entry
  is left alone (§7.3). **A sequence, not the session's start time.** Two writers who both decide
  "new session" read the same previous value and compute the same next one, so they converge on one
  id; two clock reads milliseconds apart do not, and leave the recipient with two tray entries for
  one session, one of which nothing ever updates again. Tuesday afternoon and Wednesday morning are two entries reading "5 changes"
  and "3 changes," never one reading "8 changes" and never one overwriting the other. Evaluated
  lazily on the next write, so it needs no timer either.
- **`MIN_REPOST_INTERVAL` (30s) is the per-key storm guard.** A bulk import writing 200 records
  produces at most two sends per key per minute instead of 200. The cost is a count that lags by up
  to 30 seconds; the next write corrects it, and the final write of any burst is the one that
  matters.
- **`AIRCRAFT_HOURLY_CEILING` bounds the aircraft**, not the key (PRD §9.4, §12) —
  `feature/stresstest` is compiled into every build and *will* be pointed at a shared aircraft. Past
  the cap, send one "N4589T · a lot of activity" and stop. It is checked at **step 3, before any
  write**, so a tripped ceiling costs one read and nothing else.
- **Audience and preferences are re-derived on every send**, which is what PRD §9.5 asks for. With
  no buffered fan-out there is no cached audience that could outlive a revocation, so §9.5 stops
  being a rule to enforce and becomes a property of the shape.

#### No transaction — and that is a consequence of §7.3, not an oversight

Step 4 is a plain read followed by a merge write. An earlier draft wrapped it in a transaction, which
would have been the reflex: two concurrent writes to the same aircraft race on `changeCount`.

They do, and every race is benign:

| Race | Outcome |
|:--|:--|
| Two writes both read `changeCount = 5` | `FieldValue.increment(1)` is applied server-side against the *stored* value, not the read one, so the result is 7. The read is only used for the two boolean decisions below. |
| Both decide `newSession` | Both stamp `firstWriteAt` within milliseconds of each other → effectively the same notification id → the second replaces the first in the tray |
| Both decide the throttle has expired | Two sends under the same id and `collapse_key` → the tray shows one entry, and an offline device receives one message |

**Duplicate sends collapse, so the counter does not need transactional exactness.** That is the
replacement design paying for itself a second time: the mechanism that makes bursts readable also
makes the write path lock-free. A transaction here would buy nothing and add a retry loop on the
hottest document in the feature.

#### The hot-document limit, stated plainly

`notification_activity` is one document per `(aircraft, recordType, actor)`, and Firestore's sustained
single-document write rate is roughly **1/sec**. A stress-test import of 200 task records on one
aircraft by one actor is 200 writes to one key in a few seconds, well past that.

What actually happens: latency climbs and some invocations get contention errors, which the trigger's
own retry handles. Nothing is lost — a dropped increment costs one unit of a count that the next
write corrects. `AIRCRAFT_HOURLY_CEILING` does **not** rescue this case, and the doc should not
pretend otherwise: sends are throttled to ~2/min/key, so a 10-second burst never accumulates enough
*sends* to trip an hourly *send* ceiling. The ceiling protects against sustained abuse over minutes
and hours; it does not protect against one fast burst.

Two escapes if dogfooding or the stress test shows this matters, neither built in V1:

- **Cap the count.** Stop incrementing at 99 and render "99+ changes". Bounds writes per session to
  99 regardless of burst size, and nobody reads the difference between 140 and 99+.
- **Shard the counter** across N sub-documents summed at send time — the standard Firestore
  distributed-counter pattern. More correct, more machinery.

The cap is almost certainly the right one if it comes up: this is a notification body, not an
accounting ledger.

Rules: `match /notification_activity/{id}` and `match /notification_rate/{id}` are both
`allow read, write: if false` — functions only. Doc ids concatenate with `__`; Firebase uids and
aircraft ids are alphanumeric and `recordType` is a fixed enum, so the separator is unambiguous, and
the leading uid also keeps the id clear of Firestore's reserved `__.*__` form.

**Delivery is now at-least-once by construction.** The old sweep needed a claim-then-send-then-delete
dance with a reclaim window, because delete-then-send loses a batch on a crash. Here a crashed
invocation just means one write went unannounced, and the next write re-sends with a corrected count.
Nothing to claim, nothing to reclaim.

### 7.5 The escalation bypass

A write that raises a squawk's priority sends **its own body — not the §6.5 one** — and critically,
**under its own notification id**, `n1esc:{aircraftId}:{squawkId}`, never
`n1:{aircraft}:{recordType}:{actor}`.

**The body names the actor, and that is what makes the duplicate survivable.** Both paths fire for
one squawk: this push within seconds of the write, and N2 at the recipient's next scan, since the
push never touches the local watermark. They post under different ids, so they stack. Deduplicating
them by reusing N2's id was considered and rejected — the two notifications are not the same news.
Only the server knows *who* raised the squawk, and a device-local scanner never can:

| | body |
|:--|:--|
| N2, on the device | `N4589T: priority raised from High to AOG` |
| N1, from the server | `N4589T: Dave Chen raised the priority of 1 squawk issue` |

Word-for-word identical copy is what would have made the second arrival read as a duplicate rather
than as the thing the recipient actually wants to know. So N1 carries
`notification_n1_body_squawk_raised` / `..._created`, which no N2 body can collide with because no N2
body has an actor to name.

The server also distinguishes **created** from **raised**, which the scanner cannot: only the trigger
sees the before/after pair. A squawk created straight at HIGH or AOG takes its own title
(`notification_n1_title_squawk_created`) — "Priority raised" would contradict a body saying it was
just created. AOG is not its own headline (decided 2026-08-26): it uses the same created/raised
titles HIGH does. A reopen counts as *raised*: the record was already there.

That distinction is the whole rule now. With §7.3's replacement scheme, folding an escalation into
the activity id would let the *next* routine edit overwrite "Sarah raised Left brake dragging to
AOG" with "Sarah made 4 changes to squawks" — silently replacing an escalation alert with a shrug. A
separate id makes the escalation notification immune to collapse, and it is also exempt from
`MIN_REPOST_INTERVAL` and from the per-aircraft ceiling.

PRD §5.4 called the bypass a hard rule rather than a tuning knob, in the buffering design. It is
still a hard rule; it just changes from "do not delay this" to "do not overwrite this."

The trigger detects it by decoding `before.payload` and `after.payload` and comparing
`squawk.priority` — the only ladder the server can evaluate. **Task due-status escalation is
undetectable server-side** (no write happens when a date passes, and the rule engine lives on the
device), which is exactly why N2 exists and is not a gap.

### 7.6 Message payload

Data-only FCM messages, with the client constructing the visible notification. Notification-type
messages are displayed by the OS on Android when backgrounded, which would bypass the per-channel
routing and the tap router:

```json
{
  "data": {
    "class": "collaboration",
    "channel": "COLLABORATION",
    "aircraftId": "…", "recordType": "squawk", "recordId": "…",
    "titleKey": "notification_n1_title", "bodyKey": "notification_n1_body_plural",
    "tailNumber": "N4589T", "actorName": "Dave Chen", "changeCount": "5",
    "notificationId": "n1:{aircraftId}:{recordType}:{actorUid}:{sessionSeq}",
    "tapTarget": "aircraft:{aircraftId}:squawks",
    "recipientUid": "{uid this copy is addressed to}"
  },
  "android": { "collapse_key": "<same as notificationId>" },
  "apns":    { "headers": { "apns-collapse-id": "<same as notificationId>" } }
}
```

`notificationId` is what `LocalNotifier` posts under, so the tray replacement in §7.3 happens on the
client; the two collapse headers make the *transport* do the same thing for a device that was offline
during the burst. All three carry the same value — the server computes it once.

Localization is the reason for string **keys** rather than rendered text: the server does not know
the recipient's locale, and the client already has `strings.xml`.

**Named values, not a positional `bodyArgs` array**, which an earlier draft of this section specified
and which cannot work. Two reasons, either one sufficient:

- `notification_n1_title` is `%1$s · %2$s`, and its second argument is a **localized section label**
  ("Squawks" / "Tasks" / "Logbook"). The server cannot render that at all. It sends `recordType` and
  the client resolves both the title-case and lower-case labels from it.
- `notification_n1_body_single` (`%1$s made a change to %2$s`) and `..._body_plural`
  (`%1$s made %2$d changes to %3$s`) do not share an argument order, so one array would mean the
  server encoding per-string placeholder order it has no way to verify.

So the message names the resources and supplies the *variable* values by name — `tailNumber`,
`actorName`, `changeCount`, plus `recordTitle` on the escalation path — and the client assembles
them. `bodyKey` is carried alongside `titleKey` for the same reason: single-versus-plural cannot be
chosen client-side without it. An empty `actorName` means "fall back to
`notification_n1_actor_fallback`", which is itself a localized string.

The cost is that argument order lives in the client, on three platforms rather than in one server
file. That is where `strings.xml` lives, so it is the right home, but it is a real coupling and the
table in `pushMessages.ts` is the only place the contract is written down.

**`recipientUid` addresses the copy, and the client drops anything not meant for it** (P4.13). An
FCM token belongs to the app *install*, not to an account, while `push_devices` is keyed by install
id under `users/{uid}/`. A sign-out whose registry delete does not land — offline, or through a path
that never calls it — leaves the previous account holding a document with a live token, and nothing
prunes it, because `pruneDeadTokens` only fires on a token FCM reports as gone. Without the address,
that account's notification text keeps arriving at a device somebody else is now using.

On the send side, every sign-out goes through `SignOutCoordinator`, which clears this device's
registration before `authManager.logOut()` and bounds the attempt. It is one shared call rather than
a sequence each caller repeats, because it was already wrong in the second place that tried:
corruption recovery signed out directly and cleared nothing (#550). The clear is best-effort by
nature — offline it cannot land, and no sign-out at all happens on a shared device nobody signs out
of — which is why the receive-side check exists rather than being redundant with it.

Because one message addresses a whole fan-out, the field is stamped per recipient at send time
rather than built into the payload: `sendPush` groups targets by uid and sends one multicast per
recipient. An **absent** `recipientUid` means "a server older than this field" and must still render,
so a client newer than the server does not go silent during a rollout.

iOS needs `content-available` plus a notification service extension to render a data-only message
while backgrounded; that is part of P5, and until it lands iOS may ship rendered strings with a
TODO. The extension needs the same `recipientUid` drop — `PushPayload.isAddressedTo` is in
`commonMain` for exactly that reason, though the drop itself is per-platform.

---

## 8. N1 on web — the sync-driven detector

### 8.1 Why this works without any backend

An open tab already runs the same entity sync engine every other platform runs, so it already
receives a collaborator's write the instant Firestore delivers it. `RemoteEntity.writerUid`
(`PullListener.kt`) carries rules-enforced authorship on the envelope. An incoming record whose
`writerUid` is not the signed-in user, on an aircraft where `SharingManager.observeIsShared(acId)` is
true, **is** an N1 event — the same test the server-side trigger applies, run locally.

### 8.2 Where the hook goes

`feature/sync/data` must not depend on `feature/notifications` (§3). So `core/storage` owns a
one-method listener interface, and the sync engine calls it:

```kotlin
// core/storage
fun interface ForeignWriteListener {
  fun onForeignWrite(kind: CollectionKind, scope: EntityScope, id: String, writerUid: String)
}
```

`PullListener` invokes it (no-op binding by default) when it applies a remote write whose `writerUid`
differs from the signed-in uid. `engine`'s `jsMain` source set binds the real one — the detector is a
decision about whether an event deserves a notification, so it sits with the scanner, not with the
notifier it eventually calls. This is
the `CloudSyncSetting` pattern: interface in `core:storage`, implementation supplied by a feature and
bound via Koin.

Deliberately a `jsMain`-only binding. Android and iOS get N1 from push, and running both paths would
double-notify.

### 8.3 Actor name

A **one-shot** `SharingManager.observeShareState(acId).first()` at the moment a foreign write is
detected, not a standing subscription. A per-aircraft listener would need its own lifecycle — opened
when the user has any shared aircraft, torn down on sign-out or when a share ends, kept from leaking
across account switches — for a value needed only at the instant a notification fires. One read per
notification, mirroring what the server's fan-out does. Empty result falls back to "A collaborator."

### 8.4 Coalescing on web

No server trigger sees this path, so web does its own counting — but §7.3's move from buffering to
replacement shrinks that to almost nothing. An `ActivityCounter` in `engine/commonMain`, keyed by
`(aircraftId, recordType, actorUid)`, in-memory and per-tab, holding `changeCount` / `firstWriteAt` /
`lastWriteAt` / `lastSentAt` and applying the same `ACTIVITY_WINDOW` and `MIN_REPOST_INTERVAL` as
§7.4 — including rolling `firstWriteAt` on session end, since the `tag` embeds it just as the native
ids do. No `notification_rate` equivalent and no hot-document concern: the counter is a field in a
tab's memory, and a tab only ever sees writes the sync engine delivered to it. No timers,
because there are none to mirror any more: an earlier draft had this class reproducing the server's
5-minute quiet timer and 30-minute ceiling per tab, with its own lifecycle to get wrong.

**Web's replacement primitive is the `tag` option, and its default is the behaviour the other two
platforms have to ask for:**

```js
new Notification("N4589T · Tasks", {
  tag: "n1:{aircraftId}:{recordType}:{actorUid}:{sessionStart}",  // same tag replaces, never stacks
  body: "Dave Chen made 5 changes to tasks",
  // renotify: false while the notification is still on screen, so an update replaces it quietly.
  // true once it has been dismissed — otherwise the tag replaces something that is not there and
  // the rest of the session is silent. WebLocalNotifier reads that from its own `live` map.
})
```

Android needs `setOnlyAlertOnce(true)` and iOS a passive interruption level to avoid re-buzzing on
each update; on web that is simply what happens.

Two web-specific limits, both narrowing rather than breaking it:

- **Chrome on Android rejects the `new Notification()` constructor outright** — it throws, and only
  `ServiceWorkerRegistration.showNotification()` works there. §8.5 already scopes V1 web to an open
  tab with no service worker, so mobile web is out of scope regardless; when P6 adds the service
  worker, `showNotification()` takes the same `tag` and this section is unchanged.
- **A page-created notification may auto-dismiss** (Chrome closes them after ~20s; macOS moves them
  to Notification Center). So "the entry updates in place" is best-effort visually on web — if the
  first is already gone, the replacement just appears as new. It still never *stacks*, which is the
  property that matters.

**`collapse_key` does not apply here.** Collapsing undelivered messages is an FCM feature and web V1
has no push transport (§8.5), so web gets the dedup half of §7.3 and not the offline half. A tab that
was closed during the burst sees nothing at all, which is the existing V1 limitation, not a new one.

### 8.5 What this does not cover

A closed or fully-suspended tab runs no JavaScript. Closed-tab delivery needs the real web-push stack
(service worker, VAPID, a `push_devices` entry) and stays in V1.1 / P6, at which point
`AppCapability.isPushSupported` flips true on `jsMain`.

---

## 9. Settings UI

### 9.1 Entry point

A `SettingsRow` in `feature/settings/SettingsScreen.kt` with the account-level rows, navigating to
`Screen.Notifications` (new, `"notifications"` in `core/nav/Screen.kt`), registered in
`ShellNavGraph` beside `Screen.SyncSettings`. Live subtitle: "Collaboration and urgency alerts" /
"Off — turn on to hear about changes" / "Blocked in system settings".

### 9.2 ViewModel

```kotlin
data class NotificationSettingsUiState(
  val settings: NotificationSettings = NotificationSettings(),
  val permission: PermissionState = PermissionState.UNDETERMINED,
  val canOpenSystemSettings: Boolean = false,
  val isSignedIn: Boolean = false,        // real account, not anonymous
  val isCloudSyncEnabled: Boolean = false,
  val isLoading: Boolean = true,
)
```

`combine` over `prefsManager.observe()`, `permission.observe()`, `auth.authStateChanged`, and
`syncPreferences.state`. `isLoading` is true in **two** distinct situations, and both must disable
the toggles rather than merely dim them:

- the `stateIn` seed before `combine` has emitted — the #451 case verbatim, and the reason
  `isLoading` defaults to `true`;
- `PrefsState.Unresolved` (§4.3) — preferences exist but this device has not read them yet.

The second is the one with teeth. #451 was a cosmetic flash: a returning Pro subscriber briefly saw
the paywall. Here an *editable* control rendered against a guessed value writes that guess back as a
whole-message overwrite, reverting the user's real settings on every other device (§4.3, consequence
2). Disabling the toggles while `isLoading` is what makes that unreachable; the spinner is just the
visible part.

Toggle rows read positive names like `state.settings.squawkPriorityEnabled` and write through
mutators like `prefsManager.update { it.withSquawkPriority(enabled) }` (§4.1) — the screen never sees
an inverted field name, and never constructs a `NotificationSettings` of its own.

Any in-progress edit lives in the ViewModel's `StateFlow`, never in composable `remember` — this
screen can be torn down by the OS permission dialog.

### 9.3 States the screen must be honest about

| State | Affects | UI |
|:--|:--|:--|
| Permission `UNDETERMINED` | Both groups | Toggles active; flipping the master on triggers the OS prompt inline |
| Permission `DENIED` | Both groups | Persistent **neutral** banner + "Open settings". Toggles stay editable so choices survive fixing the permission. |
| Permission `UNSUPPORTED` | Both groups | Persistent **neutral** banner, **no** "Open settings" — there is nothing to open. Copy says the browser does not support notifications, not that they are blocked; conflating the two sends a pilot hunting through a settings page that will never fix it. Toggles stay editable, same reasoning as `DENIED`: a signed-in-elsewhere account still wants its preference recorded even where this device cannot act on it. |
| Signed out / anonymous | Collaboration only | Footer with a "Sign in" action. **The urgency group stays fully live and is not dimmed.** |
| Cloud sync off | Collaboration only | Footer with "Turn on sync" → Backup & Sync. Urgency unaffected. |
| Web | Denied banner | Drops the button (`canOpenSystemSettings == false`) and names where to look, phrased generically since the path differs by browser. `UNSUPPORTED` on web reuses this row's copy path but with the not-supported wording above, not the blocked one. |

Dimming the urgency group for a signed-out user would be a straightforward bug: it works fine for
them, and they are the users for whom it matters most (§6.8).

The banner is **informational, not an error** — no red, no destructive iconography. Notifications are
a convenience on top of a logbook that works without them. Neutral surface color, plain sentence.
Read `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` before building it; the aviation
palette is required and dynamic color is disabled.

Copy conventions: all strings from `strings.xml`, reuse before adding, apostrophes literal, and edit
actions worded as "Update X" rather than "Edit X".

### 9.4 Disabling AOG (Q5) — reversed

The PRD's Q5 (a confirm-gated toggle so AOG alerts couldn't be silenced quietly) was implemented in
P2, then reversed on 2026-08-26: AOG is not its own settings toggle. It reports through
`squawk_priority_disabled` — "Squawk priority increases" — like any other escalation, with no confirm
dialog. There is no longer a way to silence *only* AOG while keeping other priority escalations on.

---

## 10. Onboarding primer

### 10.1 Where it lives

| Piece | Location |
|:--|:--|
| The step | `feature/login/src/commonMain/.../feature/login/AuthFlow.kt` — a new `AuthStep.NotificationPrimer` |
| The screen | `feature/login/src/commonMain/.../feature/login/onboarding/NotificationPrimerScreen.kt`, beside `AdsConsentExplainerScreen.kt` |
| Its strings | `feature/login/src/commonMain/composeResources/values/strings.xml`, as `onboarding_notifications_*`, matching the existing `onboarding_ads_consent_*` set. The Continue label is already shared — `core:sharedassets`' `continue_action`. |
| Build dep | `feature/login/build.gradle.kts` gains `implementation(project(":feature:notifications:permission"))` |

**In `feature/login`, not in `feature/notifications`.** `AuthFlow` is deliberately navigation-free —
a `when (step)` that renders its screens directly — so a step owned by another module would have to
arrive as an injected composable slot, which is machinery bought for nothing. Onboarding screens
belong to onboarding; `AdsConsentExplainerScreen` lives here rather than in `feature/ads` for the
same reason.

The dependency that follows is the one the ads step already declares, and the new line should carry
the same kind of comment explaining what it is for:

```kotlin
// The ads-consent priming step: showsAds() (tier gate) + AdConsentManager (background
// isConsentRequired() check, then presentConsentForm() from the explainer's Continue).
implementation(project(":feature:ads:datamanager"))
// The notification priming step: NotificationPermission (background UNDETERMINED check, then
// request() — the real OS dialog — from the primer's Continue).
implementation(project(":feature:notifications:permission"))
```

**`:permission` and nothing else — this is the case the §3 split was for.** The primer needs exactly
one interface, `NotificationPermission`. In the single-`datamanager` shape that interface sat beside
the scanner, so an onboarding card would have dragged `feature:tasks:datamanager`,
`feature:logs:datamanager`, `feature:squawk:datamanager`, `feature:fleet:datamanager` and
`feature:sharing:datamanager` onto `feature/login`'s compile classpath. Now it takes the feature's
leaf module, whose own dependencies are two `core:*` entries (§5.1).

Not `:sharedassets` either — `PermissionBanner` and `NotificationClassRow` are settings-screen
furniture, and the primer is a full-bleed explainer card that shares no component with them, exactly
as `AdsConsentExplainerScreen` shares nothing with the ads feature's own UI.

### 10.2 The screen

Stateless, one parameter, mirroring `AdsConsentExplainerScreen`'s signature exactly:

```kotlin
@Composable
fun NotificationPrimerScreen(onContinue: () -> Unit)
```

The screen has no opinion about what Continue does — it does not touch `NotificationPermission`, and
it does not know whether a dialog follows. `AuthFlow` owns that, which is what keeps the screen
previewable and keeps the permission call in one place. Copy is plain-language and concrete: hear
about changes on shared aircraft, and when something goes overdue.

### 10.3 The wiring

`AuthFlow` takes the permission interface as a `koinInject()` default parameter, alongside
`adConsentManager` and `subscriptionManager`:

```kotlin
@Composable
fun AuthFlow(
  onComplete: () -> Unit,
  …
  adConsentManager: AdConsentManager = koinInject(),
  notificationPermission: NotificationPermission = koinInject(),
) {
```

```kotlin
private enum class AuthStep { Login, EmailSignIn, NameEntry, Welcome, NotificationPrimer, AdsConsentExplainer }

suspend fun proceedPastOnboarding() {
  notificationPermission.refresh()
  if (notificationPermission.observe().value == PermissionState.UNDETERMINED) {
    step = AuthStep.NotificationPrimer
    return
  }
  proceedPastNotifications()
}
```

**The equality check is what keeps `UNSUPPORTED` out of the primer, and that has to stay an equality
check.** A device that structurally cannot show notifications has nothing to prime — showing the
card and then watching `request()` no-op back to `UNSUPPORTED` would be a dead end with a Continue
button. Testing `!= GRANTED && != DENIED` instead would show the primer here by accident; the
literal `== UNDETERMINED` is deliberate, not an oversight to "simplify" later.

```kotlin

suspend fun proceedPastNotifications() {
  val needsAdsConsent = subscriptionManager.shouldShowAds().first() && adConsentManager.isConsentRequired()
  if (needsAdsConsent) step = AuthStep.AdsConsentExplainer else onComplete()
}
```

and in the `when (step)`, immediately before the `AdsConsentExplainer` arm:

```kotlin
AuthStep.NotificationPrimer -> NotificationPrimerScreen(
  onContinue = {
    scope.launch {
      notificationPermission.request()   // the real OS dialog; its result is not branched on
      proceedPastNotifications()
    }
  },
)
```

Three details that matter:

- **`refresh()` before the check** (§5.1). `observe()` is a `StateFlow` whose value can be stale —
  the user may have changed the setting in the OS since the app last looked — and this is the one
  read that decides whether a screen appears at all.
- **The result of `request()` is deliberately not branched on.** §10.4's "not a blocking gate": a
  denial proceeds exactly as a grant does, and is handled later by §9.3's settings banner.
- **`proceedPastNotifications()` is a real split, not a rename.** `proceedPastOnboarding()` has two
  call sites — `onLoginSuccess`'s already-onboarded branch and `WelcomeScreen.onDone` — and both must
  keep entering at the top. The primer's Continue is the one path that must re-enter *below* the
  notification check; routing it back through `proceedPastOnboarding()` would re-read a permission
  state the OS may not have committed yet and show the primer again.

**No preference flag of its own**, exactly like the ads-consent detour: it reads the OS permission
state the way `AdConsentManager.isConsentRequired()` reads the CMP's cached state, and renders only
when `UNDETERMINED`. That is what makes it safe to insert unconditionally — `proceedPastOnboarding()`
runs for new and returning users alike, and the state check makes it a no-op for anyone already
resolved.

**Not a blocking gate.** Declining proceeds exactly as granting does; the denial is handled by §9.3's
banner, not by re-litigating during onboarding.

**Existing installs never see it** — an already-signed-in user who upgrades mid-session does not
re-run `AuthFlow`. For that population the first ask stays contextual: the master toggle, or right
after creating or accepting a share invite.

---

## 11. Developer Options

Gated on `AppCapability.isDeveloperOptionsSupported`, in a `NotificationDeveloperSettings` section
following `DisplayAdsDeveloperSettings`:

- **Send test notification** — one action per channel, so channel routing and the high-priority path
  can be verified without a second account and a real AOG squawk.
- **Run urgency scan now** — a feature whose normal cadence is once a day is untestable without it.
- **Reset urgency watermarks** — re-arms every crossing, which is the only way to exercise the
  seeding rules repeatedly on one device.
- **Show scan diagnostics** — last scan time, trigger, records examined, crossings found, crossings
  suppressed by preferences. This is what makes the background-versus-foreground metric (§6.6)
  debuggable rather than merely reportable.

### 11.1 Getting the section onto the screen without a compile dependency

> **Shipped.** PRs #511 and #512 built this ahead of the notification work, since it only touches
> existing modules. What follows is the mechanism as it exists, not a proposal.

Three of these four actions need `engine`, and `DeveloperOptionsScreen` lives in `feature:settings`,
which §3 keeps off `engine` deliberately. The escape hatch used to be a composable slot the shell
filled — and it was **singular and already occupied** by the stress-test extra, so a second section
forced a signature change either way. Two interfaces replaced it, both resolved from Koin, both
living in **`feature/developeroptions/plugin`**:

```kotlin
/** The row. */
interface DeveloperOptionsExtra {
  val order: Int                                    // ascending; fixed numbers, not Koin order
  fun isAvailable(): Boolean = true                 // where the capability gate lives
  @Composable fun Content(onNavigate: (route: String) -> Unit)
}

/** The page the row opens. */
interface DeveloperOptionsNavContributor {
  fun isAvailable(): Boolean = true
  fun register(builder: NavGraphBuilder, navController: NavController)
}
```

`DeveloperOptionsScreen` renders `getAll<DeveloperOptionsExtra>()` sorted and filtered;
`ShellNavGraph.settingsDetailRoutes` lets each `DeveloperOptionsNavContributor` register its own
destinations. **Both halves are needed.** The first alone drops the shell's dependency on a
developer feature's *section* while leaving it importing that feature's *screen* — which is what
`registerStressTestRoutes` was.

Three decisions worth keeping:

- **`feature/developeroptions/plugin`, not `core:ui`.** An earlier draft put the interface in
  `core:ui`. It carries a `@Composable` and a `NavGraphBuilder`, and `core:ui` is depended on by half
  the app — a developer-options concept does not belong there. Its own module rather than
  `:datamanager` because a datamanager must not carry Compose; the same split, for the same reason,
  as `core:lifecycle` / `core:lifecycle:compose`.
- **`onNavigate: (route: String) -> Unit` rather than a `NavController`** on `Content`, so the row
  half needs no navigation dependency.
- **`KoinPlatform.getKoin()` in `settingsDetailRoutes`.** It is a `NavGraphBuilder` extension, not a
  `@Composable`, so `org.koin.compose.getKoin()` is unavailable. `MainViewController.kt` already
  reaches Koin this way from non-composable code.

What it bought, measured after #512:

| | Before | After |
|:--|:--|:--|
| `feature:shell` → `feature:stresstest:config` | dependency | gone |
| `feature:shell` → `feature:developeroptions:plugin` | — | added, **interface only** |
| `isStressTestSupported` threading | `AppEntry` → `AdaptiveShellRoute` → `SettingsSection` → `settingsDetailRoutes`, plus `shellGraph` and `WebApp` | gone from all of them |
| Product routes | static in `ShellNavGraph` | unchanged |

The shell is not dependency-free — it swapped a dependency on a *feature* for one on an *interface*
that carries no screens and no `NavHost`. That is the win, and it is what lets `engine` and
`devoptions` stay off `feature:shell` when P2.11 lands.

**Scoped on purpose.** The contributor is named `DeveloperOptionsNavContributor`, not
`NavGraphContributor`. The shell's fan-out to *product* feature modules is intentional — its build
file calls that its "aggregator role for composables/nav that `core:di` plays for Koin modules" — and
inverting all of it is a much larger question. This solves the narrow case: a developer-only,
capability-gated feature has no business in the central graph. If it earns generalizing, it moves to
a `core:nav:plugin` sibling.

**Known limitation.** Contributed routes are not in `core:nav`'s `Screen`, so they stay outside that
index — as `STRESS_TEST_ROUTE` already was. Not made worse; moving those constants is a separate
change.

### 11.2 What the notification section contributes

`NotificationDeveloperOptionsExtra` (the row) and, for the scan-diagnostics page, a
`DeveloperOptionsNavContributor` — both in `feature/notifications/devoptions`, its own module so that
`engine` stays free of Compose and `settings` stays free of `engine`. `feature:shell` gains nothing.

---

## 12. Security, privacy, rules

### 12.1 Rules changes

| Path | Change |
|:--|:--|
| `users/{uid}/push_devices/{id}` | **None needed** — covered by the own-tree rule. Add a comment naming it. |
| `users/{uid}/notification_settings/main` | **None needed** — same. |
| `notification_activity/{id}` | New: `allow read, write: if false;` (functions only) |
| `notification_rate/{id}` | New: `allow read, write: if false;` (functions only) |

Add emulator tests to the existing vitest suite in `backend/firebase/functions` covering: another
user cannot read my `push_devices`; no client can read or write `notification_activity` or
`notification_rate`; a revoked
member's uid is absent from `memberRoles` and therefore from the audience.

### 12.2 Actor suppression

From `writerUid` on the envelope, never from anything client-supplied. Server-side that is
`after.writerUid`; client-side (web N1, and the §6.4 seeding refinement) it is
`StorageEntity.writerUid` / `RemoteEntity.writerUid`, which the sync engine carries down from the
same rules-enforced field.

### 12.3 Privacy

- **N1 bodies carry user content** — tail numbers, squawk titles, collaborator names — to Apple and
  Google push infrastructure. This is a new trust boundary: Firestore content stays within Firebase
  today. It belongs in the privacy policy, and it is why bodies stay short and carry no attachment or
  certificate data.
- **N2 crosses no trust boundary at all.** Local data in, local notification out. Worth stating
  explicitly, because "the app told me my annual is overdue" otherwise reads like a server watching
  the user's records — and for an anonymous, sync-off pilot nothing about their fleet has ever left
  the device.
- **N2 telemetry is the one place this touches the network for such a user** (PRD §11). The safe
  default, and this design's recommendation: **the scan reports nothing when cloud sync is off or the
  account is anonymous.** The metric loses exactly the population whose privacy expectation is
  strongest, and keeps the claim in the paragraph above literally true.
- **Log-level discipline.** Cross-account ids (recipient uids, actor uids) are redacted at info and
  above; debug and verbose may keep them.

---

## 13. Testing

`src/test/kotlin`, JUnit 4 + MockK + Truth + `kotlinx-coroutines-test`, per the house convention.

**`UrgencyRank` (pure, exhaustive).** Every `DueStatus` and every `SquawkPriority` × `SquawkStatus`.
The one that must exist by name: `complied_ranksBelowDueSoon_despiteHigherOrdinal`.

**`UrgencyScannerTest`** — fake stores, fake `Clock`, in-memory watermarks:

- a crossing notifies once and stays silent on the next scan
- `OVERDUE → COMPLIED` is silent **and** lowers the watermark, so coming due again notifies again
- a task that went overdue and was complied while the device was dark produces nothing
- first sight of an aircraft seeds silently, however many records are already overdue
- a new record written by *this* user seeds at its rank; a new record written by *another* user seeds
  at 0 and reports if urgent (§6.4)
- a dismissed squawk reopened at HIGH notifies
- three tasks crossing at once produce one summary, not three
- a preference toggled off suppresses its tier but still advances the watermark — so turning it back
  on does not replay history
- **`PrefsState.Unresolved` skips the scan entirely**: nothing notified, nothing seeded, and the
  watermarks are byte-for-byte unchanged (§4.3). Assert the last one — an implementation that
  advances watermarks and only suppresses the notification passes a weaker test and permanently
  swallows the crossing.
- an unhydrated scope is not pruned
- concurrent scans do not double-report

**`NotificationSettingsExtTest`** — one assertion per extension, that a default-constructed
`NotificationSettings()` reads every class as **on**. Small, because there is no mapping to
round-trip (§4.1): the extensions derive from the proto rather than copying it, so the only thing
that can be wrong is an inverted `!`. `absentDoc_resolvesToAllOn` is the case to name explicitly — it
is the property the whole convention exists for, and the contract the server-side trigger
independently relies on.

**`NotificationPrefsManagerTest`** — the resolution rule (§4.3), which is where copying
`DeveloperOptionsManagerImpl` would have gone wrong:

- signed out → `Resolved(defaults)` immediately, never `Unresolved`
- cloud sync off → `Resolved` immediately, even with no row and no cursor (otherwise it hangs forever)
- signed in, sync on, no row, cursor not hydrated → `Unresolved`
- …then the row lands → `Resolved(it)`
- …or the cursor flips to `hydrated` with still no row → `Resolved(defaults)`
- …or neither happens → `Resolved(defaults)` after `PREFS_HYDRATION_TIMEOUT`
- `update()` while `Unresolved` fails and **writes nothing** — assert on the store, not just the
  `Result`. This is the test that would have caught consequence 2.
- `update()` while `Resolved` copies onto the resolved value, leaving untouched fields intact

**Backend** (emulator + vitest, per the existing harness): an unshared aircraft sends nothing and
writes no counter; the actor is excluded from the audience; a second write bumps `changeCount` to 2
and re-sends under the **same** notification id and `collapse_key`; a write after `ACTIVITY_WINDOW`
resets the count to 1 **and rolls the notification id**, so the previous session's entry is not
overwritten — assert on the id, since asserting only on the count passes for the broken version; two writes inside
`MIN_REPOST_INTERVAL` produce one send; a priority escalation posts under `n1esc:…` and is exempt
from both the throttle and the ceiling; a member revoked between two writes is absent from the
second send's audience; a tripped `AIRCRAFT_HOURLY_CEILING` short-circuits at step 3 and writes
**no** counter doc at all; two concurrent writes leave `changeCount = 2`, not 1 — the lock-free
`increment` path is the one a transaction-shaped test would silently pass.

The one to write first, because it is the whole §7.3 argument in a test: **Dave's five edits on
aircraft A interleaved with three on B produce exactly two distinct notification ids**, with final
counts 5 and 3.

**Not unit-tested, verify by hand:** `WorkManager` and `BGTaskScheduler` actually firing, OS
permission dialogs, channel importance, tray rendering, and cold-start tap routing.

---

## 14. Implementation order

Maps onto the PRD's phases, with a P0 added for prerequisites that touch existing code and can land
before any notification module exists. **P0–P3 touch no backend at all.**

| Phase | Theme | Exit criteria |
|:--|:--|:--|
| **P0** ✅ | Developer Options plumbing (existing modules only) | A second Developer Options section — row *and* page — can be added by a Koin binding alone |
| **P1** | Foundations — modules, proto, preferences, permission, notifier, settings screen | A dev build requests permission and posts on each channel; preferences persist and reach a second device; a fresh install of an account with non-default preferences shows the spinner, never all-on |
| **P2** | N2 urgency, Android + iOS | Crossings fire exactly once; de-escalations silent; a fresh install notifies nothing; **no backend change was required** |
| **P3** | Web N1, open tab | A web user with a shared aircraft open sees a collaborator's edit from another account; **no backend, no token registry** |
| **P4** | N1 backend + Android | Two accounts sharing an aircraft see each other's changes; neither sees their own; **no Cloud Scheduler job, no Cloud Tasks queue** |
| **P5** | N1 on iOS | Parity with Android; `timeSensitive` works for AOG |
| **P6** | Web push (V1.1) | `isPushSupported` flips true on `jsMain` |

Dogfood across P2–P5. P2 tunes the scan cadence and per-tier batching; P4 tunes `ACTIVITY_WINDOW`
and `MIN_REPOST_INTERVAL`. The noise floor has to be felt rather than reasoned about.

### 14.1 Task breakdown

Tracked on **[GitHub Project #9 — Notifications](https://github.com/users/fz172/projects/9)**, one
epic per phase with these tasks as sub-issues: [#454 P0](https://github.com/fz172/squawkit/issues/454)
· [#455 P1](https://github.com/fz172/squawkit/issues/455)
· [#456 P2](https://github.com/fz172/squawkit/issues/456)
· [#457 P3](https://github.com/fz172/squawkit/issues/457)
· [#458 P4](https://github.com/fz172/squawkit/issues/458)
· [#459 P5](https://github.com/fz172/squawkit/issues/459)
· [#460 P6](https://github.com/fz172/squawkit/issues/460).

Issue-sized below. "Blocks on" names the immediate prerequisite only.

**P0 — Developer Options plumbing.** ✅ **Complete** (#511, #512). Independent of the rest; built first because it only touches existing modules.

| # | Task | Touches | Status |
|:--|:--|:--|:--|
| P0.1 | `DeveloperOptionsExtra` (`order`, `isAvailable()`, `Content(onNavigate)`); `DeveloperOptionsScreen` resolves `getAll()` sorted | new `feature/developeroptions/plugin`, `feature/settings` | ✅ #511 |
| P0.2 | `StressTestDeveloperOptionsExtra` onto the interface, contributed by `stressTestKoinModules()`; `dogfoodContent` deleted | `feature/stresstest/config`, `feature/settings` | ✅ #511 |
| P0.3 | `DeveloperOptionsNavContributor` for the *pages* the rows open; shell drops `feature:stresstest:config` and `isStressTestSupported` entirely | `feature/developeroptions/plugin`, `feature/stresstest/config`, `feature/shell`, both hosts | ✅ #512 |

**P1 — Foundations.**

| # | Task | Touches | Blocks on |
|:--|:--|:--|:--|
| P1.1 | Scaffold the eight modules (§3) with `build.gradle.kts` each, registered in `settings.gradle.kts` | `feature/notifications/*` | — |
| P1.2 | `settings/notification_settings.proto` (inverted fields, §4.1) + `CollectionKind.NotificationSettings` at all five registration points (§4.2) | `core/model`, `core/storage`, `feature/sync/data` | P1.1 |
| P1.3 | `NotificationSettingsExt` — the inversion in one file — plus `NotificationSettingsExtTest` | `:model` | P1.2 |
| P1.4 | `NotificationPermission` + `PermissionState` (four values, incl. `UNSUPPORTED`) and the three actuals (§5.1) | `:permission` | P1.1 |
| P1.5 | `LocalNotifier`, `PendingNotification`, `NotificationChannel` + three actuals + channel registration (§5.2) | `:viewing` | P1.1 |
| P1.6 | `NotificationPrefsManager` with the `PrefsState` resolution rule (§4.3) + `NotificationPrefsManagerTest` | `:datamanager` | P1.3 |
| P1.7 | Six Koin modules + `CommonAppModules` wiring + host platform init | `core/di`, hosts | P1.4, P1.5, P1.6 |
| P1.8 | `Screen.Notifications`, `ShellNavGraph` route, and the `SettingsRow` with its live subtitle (§9.1) | `core/nav`, `feature/shell`, `feature/settings` | P1.1 |
| P1.9 | Settings screen + ViewModel: `isLoading` disables toggles, precondition footers, AOG confirm (§9.2–9.4) | `:settings`, `:sharedassets` | P1.6, P1.8 |
| P1.10 | `strings.xml` for `:sharedassets` and `:settings` | `:sharedassets`, `:settings` | P1.9 |
| P1.11 | `NotificationDeveloperOptionsExtra` — test send per channel | `:devoptions` | P0.1, P1.5 |
| P1.12 | Onboarding primer: `NotificationPrimerScreen`, the `AuthFlow` step, `proceedPastNotifications()` split, strings (§10) | `feature/login` | P1.4 |

**P2 — N2 urgency.**

| # | Task | Touches | Blocks on |
|:--|:--|:--|:--|
| P2.1 | `urgency_watermark` table, queries, `UrgencyWatermarkStore` (§6.2) | `core/storage`, `:engine` | P1.1 |
| P2.2 | `UrgencyRank` + both ladders, exhaustive `when`, `complied_ranksBelowDueSoon_despiteHigherOrdinal` (§6.1) | `:model` | P1.1 |
| P2.3 | `UrgencyScanner` — fleet walk, `TaskDueManager` + squawk ranks, watermark diff (§6.3) | `:engine` | P2.1, P2.2 |
| P2.4 | Seeding: silent for a first-seen aircraft; rank-0 for a foreign-authored new record; watermark prune (§6.4) | `:engine` | P2.3 |
| P2.5 | Per-tier batching, notification bodies, post-then-commit ordering (§6.5–6.7) | `:engine` | P2.4 |
| P2.6 | `UrgencyScanScheduler` — Android `PeriodicWorkRequest` (§5.4) | `:engine/androidMain` | P2.5 |
| P2.7 | `UrgencyScanScheduler` — iOS `BGTaskScheduler`, re-submitting each run (§5.4) | `:engine/iosMain`, `iosApp` | P2.5 |
| P2.8 | Session-boundary scan off `AppForegroundObserver.sessionId` + 2h debounce (§6.6) | `:engine` | P2.5 |
| P2.9 | `NotificationTapRouter`, `Screen.AircraftTabDeepLink` (new route, tier pre-filter), shell collection (§5.3) | `:viewing`, `core/nav`, `feature/shell` | P1.8 |
| P2.10 | `LocalAccountMigrator` re-keys watermarks on guest→account upgrade (§6.2) | `core/storage` | P2.1 |
| P2.11 | Developer Options: run scan now, reset watermarks, scan diagnostics (§11) | `:devoptions` | P1.11, P2.5 |
| P2.12 | Analytics: background-vs-foreground delivery split, suppressed when sync is off or anonymous (§11, §12.3) | `:engine`, `core/analytics` | P2.6, P2.7 |
| P2.13 | Privacy policy — N2 crosses no trust boundary; say so (§12.3) | policy copy | P2.5 |

**P3 — Web N1, open tab.**

| # | Task | Touches | Blocks on |
|:--|:--|:--|:--|
| P3.1 | `ForeignWriteListener` in `core:storage`; `PullListener` invokes it on a foreign-authored apply (§8.2) | `core/storage`, `feature/sync/data` | P1.1 |
| P3.2 | `jsMain` detector: shared-aircraft filter, one-shot share-roster read for the actor name (§8.1, §8.3) | `:engine/jsMain` | P3.1 |
| P3.3 | `ActivityCounter` + `tag`-based replacement, `ACTIVITY_WINDOW` / `MIN_REPOST_INTERVAL` (§8.4) | `:engine`, `:viewing/jsMain` | P3.2 |

**P4 — N1 backend + Android.**

| # | Task | Touches | Blocks on |
|:--|:--|:--|:--|
| P4.1 | `device_config` table + a stable install id (§7.1) | `core/storage` | P1.1 |
| P4.2 | `push_devices` schema, `PushTokenRegistrar`, sign-out cleanup, `PushTokenSink` wiring (§5.5, §7.1) | `:datamanager`, `:viewing` | P4.1 |
| P4.3 | `deleteMyAccount` clears `push_devices` (§12.3) | `backend/firebase/functions` | P4.2 |
| P4.4 | `aircraft.proto` + `notification_settings.proto` added to `generate:proto` (§7.2) | `backend/firebase/functions` | P1.2 |
| P4.5 | Fan-out trigger: path-derived host, ACL early exit, actor suppression (§7.2) | `backend/firebase/functions` | P4.4 |
| P4.6 | `notification_activity` counter — lock-free `increment`, session window, repost throttle (§7.4) | `backend/firebase/functions` | P4.5 |
| P4.7 | `notification_rate` hourly ceiling, checked before any write (§7.4) | `backend/firebase/functions` | P4.6 |
| P4.8 | Escalation bypass under `n1esc:` — exempt from throttle and ceiling (§7.5) | `backend/firebase/functions` | P4.6 |
| P4.9 | Data-only FCM payload, `notificationId` + both collapse headers (§7.6) | `backend/firebase/functions` | P4.6 |
| P4.10 | Android FCM receiver renders the payload and posts under the given id (§5.5, §7.3) | `:viewing/androidMain` | P4.9 |
| P4.11 | Rules for `notification_activity` / `notification_rate` + emulator tests (§12.1, §13) | `backend/firebase` | P4.7, P4.8 |
| P4.12 | Privacy policy — N1 sends user content to Apple/Google push infrastructure (§12.3) | policy copy | P4.10 |

**P5 — N1 on iOS.**

| # | Task | Touches | Blocks on |
|:--|:--|:--|:--|
| P5.1 | APNs certificates, entitlements, background modes | `iosApp`, Firebase console | P4.10 |
| P5.2 | Notification service extension to render a data-only message while backgrounded (§7.6) | `iosApp` | P5.1 |
| ~~P5.3~~ | ~~Time Sensitive entitlement for AOG~~ — **cancelled 2026-08-26**, AOG is not its own tier and needs no interruption-level treatment (§5.2, §9.4) | — | — |
| P5.4 | iOS token registration + two-account parity check | `:datamanager/iosMain` | P5.2 |

**P6 — Web push (V1.1).**

| # | Task | Touches | Blocks on |
|:--|:--|:--|:--|
| P6.1 | Service worker + VAPID registration | `webApp` | P4.9 |
| P6.2 | `push_devices` for web; `AppCapability.isPushSupported` flips true on `jsMain` | `:datamanager/jsMain`, `core/appinfo` | P6.1 |
| P6.3 | Closed-tab N1 and N2 via `ServiceWorkerRegistration.showNotification()` (§8.4, §8.5) | `:viewing/jsMain` | P6.2 |

**Externally blocked, start ahead of its phase:** P5.1 (APNs certificates) has lead time that has
nothing to do with code being ready.

---

## 15. Deltas from the PRD

Everything here is a change to what the PRD specified, gathered in one place so the PRD can be
amended rather than quietly diverged from.

| # | PRD | This design | Why |
|:--|:--|:--|:--|
| D1 | §9.1.6 — "foreground is already observable via `AppForegroundObserver`" | It is a session-boundary counter, not a foreground stream. Collect `sessionId` instead of adding an event. | §6.6. Its own doc comment asks future consumers to grow from it rather than add a second observer. |
| D2 | §9.1.4 — implies `MaintenanceOverview` supplies hours | `TaskDueManagerImpl` derives current hours from `max()` over the aircraft's logs. The scanner must pass logs. | §6.3. Passing an empty log list reports the whole fleet overdue. |
| D3 | §6.4 — silent seeding, silent on a new record | A new record written by **someone else** seeds at rank 0, so an already-urgent one reports on the same scan | §6.4. Otherwise a mechanic's AOG squawk is silently seeded on the owner's device and N2 never mentions it. |
| D4 | §5.4 — buffer writes behind a 5-min quiet timer and a 30-min ceiling, then flush one summary | **Nothing is buffered.** Every write sends under a deterministic notification id, so later pushes *replace* the tray entry instead of stacking; a counter doc supplies the "5 changes" | §7.3. The buffer needed a clock the server does not have (triggers fire on writes, not on their absence) **and** could not meet the PRD's own "~a minute for an isolated edit" — a trailing-edge debounce cannot know an edit was isolated until the window has passed. |
| D5 | §5.4 — a scheduled sweep flushes buffers past either timer | **No sweep, no Cloud Scheduler job, no Cloud Tasks queue.** Work happens on writes only | §7.4. Follows from D4: with nothing buffered there is no deadline to poll for. Also removes the claim/reclaim dance — a crashed invocation means one write went unannounced, and the next write re-sends with a corrected count. |
| D6 | §4 / Q10 — N2 "requires nothing else"; the scan runs signed out | Requires *a Firebase user* — anonymous counts. With no user there is no local fleet, so the scan is a no-op. | §6.8. The real promise (no real account, no sync, no network) is intact; the wording is not. |
| D7 | §9.2 — "notification preferences: new synced entity" | Every proto field is inverted (`*_disabled`) | §4.1. proto3 has no scalar presence, so all-false must mean all-on or a user who never opened settings is silenced. |
| D8 | §11 — instrument via the analytics plan | The scan reports nothing when sync is off or the account is anonymous | §12.3. The PRD calls this a privacy call for the design doc and names this as the safe default. |
| D9 | — (not addressed) | `aircraft.proto` and `settings/notification_settings.proto` must be added to the functions' `generate:proto` list | §7.2. Neither is generated today, and the fan-out cannot read a tail number or a preference without them. |
| D10 | §7.5 — iOS N1 in V1 | ~~iOS Time Sensitive interruption level needs an entitlement and App Store review; sequenced into P5~~ — **reversed 2026-08-26**: AOG is not its own tier, so no class needs Time Sensitive at all; iOS never maps `highPriority` to `.timeSensitive` | §5.2, §9.4. iOS N2 ships at default interruption level always, not just in P2. |
| D11 | §9.2 — preferences are "a new synced entity … like every other setting" | The manager must resolve *hydrated* from *never set* before any read or write. `DeveloperOptionsManagerImpl` is not the template — it never hydrates. `TechnicianManagerImpl.awaitHydratedSelfId` is. | §4.3. Reading through an unhydrated store shows the wrong toggles; **writing** through it pushes a whole-message overwrite that reverts the user's settings on every other device. |
| D12 | §9.2 — "`feature/notifications`: canonical module set (`model` / `datamanager` / `sharedassets` / `settings`)" | Eight modules: `model`, `permission`, `viewing`, `datamanager`, `engine`, `sharedassets`, `settings`, `devoptions` — with `viewing` meaning the *notification display surface*, not the canonical read-only-UI layer | §3. One `datamanager` holding both the scanner and the display surface forces every consumer to inherit the scanner's five feature-datamanager dependencies; `feature/login` would compile against `feature:tasks:datamanager` to show one onboarding card. |
| D13 | §9.6 — Developer Options test-send actions | Two Koin-resolved interfaces in a new `feature/developeroptions/plugin` — `DeveloperOptionsExtra` for the row, `DeveloperOptionsNavContributor` for the page — replacing the single `dogfoodContent` slot. **Done ahead of this feature in #511/#512.** | §11.1. The slot was singular and already taken, so a second section forced a signature change either way. Also removed `feature:stresstest:config` and all `isStressTestSupported` threading from `feature:shell`. **Touched three modules this feature otherwise would not.** |
| D14 | §9.2 — preferences as "a new synced entity" implies a domain type beside the proto | No Kotlin mirror. `NotificationSettings` is passed around directly, with extension properties supplying the positive names | §4.1. A mirror restates the all-on defaults in a second place and needs a round-trip test to stay honest; `SubscriptionManager` already rules that "never a forked Kotlin copy" for a proto the Cloud Functions also read. |
| D15 | §5.1 — `PermissionState` is `UNDETERMINED / GRANTED / DENIED` | A fourth value, `UNSUPPORTED`, for a browser where the Notifications API is genuinely absent | §5.1, §9.3. `DENIED` would tell the settings screen to offer an "Open settings" fix that does not exist; conflating "blocked" with "cannot exist here" sends a pilot hunting through a settings page that will never fix it. Deliberately not a fourth `AppCapability` — it is a runtime property of the browser, not the build, and `NotificationPermission` already answers exactly that class of question. |

## 16. Open questions

| # | Question | Recommendation |
|:--|:--|:--|
| E1 | ~~Should the scheduled Android scan chase 08:00 local...~~ | **Moot as of the 2026-08-22 cadence change (§6.6):** the target is no longer a single daily time-of-day, it's at least every 2h, so a plain `PeriodicWorkRequest(2h)` is both the simple option and the right one — there is no 08:00 to chase anymore. |
| E2 | Does the per-device `enabled` flag (Q2) need a UI in V1, or is uninstall/sign-out sufficient? | No UI in V1. It exists on the token doc so the server honors it, and Developer Options can flip it; a real UI needs a device list, which is an inbox-era feature. |
| E3 | Should the web detector also drive N2, or does the session-boundary scan cover it? | Session scan only. Web N2 has no scheduler, and a sync-driven scan on every foreign write is a different (and much chattier) trigger than a daily one. |
| E4 | Should P2 land the tap-router deep links, or can P2 ship notifications that only open the app? | Land them in P2. Tap-through rate is a stated success metric (PRD §11) and a notification that dumps the user on the fleet dashboard will not earn it. |
