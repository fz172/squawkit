# Web Target Expansion Plan

**Implementation Status (2026-05-24):** This doc is the ordered plan to grow the
standalone `webApp` seed into a real, code-sharing web client.

- **M0 — ✅ shipped.** Standalone `webApp` Kotlin/JS module renders a login screen
  (`ComposeViewport` + Compose MP).
- **M1 — ✅ complete.** `core:model`, `core:datetime`, and `core:ui` gained `js(IR)`
  targets and compile clean to JS (Wire protobuf, coil, ktor-client-core,
  `compose-ui-tooling-preview`, material-icons-extended all have JS variants — none
  blocked). JS `actual`s added (`toWireInstant`, `platformColorScheme` → null aviation
  palette, `rememberBrandHeadlineFamily` → Space Grotesk). `webApp` renders through the
  shared `WingslogTheme`; its duplicated tokens/fonts deleted.
- **M2 — ✅ complete.** `LoginScreen`/`LoginViewModel` extracted into a JS-capable
  `feature/login` (deps: only `core:ui` + `core:auth`); `core:auth` has a `js` target +
  `jsMain` `AuthManagerImpl`; `webApp` initializes Firebase JS, starts Koin, and renders
  the *shared* production login screen — the mock and `composeApp`'s copy deleted (single
  source across Android/iOS/web). **Both anonymous and Google sign-in verified working in
  the browser.** Google goes through a deliberate GitLive + raw-Firebase-JS hybrid (see
  M2 step 4).
- **M3 — ✅ code-complete; browser runtime pending.** `core:storage` has a `js` target.
  To make the browser sql.js driver work the whole `WingsLogDatabase` was switched to
  `generateAsync=true`; the Android/iOS sync drivers wrap the async schema via
  `Schema.synchronous()`. The suspend cascade was absorbed: `TombstoneGc.runOnce`,
  `DatabaseIntegrityChecker.wipe*`, `OnboardingPreferences.setHasSeenWelcome`,
  `SyncPreferences` setters, and `SyncCursorStore`/`PullListener`/`HydrationRunner` write
  paths are now `suspend`, with callers updated to await them (e.g. logout waits for the
  wipe). `Dispatchers.IO` (absent on JS) is replaced by an expect/actual `storageIoContext`
  (IO on mobile, Default on web). JS `DriverFactory` uses `WebWorkerDriver` +
  `@cashapp/sqldelight-sqljs-worker`. **Verified:** Android `assembleDebug` + full
  `testDebugUnitTest`, iOS compile, webApp JS bundle all green. **Pending:** in-browser
  read/write round-trip (the sql.js worker + WASM load, like auth, needs a browser test).
-
- **M4 — ✅ code-complete; authenticated browser round-trip deferred until M5 UI.** `core:firebase`,
  `feature:attachment:{model,datamanager}`, `feature:sync:data`, and
  `feature:technician:datamanager` now have `js` targets. Sync database reads use async
  SQLDelight query extensions and a platform sync dispatcher (`Default` on web). Web starts
  `SyncEngine`; `feature:login` backs onboarding names with the shared real local-first
  `TechnicianManager` adapter.
  Attachments remain inactive on web through empty platform/scheduler modules and a guarded
  SHA-256 stub. **Verified:** affected Android unit tests, iOS simulator compilation, webApp JS
  bundle, and initial browser render all pass. **Pending after M5 provides an observable data
  surface:** Google-authenticated Firestore hydration/push round-trip in the browser.
- **M5 — ✅ code-complete; signed-in data verification pending.** `webApp` now routes
  completed auth into the shared fleet dashboard and aircraft overview tabs. The fleet,
  aircraft, log, task, and squawk read graph has JS targets and real Koin bindings.
  The web host supplies no mutation destinations, so add/edit/delete controls remain
  absent until M6; it also suppresses attachment presentation while browser blob handling
  remains deferred. **Verified:** webApp JS bundle, Android and iOS simulator
  compilation, and initial browser render pass. **Pending:** sign in with a populated
  account and observe hydrated fleet/detail data in the browser.
- **M6 — 🚧 in progress.** Browser routes now expose aircraft, maintenance log, task,
  and squawk add/edit flows, plus Settings, Cloud Sync, Feature Lab, and technician
  management. Browser attachment controls remain suppressed until blob handling is
  implemented; export UI is still deferred. Anonymous sign-in / account upgrade on web is
  dropped (see "Web auth scope narrowed" below) — web requires a real account.
  **Verified:** webApp JS bundle plus Android and iOS simulator compilation pass.
