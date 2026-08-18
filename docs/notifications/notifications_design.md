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
| Where it runs | Entirely on the device | Firestore trigger + scheduled sweep + FCM/APNs (web: on-device) |
| New backend surface | **None** | Token registry, fan-out trigger, coalescing buffer, sweep |
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
│                   UrgencyScanScheduler (expect), the web N1 detector, CoalescingBuffer
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
| `devoptions` | `core:ui`, `:engine`, `:viewing` | Developer-only. Its own module so `engine` stays Compose-free and `settings` stays `engine`-free — the same reason `feature:stresstest:config` is separate from `feature:stresstest`. §11 |
| `settings` | `core:ui*`, `:model`, `:permission`, `:datamanager`, `:sharedassets` | **Not `engine`, and not `viewing`.** The screen reads preferences and permission state; it neither scans nor posts. |

And what the rest of the app takes on:

| Consumer | Depends on | Why |
|:--|:--|:--|
| `feature:login` | `:permission` only | `NotificationPermission` for the primer (§10.1) |
| `feature:shell` | `:settings`, `:viewing` | The `Screen.Notifications` route, and collecting tap routes into the nav graph. **Not `engine` and not `devoptions`** — §11 |
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

  // --- Urgency (N2) — device-local detection, no account required ---
  bool aog_disabled = 2;                 // any escalation to SQUAWK_PRIORITY_AOG
  bool squawk_priority_disabled = 3;     // escalations below AOG, and reopened squawks
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
val NotificationSettings.aogEnabled: Boolean get() = !aog_disabled
val NotificationSettings.overdueEnabled: Boolean get() = !overdue_disabled
// …one line per field

fun NotificationSettings.withAog(enabled: Boolean) = copy(aog_disabled = !enabled)
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

§7.4's sweep reads `users/{recipient}/notification_settings/main` directly from Firestore, which *is*
the source of truth — a missing doc there means "never set," full stop. An absent doc, a
default-decoded message, and all-on are the same thing (§4.1), so the sweep needs no equivalent
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
enum class PermissionState { UNDETERMINED, GRANTED, DENIED }

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

This is the whole module. It has four consumers — the onboarding primer, the settings screen and its
banner, and the scanner's precondition check — none of which want anything else the feature owns, so
it depends on nothing the feature owns either: not `:model`, not `:viewing`. The only reason it is
under `feature/notifications/` rather than in `core/` is that OS notification permission is this
feature's concern; nothing else in the app asks for it.

That leafness is what makes the §10.1 dependency defensible. A primer card that transitively compiled
against `feature:tasks:datamanager` would be the tail wagging the dog.

| Platform | `request()` | `openSystemSettings()` |
|:--|:--|:--|
| Android | `POST_NOTIFICATIONS` via the activity from `CurrentActivityProvider` (already in `core/lifecycle/androidMain`, already used by `AuthManagerImpl`); auto-`GRANTED` below API 33 | `ACTION_APPLICATION_DETAILS_SETTINGS` |
| iOS | `UNUserNotificationCenter.requestAuthorizationWithOptions` (alert + sound + badge) | `UIApplication.openSettingsURLString` |
| Web | `Notification.requestPermission()` | `canOpenSystemSettings = false` — no browser API exists. §9.3 |

`minSdk` is 33 across the tree (`feature/sync/settings/build.gradle.kts:13` and siblings), so the
sub-33 auto-grant branch is dead code today. Write it anyway and comment why: the runtime prompt is
the behaviour that matters and a future `minSdk` drop must not silently start denying.

### 5.2 `LocalNotifier` — `viewing`

`PendingNotification` is the contract between the two halves of the feature: `engine` builds one,
`viewing` renders it. It carries finished display strings and a tap target, never a task or a squawk
— which is what keeps `viewing` free of every other feature.

```kotlin
enum class NotificationChannel { COLLABORATION, URGENCY, GROUNDED }  // Q8: one per class

data class PendingNotification(
  val id: String,                 // stable — re-posting the same id replaces, never stacks
  val channel: NotificationChannel,
  val title: String,
  val body: String,
  val highPriority: Boolean,      // AOG + Overdue
  val tapTarget: NotificationTapTarget,
)

interface LocalNotifier {
  suspend fun post(notification: PendingNotification)
  suspend fun cancel(id: String)
}
```

