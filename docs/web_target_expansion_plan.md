# Web Target Expansion Plan

**Implementation Status (2026-05-24):** Milestone 0 shipped — a standalone `webApp`
Kotlin/JS module renders a static login screen (`ComposeViewport` + Compose MP, no
shared code). **M1 compile path validated:** `core:model`, `core:datetime`, and
`core:ui` now declare a `js(IR)` target and all compile clean to JS (Wire protobuf,
coil, ktor-client-core, `compose-ui-tooling-preview`, material-icons-extended all have
JS variants — none were blockers). JS `actual`s added for `toWireInstant`,
`platformColorScheme` (returns null → aviation palette), and `rememberBrandHeadlineFamily`
(Space Grotesk). **M1 complete:** `webApp` now depends on `:core:ui`, renders through
`WingslogTheme`, and its duplicated color tokens + bundled Space Grotesk fonts have been
deleted — the first proof a downstream JS consumer can actually *use* the shared modules.
Next: M2 — extract `LoginScreen` into a new `feature/login` module and bring up
`core:auth` on JS, so `webApp` and `composeApp` share one real login screen with working
sign-in. This doc is the ordered plan to grow that seed into a real, code-sharing web
client.

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
4. **Add `js(IR)` to `feature/login` and `core:auth`.** Add
   `core/auth/src/jsMain/.../AuthManagerImpl.kt`: Google via Firebase JS `signInWithPopup`
   (GitLive `GoogleAuthProvider`), `signInAnonymously`, sign-out. Initialize the Firebase
   JS SDK in `webApp` (config in `index.html` or generated) so GitLive has an app to
   attach to.
5. **Point `webApp` at `feature/login`**; delete the mock `LoginScreen.kt` + duplicate
   strings. Because `core:auth` is a real dependency, the reused screen and working
   sign-in land together — no dead-button interim state.
- **Demo:** click "Continue with Google" → real Google account → signed-in state. Same
  screen renders on Android, iOS, and web from one source.

## Milestone 3 — Local-first storage on web
**Goal:** `EntityStore` works on web.
- Add `js(IR)` to `core:storage`; add `core/storage/src/jsMain/DriverFactory.js.kt`
  using SQLDelight `web-worker-driver` (sql.js worker asset wired into the webpack
  bundle / `index.html`).
- Provide JS Koin bindings; verify schema creation + a read/write round-trip.
- **Demo:** write an entity locally on web, read it back across reload (IndexedDB).

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
- **Demo:** sign in → fleet dashboard → aircraft overview tabs, all read-only, on web.

## Milestone 6 — Editing & remaining features
**Goal:** chosen web scope reaches parity.
- Add `js(IR)` to `update/` modules (logs, tasks, squawk, aircraft edit), plus
  `technician`, `settings`, `featurelab`, and `export` as scoped.
- Re-evaluate attachments (R2) on web — real blob upload/download via fetch, or keep
  gated.
- **Demo:** create/edit aircraft, logs, tasks, squawks from the browser.

---

## Module target-coverage checklist
Track which shared modules have gained a `js(IR)` target (✅ = has JS target):

| Module | JS target | Milestone |
|--------|-----------|-----------|
| `webApp` | ✅ | M0 |
| `core:model` | ✅ | M1 |
| `core:datetime` | ✅ | M1 |
| `core:ui` | ✅ | M1 |
| `core:auth` | ☐ | M2 |
| `feature:login` (new — extracted from `composeApp`) | ☐ | M2 |
| `core:storage` | ☐ | M3 |
| `feature:sync:data` | ☐ | M4 |
| `feature:fleet:*`, `feature:aircraft:dashboard` | ☐ | M5 |
| `feature:{logs,tasks,squawk}:{model,datamanager,viewing,sharedassets}` | ☐ | M5 |
| `feature:*:update`, `technician`, `settings`, `featurelab`, `export` | ☐ | M6 |

## Open questions
- Is the web client full parity, or a focused subset (e.g. view + light edit)? This
  decides how far M6 goes.
- Firebase JS config delivery: hand-written `index.html` snippet vs. generated from
  the same source as `google-services.json`?
- Persistence backing for sql.js on web: IndexedDB durability expectations vs. the
  local-first guarantees the mobile apps assume.