- **Web live-update fix (2026-05-24) — ✅ landed.** On web, adding an aircraft (or hydrating
  on sign-in) did not refresh the dashboard until a page reload. Root cause: SQLDelight routes a
  write's change-notification to `driver.currentTransaction()`, which is thread-confined on
  Android/iOS but a single shared field on the web `WebWorkerDriver`. Because web runs everything
  on one thread and the sync engine keeps suspending `db.transaction {}` blocks open across
  `await()`s (`HydrationRunner`, `PullListener`), a concurrent UI `EntityStore.put` had its
  `entity` notification captured by an open sync transaction — delivered late on commit, or
  dropped on rollback — so live `asFlow` queries never fired. Fix: a shared `DatabaseWriteLock`
  (`core:storage`) `Mutex` now serializes every DB write-unit (standalone mutations and
  transactions) across all writers and all platforms, so notifications always fire immediately and
  transactions never nest-corrupt. **Verified:** Android/JS/iOS compilation and affected Android
  unit tests pass; in-browser add/hydrate live refresh still to be confirmed in a running web build.

- **Web auth scope narrowed (2026-05-24) — ✅ landed.** Web requires a real account: anonymous
  (guest) sign-in is no longer offered. The login screen hides the "Continue without account"
  option on web (`feature/login` `isAnonymousLoginSupported = false`), and `jsMain`
  `AuthManagerImpl.signInAnonymously()` is now an explicit unsupported no-op. Consequently
  **account upgrade on web is dropped** (there are no anonymous users to upgrade);
  `upgradeAnonymousAccount()` stays a no-op stub. The login screen also now shows a
  "Continue with Apple" button on all platforms (UI only; provider not yet wired).
- **M7 — ✅ code-complete; browser persistence verification pending.** The interim
  sql.js→IndexedDB worker is replaced by the official `@sqlite.org/sqlite-wasm` build on OPFS via
  its SAH-Pool VFS (`webApp/src/jsMain/resources/sqlite-wasm-opfs.worker.js`). It speaks the same
  `WebWorkerDriver` message protocol, so `EntityStore`/managers/UI are untouched — only the worker,
  the JS `DriverFactory`, and webpack wiring change. `DriverFactory.js.kt` now does version-aware
  create/migrate via `PRAGMA user_version` (replacing the "swallow Schema.create" hack): it enqueues
  `Schema.create` first (so tables exist before any app query on a fresh DB), and on an existing DB
  reads `user_version` and runs `Schema.migrate` when behind. **Note:** this is a backend swap, not
  a data migration — existing web users' sql.js/IndexedDB data is not carried into OPFS; the local
  store starts empty and re-hydrates from Firestore on next sign-in (acceptable pre-production).
  **Verified:** webApp JS dev bundle builds — the worker, `@sqlite.org/sqlite-wasm`, the OPFS async
  proxy, and the copied `sqlite3.wasm` all emit; `core:storage` JS compiles. **Pending:** in a real
  browser, confirm write→reload persistence and schema migration over OPFS.

**What's next (in order):**
1. In a running web build, confirm (a) the live-update fix end-to-end (sign in with a populated
   account → hydrated fleet/detail data appears without a reload; an added aircraft shows on the
   dashboard immediately) and (b) M7 OPFS durability (write → reload → data persists). These close
   the browser-runtime verification carried since M4/M5 and for M7.
2. The remaining M6 surfaces (export UI, browser attachments) stay deferred;
   anonymous/account-upgrade on web is dropped rather than deferred.

_(Done: a real Firebase web app is registered; its `appId` is wired into
`webApp/src/jsMain/kotlin/main.kt`.)_

> **Gotcha discovered in M1:** `components-resources` must be declared *directly* in a
> module's dependencies (not relied on transitively via `core:ui`'s `api`) — the Compose
> Resources Gradle plugin only wires that module's generated `Res` class onto the compile
> path when the dependency is present locally. This is an exception to the repo rule about
> not redeclaring `core:ui`-exported deps.

## Goal & strategy

