# WingsLog: KMP Migration Design Doc

> **Phase order:** (1) KMP Migration — Android-first, (2) iOS, (3) Web (On Hold)
>
> **Current status:** Steps 1.0, 1.1, 1.2, 1.5 complete. Step 1.3 (Firebase KMP) in progress. Step
> 1.4 (Compose MP) not started.

---

# Overview

WingsLog is currently an Android-only app built with Jetpack Compose, Koin, Firebase/Firestore, and
Square Wire Protobuf. This document outlines a phased migration to Kotlin Multiplatform (KMP) with
Compose Multiplatform UI and the GitLive Firebase KMP wrapper — enabling Android, Web, and iOS
clients from a shared codebase while keeping Firebase as the backend.

---

# Current Stack

| Layer         | Library                                                                     |
|---------------|-----------------------------------------------------------------------------|
| Language      | Kotlin (Android-only → migrating to KMP)                                    |
| UI            | Jetpack Compose (`androidx.compose.*`)                                      |
| DI            | Koin                                                                        |
| Backend       | Firebase Firestore + Firebase Auth                                          |
| Data model    | Square Wire (`com.squareup.wire`)                                           |
| Navigation    | `androidx.navigation.compose`                                               |
| Image loading | Coil (Compose)                                                              |
| Logging       | Kermit (`co.touchlab:kermit`)                                               |
| Build         | Gradle multi-module, all modules already have `kotlin.multiplatform` plugin |

---

# Target Stack (Post-Migration)

| Layer         | Library                                                                      |
|---------------|------------------------------------------------------------------------------|
| UI            | Compose Multiplatform (JetBrains) — shared across Android, Web, iOS          |
| DI            | Koin (already done ✅)                                                        |
| Backend       | Firebase Firestore + Firebase Auth via GitLive `firebase-kotlin-sdk`         |
| Data model    | Square Wire (already KMP-compatible ✅)                                       |
| Navigation    | Compose Multiplatform Navigation (`org.jetbrains.androidx.navigation`)       |
| Image loading | Coil 3.x (`io.coil-kt.coil3:coil-compose-core`)                              |
| Logging       | Kermit (already done ✅)                                                      |
| Auth          | Firebase Auth KMP (Google Sign-In, Apple Sign-In via platform expect/actual) |

---

# Phase 1: KMP Migration (Android-first)

**Goal:** Restructure the codebase to KMP without changing any user-visible behavior on Android. The
app should remain fully functional on Android throughout this phase.

---

## Step 1.0 — Data Model: Protobuf Lite → Square Wire ✅ DONE

Migrated from `com.google.protobuf:protobuf-kotlin-lite` (JVM-bound) to Square Wire (
`com.squareup.wire`), which is KMP-compatible.

- Wire Gradle plugin configured in all affected modules
- All Firestore Managers updated to use `ADAPTER.encode()` / `ADAPTER.decode()` instead of
  `toByteArray()` / `parseFrom()`
- Managers updated: `AircraftManagerImpl`, `MaintenanceLogManagerImpl`, `InspectionManagerImpl`,
  `UserProfileManagerImpl`, `FleetDashboardManagerImpl`

---

## Step 1.1 — Build System: Android → KMP modules ✅ DONE

All 13 library modules converted to use the `kotlin.multiplatform` plugin alongside
`android.library`. All existing Kotlin source files moved to `src/androidMain/kotlin/`.

**Modules migrated:**

- `core/model`, `core/ui`, `core/database`, `core/auth`
- `feature/aircraft`, `feature/aircraft/database`
- `feature/fleet`, `feature/fleet/database`
- `feature/settings`
- `feature/userprofile`, `feature/userprofile/database`, `feature/userprofile/userprofilecard`

---

## Step 1.2 — DI: Hilt → Koin ✅ DONE (PR #1, 2026-03-18)

Replaced Hilt (Android-only) with Koin across all modules.

- All `@HiltViewModel`, `@Inject`, `@Module`, `@InstallIn` annotations removed
- Per-feature Koin modules created (`di/` package in each feature)
- `Application` class uses `startKoin { ... }` with all modules registered
- All `hiltViewModel()` calls replaced with `koinViewModel()`

---

## Step 1.3 — Backend: Android Firebase SDK → Firebase KMP Wrapper ✅ DONE (2026-03-19)