Android registers the three channels at Koin init (they must exist before the first post, and
re-creating an existing channel is a no-op); `GROUNDED` gets `IMPORTANCE_HIGH`, the other two
`IMPORTANCE_DEFAULT`. iOS maps `highPriority` to `UNNotificationInterruptionLevel.timeSensitive`,
which needs the Time Sensitive Notifications entitlement — **an App Store review item, sequenced into
P5 with the APNs work, not P2.** Until it lands, iOS N2 posts at the default interruption level; the
notification still arrives, it just does not pierce Focus.

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

`NotificationTapRouter` in `viewing` converts one to a `Screen` route
(`Screen.EditSquawk.createRoute(...)` and friends already exist in `core/nav/Screen.kt`; `core:nav`
is core, so the rule in §3 permits it). Tapping is the return leg of display, so it belongs with the
notifier that posted the thing being tapped. It emits
onto a `MutableSharedFlow<String>` that `ShellNavGraph` collects — the same shape
`EmailLinkDeepLinks.pendingLink` uses, so cold start (target buffered until the graph composes) and
warm tap (delivered immediately) both work without a second mechanism.

Two routes do not exist yet and are needed for the coalesced/summary bodies, which land on a *list*
rather than a record:

```kotlin
data object AircraftTabDeepLink : Screen("aircraft/{$AIRCRAFT_ID}?tab={tab}&tier={tier}") { … }
```

`tier` is optional and pre-filters the task list to Overdue or Due Soon, satisfying PRD §6.6
("Tapping a summary opens that aircraft's task list filtered to the tier").

**Tap-through must degrade, not crash.** A revoked share, a deleted record, or a device that has not
synced yet all produce "no longer available" and land on the fleet (PRD §12).

**The router does not pre-check that the record resolves.** An earlier draft had it do so; the module
rule in §3 forbids it — resolving a squawk id means `feature:squawk:datamanager`, which `viewing` may
not depend on. That constraint improves the design rather than constraining it: the check was racy
anyway (the record can vanish between the check and the navigation), and the destination screens must
already handle a missing record, since a record can be deleted on another device while its edit screen
is open. So the router navigates unconditionally and the destination owns the empty state — one
behaviour instead of two paths to the same message.

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
| Android | `PeriodicWorkRequest` (24h, flex 4h) via `WorkManager.enqueueUniquePeriodicWork(KEEP)`, no network constraint — the scan is local. `WorkManagerUploadScheduler` is the shape to copy. |
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

**Lifecycle, and specifically what does *not* wipe it:**

| Event | Watermarks |
|:--|:--|
| Sign-out | **Kept.** `deleteEntitiesForUser` wipes the rows and sign-in re-hydrates them; surviving watermarks mean the returning user is compared against real prior state instead of being silently re-seeded and losing a cycle. |
| Integrity-check wipe | **Kept**, for the same reason `sync_config` is excluded from `wipeAllEntities` — it is user-facing state, not a cache. |
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

Per PRD §6.6, at most one notification per `(aircraft, tier)`, where tier ∈ {Grounded, Overdue, Due
Soon, Priority raised}:

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

So: the scanner collects `sessionId`, and on each change runs a scan **if `now - lastScanAt >= 4h`**
(`lastScanAt` in `sync_config`, via the `SyncPreferences.booleanConfig` pattern). The scheduled daily
scan ignores the debounce.

| Platform | Daily | Session-boundary | Effective cadence |
|:--|:--|:--|:--|
| Android | `PeriodicWorkRequest` 24h / flex 4h | ✅ | Daily, or better for an active user. OEM battery managers may kill periodic work; the session scan is the backstop. |
| iOS | `BGTaskScheduler`, opportunistic | ✅ | Best-effort daily; in practice the session scan carries it. |
| Web | none | ✅ | Session scan only, and only while a tab is open. |