Turn the standalone `webApp` login mock into a functioning Hopply web client that
reuses the existing KMM shared code rather than reimplementing it. The work is
fundamentally **adding a `js(IR)` target to the shared module graph, bottom-up**, and
filling the per-platform gaps (auth, storage driver, dispatchers, background work) as
each layer comes online.

Guiding constraints:
- **JS target only** (not wasmJs) — a deliberate project decision.
- Each milestone is independently buildable and demoable; we never leave `main` /
  the branch in a non-compiling state.
- Prefer reusing shared modules over duplicating. The duplicated color tokens and
  fonts currently in `webApp` are temporary (M0 scaffolding) and get deleted in M2.

### Enabling fact: GitLive Firebase has JS artifacts
`dev.gitlive:firebase-{auth,firestore,functions,storage}:2.4.0` all publish JS
variants. Auth, the local-first sync engine's Firestore client, and export delivery
can therefore run on web with the same `commonMain` code — only initialization
(loading + configuring the Firebase JS SDK) is web-specific.

### Known cross-cutting blockers (resolve as they surface)
- `Dispatchers.IO` is **JVM/Native only** — unavailable on JS. `AppEntry` and likely
  several managers use it. Introduce an `expect`/`actual` IO dispatcher (or route
  through a Koin-provided `CoroutineDispatcher`) — JS maps it to `Dispatchers.Default`.
- **SQLDelight** has no android/native driver on web — use the
  `web-worker-driver` (sql.js, IndexedDB-backed) in a `jsMain` `DriverFactory`.
- **Background work**: the sync engine's blob drivers use Android WorkManager and iOS
  background `URLSession`. Web has no equivalent — blob transfer (R2/attachments) is
  deferred; provide a no-op/foreground driver so `feature:sync:data` compiles.
- **Navigation**: `AppEntry` uses `androidx.navigation.compose`. Verify the
  navigation-compose JS variant resolves at the repo's versions before relying on it
  (M5); fall back to a hand-rolled state-based navigator if not.
- **Compose tooling**: `compose-ui-tooling-preview` (api-exported by `core:ui`) may
  lack a clean JS variant — may need to move it out of `commonMain` api.

---

## Milestone 0 — Standalone login renders ✅ (done)
`webApp` JS module builds and serves a static login screen.
- Fixed during review: `moduleName` → `outputModuleName`; `CanvasBasedWindow`
  (deprecated/experimental) → `ComposeViewport`; HTML `<canvas>` → `<div>` container.
- Run locally: `./gradlew :webApp:jsBrowserDevelopmentRun` (dev/unminified — fast).
  Avoid `jsBrowserProductionWebpack` while iterating (Terser on ~42 MiB = minutes).

> **Note (M1/M2 ordering):** M1 (presentation: model/theme) leads because it is the
> lower-risk refactor *and* the more foundational one: adding a `js` target to
> `core:model`/`core:ui` is the first real test of whether the shared Kotlin graph
> compiles to JS at all, and it blocks M3–M6. M2 (extract `feature/login` + auth)
> builds directly on M1's shared `core:ui` and brings up `core:auth` on JS.

## Milestone 1 — Share the design system & model
**Goal:** web renders with the real `WingslogTheme`; delete `webApp`'s duplicated
tokens/fonts. Also the foundational JS-compile test for the whole shared graph.
- Add `js(IR)` to `core:model`, then `core:datetime`, then `core:ui` (bottom-up).
- Resolve `core:ui`'s api-exported transitive deps on JS: coil
  (`coil-compose`/`coil-network-ktor3`), `compose-ui-tooling-preview`,
  `ktor-client-okhttp` is androidMain-only so JS is unaffected. Move/guard any dep
  without a JS variant.
- Swap `webApp`'s local `AviationBlue*` constants and bundled Space Grotesk fonts for
  `core:ui` theme + resources; remove the duplicates.
- **Demo:** login screen rendered via shared theme, pixel-consistent with the apps.

## Milestone 2 — Extract `feature/login` and real auth on web
**Goal:** `webApp` reuses the *real production* `LoginScreen` (no more mock) with
working Google/anonymous sign-in — and `composeApp`'s local copy is deleted, leaving a
single source.