**Strategy: Migrate module by module.**
Within a module, it is acceptable (and expected) to have both `com.google.firebase` and
`dev.gitlive.firebase` imports coexisting as an intermediate step. The goal is to fully remove
`com.google.firebase` from each module before considering that module done — not to do everything in
one pass.

**What's done:**

- `core/auth` module migrated: `GitLiveAuthManager` + `GitLiveAuthManagerImpl` in `commonMain`/
  `androidMain` using `dev.gitlive.firebase.auth`
- GitLive dependencies added to `libs.versions.toml` (`gitlive = "2.1.0"`)
- `core/database` has `CommonFirebaseModule` and `GitLiveDocumentReferences` in `commonMain`
- `feature/aircraft/database`, `feature/fleet/database`, `feature/userprofile/database` managers
  migrated and moved to `commonMain`
- `feature/settings`, `feature/fleet`, `feature/userprofile` UI states and ViewModels successfully
  updated to use `dev.gitlive.firebase`
- All Google Firebase SDK imports (`com.google.firebase`) have been fully replaced across all
  library modules

**Rest of GitLive Firebase to migrate:**

- The ViewModels (`FleetDashboardViewModel`, `EditProfileViewModel`) and UI State classes (
  `SettingsUiState`) that use `dev.gitlive.firebase` are currently still located in `androidMain`
  because they depend on `androidx.compose.*` or Android ViewModels.
- These will be moved to `commonMain` during Step 1.4 once Compose Multiplatform is configured.

**Module completion criteria:** A module is considered fully migrated when it has **zero**
`com.google.firebase` imports remaining (GitLive only).

**Migration pattern** (import swap only — API is identical):

```kotlin
// Before (Android-only)
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// After (KMP — can move to commonMain)
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
```

**Notes:**

- `google-services.json` and existing Firestore collections stay unchanged
- Wire `ADAPTER` calls are already in place — no serialization changes needed
- Once all imports in a module are swapped, files can move from `androidMain` → `commonMain`
- `app/` module may retain Google Firebase plugin config (`google-services` plugin,
  `google-services.json`) — this is intentional and separate from the SDK migration

---

## Step 1.4 — UI: Jetpack Compose → Compose Multiplatform ⏳ NOT STARTED

31 UI files currently use `androidx.compose.*` imports. API surface is nearly identical — mostly
import path changes.

**Strategy: Staged feature-by-feature transition.**
Since JetBrains Compose Multiplatform binary-matches Jetpack Compose on Android, this migration will be executed in sequential stages to guarantee stability.

**Sub-Steps:**
- **Step 1.4.1:** Build Layer & Core — Apply CMP plugin (`1.7.3`) to root `build.gradle.kts` and `libs.versions.toml`. Migrate `core/ui` dependencies to CMP, moving foundational UI (Themes, etc.) to `commonMain`.
- **Step 1.4.2:** Feature Migration — Sequentially migrate modules (Apply CMP plugin, configure CMP ViewModel, move `androidMain` files to `commonMain`):
    - **Step 1.4.2.1:** `feature/settings`
    - **Step 1.4.2.2:** `feature/userprofile` (Handle `android.net.Uri` appropriately)
    - **Step 1.4.2.3:** `feature/fleet`
    - **Step 1.4.2.4:** `feature/aircraft`
- **Step 1.4.3:** Module Restructuring & App-Level Dependencies
    - Create a new `composeApp` (or `shared-app`) KMP library module. Move `AppEntry.kt` and cross-platform Koin initialization here (this becomes the shared UI root).
    - Modify the existing `app` module to remain a pure Android Application. It will depend on `composeApp` and simply set `setContent { AppEntry() }` in `MainActivity.kt`.
    - Replace `androidx.navigation.compose` with CMP Navigation globally.
    - Update Coil to Coil 3.x KMP.

---

## Step 1.5 — Resources: Android strings.xml → Compose Multiplatform Resources ✅ DONE

Hardcoded strings externalized to `strings.xml` and migration guide written (`docs/kmp_migration.md`
companion). See `docs/` for Compose Multiplatform resource migration notes.

---

## Step 1.6 — Logging: Flogger → Kermit ✅ DONE (2026-03-19)

Replaced Google Flogger (JVM-only) with TouchLab Kermit (`co.touchlab:kermit`, KMP-compatible).

