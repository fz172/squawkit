# WingsLog: KMP Migration Design Doc

<aside>
🗺️ Phase order: (1) KMP Migration — Android-first, (2) Web, (3) iOS

Current status: Step 1.2 (Hilt → Koin) ✅ complete. Next: Step 1.0 (Protobuf to Square Wire).

</aside>

# Overview

WingsLog is currently an Android-only app built with Jetpack Compose, Hilt, Firebase/Firestore, and Protobuf. This document outlines a phased migration to Kotlin Multiplatform (KMP) with Compose Multiplatform UI, Koin DI, and the GitLive Firebase KMP wrapper — enabling Android, Web, and iOS clients from a shared codebase while keeping Firebase as the backend.

# Current Stack

- Language: Kotlin (Android-only)
- UI: Jetpack Compose (androidx.compose.\*)
- DI: Hilt (Dagger, annotation processing, Android-only)
- Backend: Firebase Firestore + Firebase Auth
- Data model: Protobuf (protobuf-kotlin-lite)
- Navigation: androidx.navigation.compose
- Image loading: Coil (Compose)
- Build: Gradle multi-module (app, feature/_, core/_)

# Target Stack (Post-Migration)

- UI: Compose Multiplatform (JetBrains) — shared across Android, Web, iOS
- DI: Koin — KMP native, no annotation processing
- Backend: Firebase Firestore + Firebase Auth (via GitLive firebase-kotlin-sdk KMP wrapper)
- Data model: Protobuf via Square Wire (KMP compatible generator and runtime)
- Navigation: Compose Multiplatform Navigation or Voyager
- Image loading: Coil 3.x (KMP support)
- Auth: Firebase Auth KMP (Google Sign-In, Apple Sign-In via platform expect/actual)

---

# Phase 1: KMP Migration (Android-first) 

Goal: Restructure the codebase to KMP without changing any user-visible behavior on Android. The app should remain fully functional on Android throughout this phase.

## Step 1.0 — Data Model: Protobuf Lite → Square Wire  ✅ DONE

The official `protobuf-kotlin-lite` is JVM-bound and cannot be compiled to KMP targets like iOS or Web (Wasm). Before migrating Android modules to KMP, we must switch to a KMP-compatible Protobuf library.

- Replace `com.google.protobuf:protobuf-kotlin-lite` with Square `Wire` (`com.squareup.wire:wire-runtime`).
- Update Gradle build scripts to use the Wire Gradle plugin (`com.squareup.wire`) instead of the standard Protobuf plugin.
- Regenerate Protobuf models using the Wire compiler.
- Update all Firestore Managers that perform Protobuf serialization to use their respective Wire `ADAPTER` methods (e.g., `ADAPTER.encode()` and `ADAPTER.decode()`) instead of Google's `toByteArray()` and `parseFrom()`. This specifically includes:
  - `AircraftManagerImpl` (Aircraft)
  - `MaintenanceLogManagerImpl` (MaintenanceLog)
  - `InspectionManagerImpl` (Inspection)
  - `UserProfileManagerImpl` (UserProfile)
  - `FleetDashboardManagerImpl` (Aircraft parsing)
- Verify the Android app still runs successfully reading/writing existing Firestore blobs.

## Step 1.1 — Build System: Android → KMP modules

Convert every module from android.library to the kotlin.multiplatform plugin. This is a mechanical change — no Kotlin code changes yet.

- Replace `plugins { alias(libs.plugins.android.library) }` with `kotlin("multiplatform")` + `android()` target block in each core/_ and feature/_ module
- Move all existing Kotlin source files to `src/androidMain/kotlin/` (not commonMain yet — that comes later)
- Add `commonMain` source set (empty for now) to each module
- Verify Android app still builds and runs — no regressions
- Update libs.versions.toml: add kotlin-multiplatform plugin alias

## Step 1.2 — DI: Hilt → Koin ✅ DONE (merged PR #1, 2026-03-18)

Hilt is Android-only. Koin is pure Kotlin and works on all KMP targets. This is the most code-touching step.

- Add Koin dependencies: koin-android, koin-compose, koin-core to libs.versions.toml
- Remove all @HiltViewModel, @Inject constructor, @Module, @Provides, @InstallIn annotations
- Create Koin modules per feature: `val aircraftModule = module { viewModel { AircraftOverviewViewModel(get()) } }`
- Replace HiltAndroidApp with `startKoin { androidContext(this@App); modules(allModules) }` in Application
- Replace `hiltViewModel()` composable calls with `koinViewModel()`
- Remove Hilt from all build.gradle.kts files, remove KSP hilt-compiler
- Modules affected: all feature/\*/database modules (Managers), app module

## Step 1.3 — Backend: Android Firebase SDK → Firebase KMP Wrapper

The GitLive firebase-kotlin-sdk wraps the official Firebase SDKs with a KMP-compatible API. The Firestore data structure, auth flow, and google-services.json config all stay exactly the same — only the import paths change from com.google.firebase to dev.gitlive.firebase. This is the lowest-risk migration path for the backend.

### Migration Steps