**Why extract instead of reuse in place:** the screen itself is portable; the blocker is
that it lives in `composeApp`, whose dependency closure is the entire app (sync, storage,
every feature, navigation, the god-level `initKoin`). `composeApp` can never compile to
JS. Pulling `LoginScreen` into its own `feature/login` module shrinks its closure to
`core:auth` + `core:ui` + its own resources — a small, JS-capable island. Keeping
`core:auth` as a dependency is fine (deliberately accepted): its `commonMain` is only
gitlive-firebase-auth + koin + kermit (all JS-capable), and the Android-only bits
(play-services-auth, credentials, googleid) stay in `androidMain`.

Order matters — keep Android/iOS green at every step:
1. **Create `feature/login`** (flat module, like `feature/settings`): move `LoginScreen`
   + `LoginViewModel`. Depends on `:core:auth` + `:core:ui`. Android + iOS targets first.
2. **Resource placement** — shared asset → common lib; feature-only asset → the feature:
   - `ic_launcher_foreground` (the Hopply brand mark) is used by `LoginScreen`,
     `NameEntryScreen`, *and* `WelcomeScreen` → move it into **`core:ui`** and repoint all
     three. Same brand mark, single source (no generic-vector substitution).
   - `ic_google_rd_na` + the `google_logo`/login strings are login-only → move into
     **`feature/login`**.
   - The `app/src/main/res` launcher mipmap and `drawable-night` Google variant are
     separate Android platform resources — leave them.
3. **Rewire `composeApp` `AppEntry`** to the extracted screen; delete `composeApp/login/`.
   Verify Android + iOS still build before going further.
4. **Add `js(IR)` to `feature/login` and `core:auth`.** `jsMain` `AuthManagerImpl` does
   anonymous / silent / sign-out through GitLive. Google sign-in is a **deliberate
   hybrid**: GitLive exposes no `signInWithPopup`, so `core/auth/src/jsMain/FirebaseAuthJs.kt`
   declares minimal `@JsModule("firebase/auth")` externals (`getAuth`, `signInWithPopup`,
   `GoogleAuthProvider`) and calls the raw Firebase JS SDK directly. Both `getAuth()` and
   GitLive's `Firebase.auth` resolve the *same* default-app Auth singleton, so after the
   popup resolves the result surfaces through GitLive's `currentUser` — no special-casing
   downstream. `webApp.main()` initializes the Firebase JS app via `Firebase.initialize`.
5. **Point `webApp` at `feature/login`**; delete the mock `LoginScreen.kt` + duplicate
   strings. Because `core:auth` is a real dependency, the reused screen and working
   sign-in land together — no dead-button interim state.
- **Demo:** click "Continue with Google" → real Google account → signed-in state. Same
  screen renders on Android, iOS, and web from one source.

## Milestone 3 — Local-first storage on web
**Goal:** `EntityStore` works on web.
- Add `js(IR)` to `core:storage`; `core/storage/src/jsMain/DriverFactory.js.kt` uses
  SQLDelight `web-worker-driver` over a sql.js worker. The JS `DriverFactory` takes the
  `Worker` from the host app and swallows `Schema.create` "table exists" on reload.
- Persistence: SQLDelight ships no persistent web worker, so `webApp` bundles a small
  **custom sql.js → IndexedDB worker** (`sqljs-idb.worker.js`): whole-file `db.export()`
  snapshot to IndexedDB after writes, restored on startup. **Interim** — migration to the
  maintained, OPFS-backed engine is tracked as **M7**.
- **Demo:** write data on web, reload the page, confirm it's still there (IndexedDB).

## Milestone 4 — Sync engine on web
**Goal:** signed-in web user's local store syncs with Firestore.
- Add `js(IR)` to `feature:sync:data`. Pull/push use GitLive Firestore (JS-ready).
- Provide JS blob drivers as **no-op/foreground stubs** (attachments are gated behind
  `attachmentUploadEnabled` anyway) so the module compiles without WorkManager.
- Confirm `SyncEngine` gating (signed-in & non-anonymous & cloud-sync-enabled) and
  hydration run on web.
- **Demo:** data created on phone appears on web after sign-in, and vice versa.

## Milestone 5 — App shell + read-only fleet
**Goal:** post-login web app showing the user's fleet (read path first).
- Resolve the `Dispatchers.IO` blocker (expect/actual IO dispatcher).
- Verify/establish navigation on JS (androidx navigation-compose, else custom).
- Add `js(IR)` to the read-path feature modules: `feature:fleet:*`,
  `feature:aircraft:dashboard`, and the `model`/`datamanager`/`viewing`/`sharedassets`
  of `logs`, `tasks`, `squawk`. Port `AppEntry`'s graph (or a web-tailored subset).