- `FluentLogger.forEnclosingClass()` → `Logger.withTag("ClassName")`
- Log level methods updated: `atInfo().log()` → `i { }`, `atWarning()` → `w { }`, etc.
- Flogger fully removed from `libs.versions.toml` and all `build.gradle.kts` files

---

## Phase 1 Completion Criteria

- [ ] All Managers migrated to GitLive Firebase SDK (Step 1.3)
- [ ] All UI composables moved to `commonMain` with Compose Multiplatform (Step 1.4)
- [ ] Android app builds and all features work end-to-end
- [ ] No `com.google.firebase` imports remaining outside of `app/` module
- [ ] No `androidx.compose` imports in `commonMain`

---

# Phase 2: iOS Target

Add iOS via Kotlin/Native. Compose Multiplatform on iOS is production-ready as of 2024. This will build upon the `composeApp` shared UI module created in Phase 1.

- Add `iosArm64()`, `iosSimulatorArm64()`, `iosX64()` targets to the `composeApp` build config.
- Create Xcode project (`iosApp`) wrapping the KMP framework.
- iOS entry point: `MainViewController.kt` in `iosMain` calling `ComposeUIViewController { AppEntry() }`.
- Firebase KMP wraps native Firebase iOS SDK — add `GoogleService-Info.plist` to iOS target.
- Auth: Apple Sign-In + Google Sign-In via Firebase Auth KMP via `expect/actual`.
- File attachments: `UIImagePickerController` / `PHPicker` via `expect/actual`.
- Platform quirks: iOS keyboard, safe area insets, back gesture — test thoroughly.

**Completion criteria:**
- iOS app installs and runs on device simulator.
- Feature parity with Android.
- Authentication implemented.

---

# Phase 3: Web Target (On Hold)

*Note: Hold off on this phase until GitLive Firebase introduces `wasmJs` support, or we switch to a different Web backend.*

Add a Kotlin/Wasm web target by creating a dedicated web module. This keeps the build scripts clean without mixing Web and Android Application plugin.

- Create a new `webApp` module applying the KMP plugin with a `wasmJs()` target.
- Add `webApp` to `settings.gradle.kts`.
- Setup a `wasmJsMain` entry point (`Main.kt`) that depends on `composeApp` and launches `AppEntry()`.
- GitLive Firebase KMP does **not natively support** `wasmJs` currently, necessitating custom bindings or waiting for upstream updates.
- Auth: Google OAuth on web uses browser redirect flow.
- Navigation: URL/deep-link handling in browser via `window.location`.
- Deploy as static site (Vercel, Cloudflare Pages, etc.)

> ⚠️ Compose Multiplatform web is Wasm-based (not JS/DOM). Bundle sizes are large (~5MB+). Not SEO-friendly. Best for a logged-in dashboard, not a public landing page.

**Completion criteria:**
- Web build produces deployable Wasm bundle.
- Login, fleet view, aircraft overview, maintenance log functional in browser.
- Deployed to a staging URL.

---

# Risks & Mitigations

| Risk | Mitigation |
|---|---|
| GitLive `firebase-kotlin-sdk` is community-maintained, may lag Firebase releases | Pin versions carefully, watch GitHub releases |
| No data migration needed | GitLive Firebase uses the existing Firestore collections completely untouched |
| Koin runtime errors (no compile-time DI validation) | Add `checkModules()` in tests to catch missing bindings early |
| Compose Multiplatform Web (Wasm) still maturing | Pin stable CMP version, update carefully |
| Kotlin/Native compile times (iOS) | Use Xcode incremental builds and Kotlin caching flags |

---

# Dependency Reference

```toml
# libs.versions.toml — current + planned additions

# Already added ✅
koin = "4.0.4"
gitlive = "2.1.0"
wire = "5.1.0"
kermit = "2.0.5"

# To add for Step 1.4 (Compose Multiplatform)
compose-multiplatform = "1.8.0"     # org.jetbrains.compose plugin
coil3 = "3.1.0"                     # io.coil-kt.coil3:coil-compose-core

# To add for Phase 2/3 (Web/iOS HTTP clients)
ktor = "3.1.0"
# ktor-client-android  → androidMain
# ktor-client-darwin   → iosMain
# ktor-client-js       → wasmJsMain

# Keep in app/build.gradle.kts only
# com.google.gms.google-services plugin
# google-services.json stays in app/
```