**08:00 local (Q4) is a target, not a guarantee, and only Android can honestly aim for it.**
`PeriodicWorkRequest` has no time-of-day API — the usual construction is a one-time request with an
`initialDelay` computed to the next 08:00 that re-enqueues itself. `BGTaskScheduler` accepts only an
`earliestBeginDate` and iOS decides the rest. Do not build timezone plumbing for this: the device
uses its own clock, and `TaskDueManagerImpl` already defaults to `TimeZone.currentSystemDefault()`.

**Metric to instrument from day one** (PRD §11): what share of urgency notifications came from the
background scan versus the session scan, per platform. That single number decides whether iOS keeps
claiming a daily cadence or the copy changes to foreground-only (PRD §7.5's honesty note).

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
   else                    -> upsert the coalescing buffer          // §7.3
```

Step 2 is the early exit that keeps this cheap: **most writes are on unshared aircraft**, and they
cost one document read and nothing else. The `<= 1` test (not `!exists`) also covers a share whose
last member left but whose ACL doc survives.

Two additions to `backend/firebase/functions/package.json`'s `generate:proto`, both currently absent:

- `aircraft/aircraft.proto` — the notification body leads with the tail number (PRD §5.3, "aircraft
  identity first"), and the server cannot read it out of anything else; the aircraft record is opaque
  proto bytes.
- `settings/notification_settings.proto` — the sweep decodes each recipient's preferences to honor
  their per-class toggles.

### 7.3 The coalescing buffer, keyed by source rather than by recipient

PRD §5.4 keys the buffer per `(recipient, aircraft, recordType, actor)`. Keying it per
`(aircraft, recordType, actor)` and fanning out at flush is strictly better on three counts, and
this design takes that route:

- **Fewer writes.** A five-member share is one buffer write per edit instead of four.
- **§9.5 falls out instead of needing enforcement.** The PRD requires the audience be re-derived from
  the ACL at send time, never from a cached list — because a revoked member must stop receiving
  immediately, and `SharedScopeJanitor` has already pruned their local data. With a per-recipient
  buffer the audience is resolved at *buffer* time and must be re-checked at flush anyway; with a
  shared buffer there is no cached audience to go stale.
- **Preferences are honored at flush**, against the recipient's current settings rather than their
  settings when the first edit landed.

```
notification_batches/{aircraftId}__{recordType}__{actorUid}
  hostUid, aircraftId, recordType, actorUid
  aircraftLabel        // resolved once at buffer time — cosmetic, so staleness is harmless
  actorDisplayName     // from aircraft_shares/{host}/aircraft/{ac}/members/{actor}.displayName
  firstWriteAt, lastWriteAt
  changeCount
  sampleTitles[]       // capped at 5
  flushAt              // = min(lastWriteAt + 5min, firstWriteAt + 30min), recomputed each upsert
```

The doc id concatenates with `__`; Firebase uids and the UUID aircraft ids are alphanumeric, and
`recordType` is a fixed enum, so the separator is unambiguous. Rules:
`match /notification_batches/{id} { allow read, write: if false; }` — functions only.

`flushAt` as a stored field is what makes the sweep a single indexed query instead of a scan, and it
encodes both of PRD §5.4's timers in one number: the quiet timer that collapses a burst, and the
max-wait ceiling that keeps a technician working steadily for an hour from getting silence.
`sampleTitles` is capped because a batch doc is not a change log.

Neither `actorDisplayName` nor `aircraftLabel` is authorization-relevant, so resolving them once at
buffer time and letting them go stale is fine — and it means the sweep does zero extra reads for the
body. Missing display name falls back to "A collaborator" (PRD §5.3).

### 7.4 The sweep

`scheduledNotificationSweep`, an `onSchedule` function following `scheduledStorageSweep` exactly
(`storageSweepTriggers.ts` — Cloud Scheduler, "Force run" in the console for on-demand, schedule as a
code constant so arming it costs a redeploy).

```
every 1 min:
  batches = notification_batches where flushAt <= now, limit N
  for each batch:
    claim in a transaction (set claimedAt; skip if claimed within the last 2 min)
    audience = memberRoles(hostUid, aircraftId) minus actorUid        // re-derived, §9.5
    for each recipient:
      prefs = decode(users/{recipient}/notification_settings/main)
      skip if allDisabled or the recordType's class is off
      tokens = users/{recipient}/push_devices where enabled == true
      send
    delete the batch
```

**Claim-send-delete, not delete-then-send.** PRD §5.4 describes deleting the buffer inside the
transaction that read it. That makes the send at-most-once: a crash between the commit and the FCM
call loses the batch silently. Claiming with a reclaim timeout makes it at-least-once — a crash
re-delivers up to one duplicate summary, which is the right side of that trade for a notification
system. The 2-minute reclaim window comfortably exceeds a normal flush.

**Per-aircraft rate ceiling** (PRD §9.4, §12). `feature/stresstest` is compiled into every build and
will be pointed at a shared aircraft. Cap sends per `(aircraft, hour)`; past the cap, collapse to one
"N4589T · a lot of activity" and drop the rest. Enforce it in the sweep, where the count is already
in hand — enforcing after the send is not enforcement.

### 7.5 The escalation bypass

A write that raises a squawk's priority sends immediately with the specific §6.5 body, never folded
into a summary. PRD §5.4 calls this a hard rule, not a tuning knob, and the reasoning holds: "wait up
to 30 minutes to see whether they are still typing" is the wrong behaviour for AOG.

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
    "titleKey": "…", "bodyArgs": "…",
    "tapTarget": "squawk:{aircraftId}:{squawkId}"
  }
}
```

Localization is the reason for `titleKey`/`bodyArgs` rather than a rendered string: the server does
not know the recipient's locale, and the client already has `strings.xml`. iOS needs
`content-available` plus a notification service extension to render a data-only message while
backgrounded; that is part of P5, and until it lands iOS may ship rendered strings with a TODO.

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

### 8.4 Client-side coalescing

The server buffer never sees this path. A `CoalescingBuffer` in `engine/commonMain` with the same 5-minute /
30-minute constants, keyed by `(aircraftId, recordType, actorUid)`, in-memory and per-tab. It is
small, it is shared code, and the same class is the natural home for any future client-side
coalescing. V1 could ship without it and accept a burst on web, but the class is a few dozen lines
and the constants are already written down — build it.

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
  val confirmDisableAog: Boolean = false, // Q5
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

Toggle rows read `state.settings.aogEnabled` and write
`prefsManager.update { it.withAog(enabled) }` (§4.1) — the screen never sees an inverted field name,
and never constructs a `NotificationSettings` of its own.

Any in-progress edit lives in the ViewModel's `StateFlow`, never in composable `remember` — this
screen can be torn down by the OS permission dialog.

### 9.3 States the screen must be honest about

| State | Affects | UI |
|:--|:--|:--|
| Permission `UNDETERMINED` | Both groups | Toggles active; flipping the master on triggers the OS prompt inline |
| Permission `DENIED` | Both groups | Persistent **neutral** banner + "Open settings". Toggles stay editable so choices survive fixing the permission. |
| Signed out / anonymous | Collaboration only | Footer with a "Sign in" action. **The urgency group stays fully live and is not dimmed.** |
| Cloud sync off | Collaboration only | Footer with "Turn on sync" → Backup & Sync. Urgency unaffected. |
| Web | Denied banner | Drops the button (`canOpenSystemSettings == false`) and names where to look, phrased generically since the path differs by browser |

Dimming the urgency group for a signed-out user would be a straightforward bug: it works fine for
them, and they are the users for whom it matters most (§6.8).

The banner is **informational, not an error** — no red, no destructive iconography. Notifications are
a convenience on top of a logbook that works without them. Neutral surface color, plain sentence.
Read `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` before building it; the aviation
palette is required and dynamic color is disabled.

Copy conventions: all strings from `strings.xml`, reuse before adding, apostrophes literal, and edit
actions worded as "Update X" rather than "Edit X".

### 9.4 Disabling AOG (Q5)

Mutable, with a confirmation: "You won’t be told when an aircraft is grounded." A user who cannot
silence one alert silences the whole app instead.

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

Three of these four actions need `engine`, and `DeveloperOptionsScreen` lives in `feature:settings`,
which §3 keeps off `engine` deliberately. The existing escape hatch is a composable slot the shell
fills:

```kotlin
// feature/settings — DeveloperOptionsScreen.kt
dogfoodContent: @Composable () -> Unit = {},

// feature/shell — ShellNavGraph.kt
dogfoodContent = { if (isStressTestSupported) StressTestDeveloperOptionsExtra(navController) },
```

**That slot is singular and already occupied.** A second extra cannot be added without either
nesting both inside one lambda or changing the signature — so this feature forces a change to
`DeveloperOptionsScreen` no matter which way it goes. Given that, the generalization is the cheaper
of the two:

```kotlin
// core/ui — DeveloperOptionsExtra.kt
/**
 * A Developer Options section contributed by a feature. Resolved from Koin rather than passed down,
 * so a feature can add one without the shell — or feature:settings — depending on it.
 *
 * [onNavigate] rather than a NavController on purpose: it keeps this interface, and therefore
 * core:ui, free of a navigation dependency, and an extra that does not navigate ignores it.
 */
interface DeveloperOptionsExtra {
  /** Ascending. Fixed numbers so section order does not depend on Koin registration order. */
  val order: Int
  /** False hides the section outright — this is where the capability gate now lives. */
  fun isAvailable(): Boolean = true
  @Composable fun Content(onNavigate: (route: String) -> Unit)
}
```

`DeveloperOptionsScreen` replaces `dogfoodContent()` with the resolved list:

```kotlin
val koin = getKoin()
val extras = remember { koin.getAll<DeveloperOptionsExtra>().sortedBy { it.order } }
extras.filter { it.isAvailable() }.forEach { extra ->
  extra.Content(onNavigate = navController::navigate)
  HorizontalDivider()
}
```

Contributors bind through their own Koin module — `bind DeveloperOptionsExtra::class`, so `getAll`
finds them. Note that two definitions of the same type make a bare
`get<DeveloperOptionsExtra>()` ambiguous; nothing should ever call it, and `getAll` is the only
supported read.

**In `core:ui`, not `feature:settings`.** Putting the interface in `feature:settings` would make
every contributor depend on a UI feature module — worse than the problem being solved.

What this buys, beyond the notification section:

- **The shell drops both `:engine` and `feature:stresstest:config`.** Its only remaining notification
  dependencies are `:settings` (the route) and `:viewing` (tap routes).
- **`isStressTestSupported` stops being plumbed through `settingsDetailRoutes` for this purpose.** The
  gate moves into the stress-test extra's own `isAvailable()`, where the capability actually belongs.
  The parameter stays for `registerStressTestRoutes`.
- **Adding a Developer Options section stops being a shell edit.** It becomes a Koin binding in the
  feature that owns the section.

### 11.2 Prerequisite: migrate the stress-test extra

Not optional and not this feature's to skip — `dogfoodContent` cannot survive alongside the list.
`StressTestDeveloperOptionsExtra` (`feature/stresstest/config/StressTestPlugin.kt`, already named a
*plugin*) becomes a `DeveloperOptionsExtra` implementation contributed by `stressTestKoinModules()`,
with `isAvailable() = capability.isStressTestSupported` and a high `order` so "Debug tools" stays
last. Its `navController.navigate(STRESS_TEST_ROUTE)` becomes `onNavigate(STRESS_TEST_ROUTE)`. The
`dogfoodContent` parameter and the shell's import are then deleted.

The notification section is `NotificationDeveloperOptionsExtra` in `feature/notifications/devoptions`
— its own module so that `engine` stays free of Compose and `settings` stays free of `engine`,
mirroring why `feature:stresstest:config` is separate from `feature:stresstest`.

---

## 12. Security, privacy, rules

### 12.1 Rules changes

| Path | Change |
|:--|:--|
| `users/{uid}/push_devices/{id}` | **None needed** — covered by the own-tree rule. Add a comment naming it. |
| `users/{uid}/notification_settings/main` | **None needed** — same. |
| `notification_batches/{id}` | New: `allow read, write: if false;` (functions only) |

Add emulator tests to the existing vitest suite in `backend/firebase/functions` covering: another
user cannot read my `push_devices`; no client can read or write `notification_batches`; a revoked
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
is the property the whole convention exists for, and the contract the server-side sweep independently
relies on.

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

**Backend** (emulator + vitest, per the existing harness): an unshared aircraft produces no batch;
the actor is excluded from the audience; two writes inside the quiet window produce one batch with
`changeCount = 2`; continuous writes flush at the 30-minute ceiling; a priority escalation bypasses
the buffer; a revoked member is excluded at flush even though they were a member at buffer time; a
claimed batch is not double-swept; the per-aircraft rate ceiling holds under a stress-test burst.

**Not unit-tested, verify by hand:** `WorkManager` and `BGTaskScheduler` actually firing, OS
permission dialogs, channel importance, tray rendering, and cold-start tap routing.

---

## 14. Implementation order

Maps onto the PRD's phases. P1–P3 touch no backend at all.

| Phase | Contents | Exit criteria |
|:--|:--|:--|
| **P1 Foundations** | Eight modules (§3) wired into `settings.gradle.kts` + `CommonAppModules`; proto + `CollectionKind` (all 5 registration points, §4.2); `NotificationPrefsManager` **including the hydration-resolution rule** (§4.3); `NotificationPermission` + `LocalNotifier` actuals ×3; channels; `Screen.Notifications` + nav; settings screen; **`DeveloperOptionsExtra` in `core:ui` + migrating the stress-test extra off `dogfoodContent` (§11.2)**; the notification Developer Options section | A dev build requests permission and posts a local notification on each channel; preferences persist and appear on a second device; **and a fresh install of an account with non-default preferences shows the spinner until they hydrate, never all-on — with the toggles disabled meanwhile** |
| **P2 N2 urgency** | `urgency_watermark` table; `UrgencyRank`; `UrgencyScanner`; `UrgencyScanScheduler` (Android + iOS); session-boundary scan; seeding; per-tier batching; tap routing; scan diagnostics | Crossings fire exactly once; de-escalations silent; a fresh install notifies nothing; **no backend change was required** |
| **P3 Web N1** | `ForeignWriteListener` in `core:storage`; `PullListener` hook; `jsMain` detector; one-shot actor read; `CoalescingBuffer` | A web user with a shared aircraft open sees a collaborator's edit from another account; **no backend, no token registry** |
| **P4 N1 backend + Android** | `device_config` table; `PushTokenRegistrar`; `aircraft.proto` + `notification_settings.proto` added to `generate:proto`; fan-out trigger; buffer; sweep; rate ceiling; rules + emulator tests | Two accounts sharing an aircraft see each other's changes; neither sees their own |
| **P5 N1 on iOS** | APNs certificates, entitlements, background modes, notification service extension, Time Sensitive entitlement | Parity with Android on N1; `timeSensitive` works for AOG |
| **P6 Web push** | Service worker, VAPID, `push_devices` for web | `isPushSupported` flips true on `jsMain` |

Dogfood across P2–P5. P2 tunes the scan cadence and per-tier batching; P4 tunes the coalescing
window. The noise floor has to be felt rather than reasoned about.

---

## 15. Deltas from the PRD

Everything here is a change to what the PRD specified, gathered in one place so the PRD can be
amended rather than quietly diverged from.

| # | PRD | This design | Why |
|:--|:--|:--|:--|
| D1 | §9.1.6 — "foreground is already observable via `AppForegroundObserver`" | It is a session-boundary counter, not a foreground stream. Collect `sessionId` instead of adding an event. | §6.6. Its own doc comment asks future consumers to grow from it rather than add a second observer. |
| D2 | §9.1.4 — implies `MaintenanceOverview` supplies hours | `TaskDueManagerImpl` derives current hours from `max()` over the aircraft's logs. The scanner must pass logs. | §6.3. Passing an empty log list reports the whole fleet overdue. |
| D3 | §6.4 — silent seeding, silent on a new record | A new record written by **someone else** seeds at rank 0, so an already-urgent one reports on the same scan | §6.4. Otherwise a mechanic's AOG squawk is silently seeded on the owner's device and N2 never mentions it. |
| D4 | §5.4 — buffer keyed per `(recipient, aircraft, recordType, actor)` | Keyed per `(aircraft, recordType, actor)`; fan out at flush | §7.3. Fewer writes, and §9.5's "re-derive the audience at send time" stops being a rule to enforce. |
| D5 | §5.4 — sweep deletes the buffer in the transaction it read it from | Claim (with a 2-min reclaim window), send, then delete | §7.4. Delete-then-send is at-most-once; a crash loses the batch silently. |
| D6 | §4 / Q10 — N2 "requires nothing else"; the scan runs signed out | Requires *a Firebase user* — anonymous counts. With no user there is no local fleet, so the scan is a no-op. | §6.8. The real promise (no real account, no sync, no network) is intact; the wording is not. |
| D7 | §9.2 — "notification preferences: new synced entity" | Every proto field is inverted (`*_disabled`) | §4.1. proto3 has no scalar presence, so all-false must mean all-on or a user who never opened settings is silenced. |
| D8 | §11 — instrument via the analytics plan | The scan reports nothing when sync is off or the account is anonymous | §12.3. The PRD calls this a privacy call for the design doc and names this as the safe default. |
| D9 | — (not addressed) | `aircraft.proto` and `settings/notification_settings.proto` must be added to the functions' `generate:proto` list | §7.2. Neither is generated today, and the fan-out cannot read a tail number or a preference without them. |
| D10 | §7.5 — iOS N1 in V1 | iOS Time Sensitive interruption level needs an entitlement and App Store review; sequenced into P5 | §5.2. iOS N2 ships at default interruption level in P2. |
| D11 | §9.2 — preferences are "a new synced entity … like every other setting" | The manager must resolve *hydrated* from *never set* before any read or write. `DeveloperOptionsManagerImpl` is not the template — it never hydrates. `TechnicianManagerImpl.awaitHydratedSelfId` is. | §4.3. Reading through an unhydrated store shows the wrong toggles; **writing** through it pushes a whole-message overwrite that reverts the user's settings on every other device. |
| D12 | §9.2 — "`feature/notifications`: canonical module set (`model` / `datamanager` / `sharedassets` / `settings`)" | Eight modules: `model`, `permission`, `viewing`, `datamanager`, `engine`, `sharedassets`, `settings`, `devoptions` — with `viewing` meaning the *notification display surface*, not the canonical read-only-UI layer | §3. One `datamanager` holding both the scanner and the display surface forces every consumer to inherit the scanner's five feature-datamanager dependencies; `feature/login` would compile against `feature:tasks:datamanager` to show one onboarding card. |
| D13 | §9.6 — Developer Options test-send actions | Requires replacing `DeveloperOptionsScreen`'s single `dogfoodContent` slot with a Koin-resolved `List<DeveloperOptionsExtra>` in `core:ui`, and migrating the existing stress-test extra onto it | §11. The slot is singular and already taken, so a second section forces a signature change either way; generalizing also removes `:engine` and `feature:stresstest:config` from `feature:shell`. **Touches two modules this feature otherwise would not.** |
| D14 | §9.2 — preferences as "a new synced entity" implies a domain type beside the proto | No Kotlin mirror. `NotificationSettings` is passed around directly, with extension properties supplying the positive names | §4.1. A mirror restates the all-on defaults in a second place and needs a round-trip test to stay honest; `SubscriptionManager` already rules that "never a forked Kotlin copy" for a proto the Cloud Functions also read. |

## 16. Open questions

| # | Question | Recommendation |
|:--|:--|:--|
| E1 | Should the scheduled Android scan chase 08:00 local with a self-re-enqueuing one-time request, or accept a plain 24h periodic? | Start with the plain periodic in P2. The session-boundary scan (§6.6) already covers active users, and the 08:00 target is worth real complexity only if the diagnostics show background scans landing at hours users complain about. |
| E2 | Does the per-device `enabled` flag (Q2) need a UI in V1, or is uninstall/sign-out sufficient? | No UI in V1. It exists on the token doc so the server honors it, and Developer Options can flip it; a real UI needs a device list, which is an inbox-era feature. |
| E3 | Should the web detector also drive N2, or does the session-boundary scan cover it? | Session scan only. Web N2 has no scheduler, and a sync-driven scan on every foreign write is a different (and much chattier) trigger than a daily one. |
| E4 | Should P2 land the tap-router deep links, or can P2 ship notifications that only open the app? | Land them in P2. Tap-through rate is a stated success metric (PRD §11) and a notification that dumps the user on the fleet dashboard will not earn it. |