- Mutation controls render only when their host supplies edit destinations; web omits
  them until M6 wires those destinations, without a temporary `readOnly` mode flag.
- **Demo:** sign in → fleet dashboard → aircraft overview tabs, all read-only, on web.

## Milestone 6 — Editing & remaining features
**Goal:** chosen web scope reaches parity.
- Add `js(IR)` to `update/` modules (logs, tasks, squawk, aircraft edit), plus
  `technician`, `settings`, `featurelab`, and `export` as scoped.
- Re-evaluate attachments (R2) on web — real blob upload/download via fetch, or keep
  gated.
- **Demo:** create/edit aircraft, logs, tasks, squawks from the browser.

## Milestone 7 — Durable web storage: migrate to sqlite-wasm + OPFS ✅ code-complete (browser verification pending)
**Goal:** replace the interim sql.js→IndexedDB worker (M3) with a maintained, scalable,
durable backend.
- **Why:** the M3 worker snapshots the *entire* DB to IndexedDB on every write (O(db size),
  doesn't scale) and is bespoke code we own. The long-term target is the official
  **`@sqlite.org/sqlite-wasm`** build on **OPFS** using its **SyncAccessHandle-Pool VFS**.
- **Why this specific stack:**
  - OPFS is the browser's purpose-built persistent file storage (real block I/O, scales).
  - `@sqlite.org/sqlite-wasm` is maintained by the SQLite authors (unlike `absurd-sql`,
    which is effectively abandoned and needs `SharedArrayBuffer`).
  - The SAH-Pool VFS needs **no `COOP`/`COEP` headers** — important because those headers
    break Firebase `signInWithPopup` (the app's Google sign-in).
- **Work:** write a worker that bridges SQLDelight's `WebWorkerDriver` message protocol
  (`exec` / `*_transaction`) to the sqlite-wasm OO1 API over an OPFS SAH-Pool VFS. Add
  version-aware create/migrate (replacing the M3 "swallow Schema.create" hack). Only the
  worker + JS `DriverFactory` change — `EntityStore`/managers/UI are untouched (the backend
  is isolated behind that seam).
- **Demo:** same as M3 (write → reload → data persists), but durable and scalable, and with
  schema migrations handled.

---

## Module target-coverage checklist
Track which shared modules have gained a `js(IR)` target (✅ = has JS target):

| Module | JS target | Milestone |
|--------|-----------|-----------|
| `webApp` | ✅ | M0 |
| `core:model` | ✅ | M1 |
| `core:datetime` | ✅ | M1 |
| `core:ui` | ✅ | M1 |
| `core:auth` | ✅ | M2 |
| `feature:login` (new — extracted from `composeApp`) | ✅ | M2 |
| `core:storage` | ✅ | M3 |
| `core:firebase`, `feature:attachment:{model,datamanager}` | ✅ | M4 |
| `feature:sync:data`, `feature:technician:datamanager` | ✅ | M4 |
| `feature:fleet:*`, `feature:aircraft:dashboard` | ✅ | M5 |
| `feature:{logs,tasks,squawk}:{model,datamanager,viewing,sharedassets}` | ✅ | M5 |
| `feature:attachment:{sharedassets,viewing}`, `feature:featurelab:datamanager` | ✅ | M5 dependency |
| `feature:{logs,tasks,squawk}:update`, `feature:technician:{manage,sharedassets}` | ✅ | M6 |
| `core:appinfo`, `feature:settings`, `feature:sync:{settings,sharedassets}` | ✅ | M6 dependency |
| `feature:userprofile:{sharedassets,userprofilecard}`, `feature:export:sharedassets` | ✅ | M6 dependency |
| `feature:export:{datamanager,update}`, browser attachments | ☐ | M6 deferred |
| anonymous sign-in / account upgrade on web | ✖︎ | dropped — web requires a real account |

## Open questions
- Is the web client full parity, or a focused subset (e.g. view + light edit)? This
  decides how far M6 goes.
- Firebase JS config delivery: hand-written `index.html` snippet vs. generated from
  the same source as `google-services.json`?
- Persistence backing for sql.js on web: IndexedDB durability expectations vs. the
  local-first guarantees the mobile apps assume.