No schema changes needed — Firestore collections stay intact. The wrapper mirrors the official Firebase API:

```kotlin
// Before (Android-only)
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

val db = Firebase.firestore
db.collection("aircraft").document(id).set(data)

// After (KMP — commonMain)
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore

val db = Firebase.firestore
db.collection("aircraft").document(id).set(data)  // identical API
```

### Manager Implementation Changes

- Add firebase-kotlin-sdk dependencies: firebase-firestore, firebase-auth, firebase-storage (from dev.gitlive)
- Keep google-services.json and existing Firebase project — no Firebase console changes needed
- Move AircraftManagerImpl, MaintenanceLogManagerImpl, InspectionManagerImpl to commonMain — change import paths only
- Firestore snapshotListeners work the same via callbackFlow — no logic changes in Managers
- Protobuf serialization uses Square Wire ADAPTER methods instead of Google Protobuf methods
- Auth: Firebase Auth KMP works the same as Android SDK — Google Sign-In flow unchanged
- Platform-specific Firebase initialization (FirebaseApp.initializeApp) stays in androidMain/iosMain via expect/actual
- Remove com.google.firebase:_ Android SDK deps from commonMain — replace with dev.gitlive:firebase-_; keep Android-specific google-services plugin in app/build.gradle.kts

## Step 1.4 — UI: Jetpack Compose → Compose Multiplatform

API surface is nearly identical — most composables just change their import path. Material3 is fully supported.

- Replace `androidx.compose.*` BOM with JetBrains Compose Multiplatform BOM
- Update imports: `androidx.compose.material3.*` → `androidx.compose.material3.*` (same for Material3, different for foundation/runtime)
- Move UI composables to `commonMain` source set in core:ui and feature modules
- Replace androidx.navigation.compose with Compose Multiplatform Navigation (org.jetbrains.androidx.navigation)
- Replace Coil Compose with Coil 3.x KMP (io.coil-kt.coil3:coil-compose-core)
- Any Android-specific UI code (Activity, lifecycle hooks) stays in androidMain via expect/actual

## Phase 2 Completion Criteria

- Web build produces deployable Wasm bundle
- Login, fleet view, aircraft overview, maintenance log all functional in browser
- Deployed to a staging URL

---

# Phase 3: iOS Target

Add iOS (Swift/Objective-C interop via Kotlin/Native). Compose Multiplatform on iOS is production-ready as of 2024.

- Add iosArm64(), iosSimulatorArm64(), iosX64() targets to build config
- Create an Xcode project wrapping the KMP framework (use KMP Xcode plugin or create manually)
- iOS entry point: MainViewController.kt in iosMain calls ComposeUIViewController { App() }
- Firebase KMP supports iOS via the native Firebase iOS SDK under the hood — firebase-kotlin-sdk wraps it automatically
- Auth: Apple Sign-In + Google Sign-In via Firebase Auth KMP — add GoogleService-Info.plist to iOS target
- Platform quirks: iOS keyboard handling, safe area insets, back gesture — test thoroughly
- File attachments: UIImagePickerController / PHPicker via expect/actual
- Distribute via TestFlight for initial testing, then App Store

## Phase 3 Completion Criteria

- iOS app installs and runs on device via TestFlight
- Feature parity with Android
- Apple Sign-In implemented (required by App Store for social auth apps)

---

# Risks & Mitigations

- GitLive firebase-kotlin-sdk is community-maintained (not official Google). It tracks Firebase SDK releases but may occasionally lag. Pin versions carefully and watch the GitHub releases.
- No data migration needed — existing Firestore data is untouched. This is the biggest advantage over switching to Supabase.
- Koin runtime errors: add checkModules() in tests to catch missing bindings early
- Compose Multiplatform Web (Wasm): still maturing, pin stable CMP version and update carefully
- Kotlin/Native compile times: iOS builds are slow — use Xcode incremental builds and Kotlin caching flags

---

# Dependency Reference

```
# Key additions to libs.versions.toml
koin = "4.0.0"
firebase-kotlin-sdk = "2.1.0"          # dev.gitlive:firebase-*
compose-multiplatform = "1.8.0"
coil3 = "3.1.0"
ktor = "3.1.0"
wire = "5.1.0"

# Koin
koin-core = { module = "io.insert-koin:koin-core" }
koin-android = { module = "io.insert-koin:koin-android" }
koin-compose = { module = "io.insert-koin:koin-compose" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel" }

# Firebase KMP (GitLive wrapper)
firebase-firestore = { module = "dev.gitlive:firebase-firestore" }
firebase-auth = { module = "dev.gitlive:firebase-auth" }
firebase-storage = { module = "dev.gitlive:firebase-storage" }

# Compose Multiplatform
# org.jetbrains.compose plugin replaces androidx BOM
# org.jetbrains.androidx.navigation replaces androidx.navigation.compose

# Coil 3 (KMP)
coil-compose-core = { module = "io.coil-kt.coil3:coil-compose-core" }

# Keep in app/build.gradle.kts (Android-only)
# com.google.gms.google-services plugin
# google-services.json stays in app/
```
