# WingsLog: iOS Support Guide

> **Status:** The codebase is iOS-ready at the architecture level. Phase 2 (iOS target) was
> intentionally skipped in favor of Phase 3 (Web), but the groundwork is in place. This document
> describes the architecture, what already works cross-platform, and exactly what steps are needed
> to ship an iOS app.

---

## 1. Architecture Overview

WingsLog is a **Kotlin Multiplatform (KMP)** project using **Compose Multiplatform** for shared UI.
The architecture follows a clean layered structure:

```
┌─────────────────────────────────────────────────┐
│              composeApp (Shared UI Root)         │
│  AppEntry.kt · Navigation · Koin initialization │
├─────────────────────────────────────────────────┤
│                Feature Modules                   │
│  feature:aircraft  feature:fleet                 │
│  feature:settings  feature:userprofile           │
├─────────────────────────────────────────────────┤
│               Core Modules                       │
│  core:auth   core:database   core:ui  core:model │
├─────────────────────────────────────────────────┤
│           Platform Entry Points                  │
│  app/ (Android)   iosApp/ (to be created)        │
└─────────────────────────────────────────────────┘
```

### Module Map

| Module | Purpose | KMP Status |
|---|---|---|
| `app/` | Android `Application` + `MainActivity` | Android-only (thin wrapper) |
| `composeApp/` | Shared `AppEntry.kt`, navigation, DI initialization | `commonMain`, `androidTarget`, `js` |
| `core/auth` | `AuthManager` interface + per-platform impl | `commonMain` interface, `androidMain`/`iosMain`/`jsMain` impls |
| `core/database` | Firestore document helpers (`setEncoded`, `getBlobAsBytes`, `observeSnapshot`) | `commonMain` `expect`, `androidMain`/`jsMain` `actual` — **iOS `actual` needed** |
| `core/model` | Square Wire protobuf models | `commonMain` (fully KMP) |
| `core/ui` | Compose themes, reusable composables, `DateTimeUtils` | `commonMain` + platform color scheme `actual` |
| `feature/aircraft` | Aircraft overview, edit, inspections, maintenance logs | `commonMain` |
| `feature/aircraft/database` | Firestore managers: `AircraftManager`, `InspectionManager`, `MaintenanceLogManager` | `commonMain` |
| `feature/fleet` | Dashboard screen | `commonMain` |
| `feature/fleet/database` | `FleetDashboardManager` | `commonMain` |
| `feature/settings` | Settings screen + ViewModel | `commonMain` |
| `feature/userprofile` | Edit profile screen + ViewModel | `commonMain` |
| `feature/userprofile/database` | `UserProfileManager` | `commonMain` |
| `feature/userprofile/userprofilecard` | Profile card composable | `commonMain` |

---

## 2. Current KMP Source Set Layout

Each module follows the standard KMP source set hierarchy:

```
module/
├── src/
│   ├── commonMain/kotlin/   ← Shared logic (most code lives here now)
│   ├── androidMain/kotlin/  ← Android-specific `actual` impls
│   ├── iosMain/kotlin/      ← iOS `actual` impls (stubs exist in core:auth)
│   └── jsMain/kotlin/       ← Web `actual` impls
```

The vast majority of code — all ViewModels, Composables, data managers, and navigation — is already
in `commonMain`. iOS-specific code is limited to `actual` declarations for platform seams.

---

## 3. Data Models

### 3.1 Protobuf Definitions (Square Wire)

All domain models are defined as `.proto` files and compiled by the Wire Gradle plugin. They are
fully KMP-compatible and live in `core/model`.

#### Aircraft Domain

```protobuf
// Aircraft.proto
message Aircraft {
  string id = 1;
  string make = 2;
  string model = 3;
  string serial = 4;
  string tail_number = 5;
  repeated Engine engine = 6;
}

// Engine.proto
message Engine {
  string make = 1;
  string model = 2;
  string serial = 3;
  Propeller propeller = 4;
}

// Propeller.proto
message Propeller {
  PropellerHub hub = 1;
  repeated PropellerBlade blades = 2;
}
message PropellerHub { string make = 1; string model = 2; string serial = 3; }
message PropellerBlade { string make = 1; string model = 2; string serial = 3; }
```

#### Maintenance Logging

```protobuf
// MaintenanceLog.proto
message MaintenanceLog {
  string id = 1;
  google.protobuf.Timestamp timestamp = 2;
  string technician_id = 3;
  string work_description = 4;
  enum ComponentType { UNKNOWN = 0; AIRFRAME = 1; ENGINE = 2; PROPELLER = 3; }
  ComponentType component_type = 5;
  string component_serial = 6;
  double tach_time = 8;
  repeated string attachment_urls = 9;
  double airframe_time = 10;
  double prop_time = 11;
  repeated string inspection_ids = 12; // Links to InspectionCard IDs
}
```

#### Inspection Cards

```protobuf
// InspectionCard.proto
message InspectionCard {
  string id = 1;
  string title = 2;
  InspectionComponentType component = 3;  // AIRFRAME | ENGINE | PROPELLER
  repeated InspectionRule rules = 4;
  google.protobuf.Timestamp force_due_date = 5; // Optional: skip computed due
  float force_due_tach = 6;                     // Optional: skip computed due
}

message InspectionRule {
  oneof rule {
    TimeRule time_rule = 1;        // e.g. every 12 months
    TachRule tach_rule = 2;        // e.g. every 100 tach hours
    OnConditionRule on_condition_rule = 3;
  }
}
```

#### User Profile

```protobuf
// LicenseInfo.proto
message LicenseInfo {
  LicenseType license_type = 1;   // NONE | REPAIRMAN | AMT
  string license_number = 2;
  google.protobuf.Timestamp expiration_date = 3;
  LicenseExpireLimit expireLimit = 4; // EXPIRES | NEVER_EXPIRES
}
```

### 3.2 Firestore Schema

Data is stored in Firestore as binary blobs (Wire-encoded protobufs) alongside indexed plain-text
fields for querying.

```
users/{userId}/
  profile/
    license_info                     → { license_info_blob: Blob }
  fleet/{aircraftId}                 → { aircraft_info_blob: Blob }
    maintenance_logs/{logId}         → { log_blob: Blob, timestamp: Timestamp, component_type: String, tach_time: Double }
    inspection_cards/{cardId}        → { inspection_card_blob: Blob, title: String, component: String }
```

**Encoding/Decoding pattern (same across all platforms):**
```kotlin
// Encode
val bytes: ByteArray = Aircraft.ADAPTER.encode(aircraft)

// Decode
val aircraft: Aircraft = Aircraft.ADAPTER.decode(bytes)
```

---

## 4. Key Architecture Patterns

### 4.1 Dependency Injection (Koin)

WingsLog uses **Koin 4.0.4** — the DI framework was migrated from Hilt specifically because it's
KMP-compatible. Each module defines its bindings in a `Module` object.

**Initialization in `composeApp/commonMain/di/initKoin.kt`:**
```kotlin
fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(
        commonFirebaseModule,   // Firebase instances (auth, firestore)
        authModule,             // expect/actual — platform-specific AuthManager impl
        userProfileDatabaseModule,
        aircraftDatabaseModule,
        fleetDatabaseModule,
        appModule,
        userProfileModule,
        settingsModule,
        fleetModule,
        aircraftModule,
    )
}
```

**iOS will call this from `iosMain` with an iOS-specific app declaration:**
```kotlin
// iosMain/MainViewController.kt (to be created)
fun MainViewController() = ComposeUIViewController {
    initKoin { /* iOS-specific Koin setup here */ }
    AppEntry()
}
```

### 4.2 ViewModel Pattern

ViewModels extend `androidx.lifecycle.ViewModel` from JetBrains' lifecycle KMP artifact
(`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`). This is compatible with iOS.

```kotlin
class AircraftOverviewViewModel(
    private val inspectionManager: InspectionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AircraftOverviewUiState>(Loading)
    val uiState: StateFlow<AircraftOverviewUiState> = _uiState.asStateFlow()
    // ...
}
```

ViewModels are injected via `koinViewModel()` in composables — this works on iOS via the
`koin-compose-viewmodel` artifact.

### 4.3 Navigation

Navigation uses **JetBrains Navigation Compose** (`org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha11`),
which is fully multiplatform. All routes are defined in `composeApp/AppEntry.kt`:

| Route | Screen |
|---|---|
| `login` | `LoginScreen` |
| `main` | `DashboardScreen` |
| `settings` | `SettingsScreen` |
| `edit_profile` | `EditProfileScreen` |
| `add_aircraft` | `EditAircraftScreen` |
| `edit_aircraft/{aircraftId}` | `EditAircraftScreen` |
| `aircraft_overview/{aircraftId}` | `AircraftOverviewScreen` |
| `maintenance_logs/{aircraftId}` | `MaintenanceLogListScreen` |
| `maintenance_log_create/{aircraftId}` | `MaintenanceLogFormScreen` |
| `maintenance_log_edit/{aircraftId}/{logId}` | `MaintenanceLogFormScreen` |

### 4.4 Firebase via GitLive KMP Wrapper

WingsLog uses `dev.gitlive:firebase-auth` and `dev.gitlive:firebase-firestore` (version `2.4.0`).
These wrap the native Firebase SDKs on each platform:

- **Android:** wraps `com.google.firebase:firebase-firestore` / `firebase-auth`
- **iOS:** wraps the native Firebase iOS SDK
- **JS:** wraps the Firebase JS SDK

`expect`/`actual` declarations in `core/database` handle platform-specific Firestore encoding:

```kotlin
// commonMain — expect declarations
expect fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray?
expect suspend fun DocumentReference.setEncoded(data: Map<String, Any>, merge: Boolean)
expect fun DocumentReference.observeSnapshot(): Flow<DocumentSnapshot>
```

The `androidMain` and `jsMain` actuals are implemented. **iOS actuals are missing** — see
[Section 6](#6-what-needs-to-be-done-for-ios).

### 4.5 Real-Time Data with Kotlin Flow

All data is observed via Kotlin `Flow`. Managers return `Flow<List<T>>` from Firestore snapshot
listeners. ViewModels collect these in `viewModelScope` using `combine` for derived state.

```kotlin
combine(
    aircraftManager.loadAircraft(aircraftId),
    logManager.observeLogs(aircraftId),
    inspectionManager.observeInspections(aircraftId)
) { aircraft, logs, inspectionCards ->
    // Compute derived state
}.collect { state -> _uiState.update { state } }
```

### 4.6 Authentication

The `AuthManager` interface (in `core/auth/commonMain`) abstracts platform sign-in:

```kotlin
interface AuthManager {
    fun getCurrentUser(): FirebaseUser?
    suspend fun trySilentLogin(): FirebaseUser?
    suspend fun signInWithGoogle(): FirebaseUser?
    suspend fun logOut()
}
```

- **Android:** Uses `androidx.credentials.CredentialManager` + Google Identity SDK
- **iOS (stub):** `AuthManagerIosStub` in `core/auth/iosMain` — returns `null` for everything
- **Real iOS:** Needs Firebase Auth with Google Sign-In or Apple Sign-In

### 4.7 Inspection Due Calculation

`InspectionManagerImpl.computeNextDue()` computes the next-due date/tach for each `InspectionCard`:

1. If `force_due_tach > 0f` or `force_due_date` is set → return those values directly (override)
2. Otherwise: find the most recent `MaintenanceLog` that references this card's ID (`inspection_ids`)
3. Add the rule interval (months or tach hours) to the last-service date/tach

---

## 5. iOS Scaffold That Already Exists

The codebase has iOS scaffolding in place from the Phase 1 KMP migration:

### `core/auth/src/iosMain/` — Auth stub
```kotlin
// core/auth/src/iosMain/kotlin/.../di/AuthModule.kt
class AuthManagerIosStub : AuthManager {
    override fun getCurrentUser(): FirebaseUser? = null
    override suspend fun trySilentLogin(): FirebaseUser? = null
    override suspend fun signInWithGoogle(): FirebaseUser? = null
    override suspend fun logOut() {}
}
actual val authModule: Module = module {
    single<AuthManager> { AuthManagerIosStub() }
}
```

This means `core/auth` already has a valid `iosMain` source set and compiles for iOS.

### `core/database/src/androidMain/` — Pattern to follow
The `androidMain` actuals for `DocumentReferences.kt` show exactly what the iOS actuals need to
implement (real-time Firestore snapshot listeners via `snapshots` Flow).

### `composeApp` — No iOS target yet
The `composeApp/build.gradle.kts` defines `androidTarget` and `js(IR)` but no iOS targets. Adding
`iosArm64()`, `iosSimulatorArm64()`, and `iosX64()` is the primary build step.

---

## 6. What Needs to Be Done for iOS

### Step 1: Add iOS Targets to `composeApp`

Edit `composeApp/build.gradle.kts`:

```kotlin
kotlin {
    // Add these:
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    
    // Keep existing:
    js(IR) { browser() }
    androidTarget { ... }

    sourceSets {
        // Add iOS source set
        val iosMain by creating {
            dependsOn(commonMain.get())
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        
        // iOS entry point
        iosMain.dependencies {
            // No extra deps needed — Firebase via GitLive wraps native SDK
        }
    }
}
```

### Step 2: Add iOS Targets to All Modules

Each of these modules also needs `iosX64()`, `iosArm64()`, `iosSimulatorArm64()` in their
`build.gradle.kts`:

- `core/auth` — Already has `iosMain` source set (stub exists) ✅ needs build targets
- `core/database` — **Needs `iosMain` actual implementations** (critical path)
- `core/model` — Wire protobufs are pure KMP; just needs targets declared
- `core/ui` — Has `commonMain` composables + `PlatformColorScheme` actuals; needs iOS `actual`
- `feature/*` — All in `commonMain`; just need targets declared

### Step 3: Implement `core/database` iOS Actuals

This is the **most critical** missing piece. Create:

**`core/database/src/iosMain/kotlin/dev/fanfly/wingslog/core/database/DocumentReferencesIos.kt`**

```kotlin
package dev.fanfly.wingslog.core.database

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.Flow

actual fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray? {
    return try {
        get<ByteArray>(field)
    } catch (e: Exception) {
        null
    }
}

actual suspend fun DocumentReference.setEncoded(data: Map<String, Any>, merge: Boolean) {
    set(data, merge = merge)
}

actual fun DocumentReference.observeSnapshot(): Flow<DocumentSnapshot> {
    return this.snapshots  // GitLive provides this on iOS
}

actual fun dev.gitlive.firebase.firestore.Query.observeSnapshot(): Flow<dev.gitlive.firebase.firestore.QuerySnapshot> {
    return this.snapshots  // GitLive provides this on iOS
}
```

> **Note:** The `jsMain` actuals use a one-shot `emit` (no real-time). The `androidMain` actuals
> use `.snapshots` (real-time). iOS should use `.snapshots` like Android.

### Step 4: Implement `core/ui` Platform Color Scheme iOS Actual

Create **`core/ui/src/iosMain/kotlin/.../theme/PlatformColorScheme.kt`**:

```kotlin
package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun platformColorScheme(darkTheme: Boolean): ColorScheme {
    // iOS doesn't have dynamic Material You colors; use static scheme
    return if (darkTheme) DarkColorScheme else LightColorScheme
}
```

### Step 5: Implement Real iOS Auth

Replace `AuthManagerIosStub` with a real implementation in
`core/auth/src/iosMain/`:

**Option A: Firebase Google Sign-In (cross-platform parity)**
```kotlin
class AuthManagerIosImpl(private val authProvider: FirebaseAuth) : AuthManager {
    override fun getCurrentUser(): FirebaseUser? = authProvider.currentUser

    override suspend fun signInWithGoogle(): FirebaseUser? {
        // Use GIDSignIn from GoogleSignIn iOS SDK
        // Wrap with Firebase AuthCredential
        // GitLive Firebase Auth KMP handles the credential exchange
        TODO("Implement with GIDSignIn.sharedInstance.signIn(...)")
    }

    override suspend fun trySilentLogin(): FirebaseUser? {
        return authProvider.currentUser
            ?: GIDSignIn.sharedInstance.restorePreviousSignIn()
                ?.let { signInToFirebase(it) }
    }

    override suspend fun logOut() {
        authProvider.signOut()
        GIDSignIn.sharedInstance.signOut()
    }
}
```

**Option B: Apple Sign-In (iOS-native, recommended for App Store)**
```kotlin
// Uses ASAuthorizationAppleIDProvider via expect/actual
// Firebase Auth KMP supports OAuthProvider for Apple
```

### Step 6: Create `iosApp/` Xcode Project

Create an Xcode project that wraps the KMP framework:

**`iosApp/iosApp/ContentView.swift`:**
```swift
import SwiftUI
import composeApp  // The KMP framework

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

**`composeApp/src/iosMain/kotlin/MainViewController.kt`:**
```kotlin
import androidx.compose.ui.window.ComposeUIViewController
import dev.fanfly.wingslog.AppEntry
import dev.fanfly.wingslog.di.initKoin

fun MainViewController() = ComposeUIViewController {
    initKoin()
    AppEntry()
}
```

**`iosApp/iosApp/WingsLogApp.swift`:**
```swift
import SwiftUI
import Firebase

@main
struct WingsLogApp: App {
    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

### Step 7: Add Firebase iOS Configuration

- Add `GoogleService-Info.plist` to `iosApp/iosApp/` (download from Firebase Console)
- Add Firebase iOS SDK via Swift Package Manager in Xcode:
  ```
  https://github.com/firebase/firebase-ios-sdk
  ```
  Required products: `FirebaseAuth`, `FirebaseFirestore`, `GoogleSignIn`
- GitLive's `firebase-kotlin-sdk` wraps these native packages automatically

### Step 8: Add `iosArm64` SPM / CocoaPods Deps in `build.gradle.kts`

Add iOS-specific dependencies to `iosMain` source sets where needed:

```kotlin
// In composeApp/build.gradle.kts iosMain dependencies:
implementation(libs.gitlive.firebase.auth)
implementation(libs.gitlive.firebase.firestore)
implementation(libs.koin.core)
implementation(libs.coil.compose)
```

> GitLive's Gradle plugin auto-links the native Firebase iOS frameworks through CocoaPods/SPM.
> Ensure `cocoapods {}` or `useLibraries()` is configured if using CocoaPods.

---

## 7. Platform-Specific Considerations for iOS

### 7.1 Keyboard Insets
On iOS, the keyboard pushes up the entire screen. Wrap `AppEntry()` in the iOS entrypoint with
`.ignoresSafeArea(.keyboard)` in SwiftUI.

### 7.2 Back Navigation
iOS uses a swipe-from-left gesture for back navigation. JetBrains Navigation Compose handles this
automatically on iOS — no additional work needed.

### 7.3 BottomSheet Behavior
`ModalBottomSheet` from Compose Multiplatform renders correctly on iOS. The
`skipPartiallyExpanded = true` used in `EditInspectionSheet` and `AddInspectionSheet` is respected.

### 7.4 Image Loading (Coil)
Coil 3.x (`io.coil-kt.coil3:coil-compose`) is KMP-compatible and iOS-ready. Profile images in
`CircularImage.kt` will render on iOS without changes.

### 7.5 File Attachments (Future Feature)
The PRD mentions PDF/image attachments. On iOS this will require:
- `UIImagePickerController` / `PHPickerViewController` for photos
- `UIDocumentPickerViewController` for files
- Wrap these in `expect/actual` under a `FilePicker` abstraction in `core/ui` or `core/platform`

### 7.6 Date/Time
`DateTimeUtils.kt` in `core/ui/commonMain` uses `kotlinx.datetime` — fully KMP-compatible. The
`WireInstantFactory` helpers exist in both `androidMain` and `jsMain`; an `iosMain` version should
mirror the `jsMain` implementation (simple delegation to `kotlinx.datetime`).

### 7.7 Safe Area Insets
On iOS, use `.ignoresSafeArea(.all, edges: .bottom)` for full-screen Compose content. The Scaffold
composables in WingsLog handle padding internally, so this should work without UI changes.

### 7.8 Koin Initialization
Koin on iOS must be initialized before any composable runs. Call `initKoin()` inside
`ComposeUIViewController { ... }` block (before `AppEntry()`), or in the Swift `AppDelegate`/`App`
init:
```swift
// Alternative: init Koin from Swift
init() {
    InitKoinKt.initKoin()  // If exposed as a top-level Kotlin function
}
```

---

## 8. Missing `iosMain` Actuals — Full Checklist

| Module | `expect` declaration | Status | Action needed |
|---|---|---|---|
| `core/auth` | `authModule: Module` | Stub exists ✅ | Implement real `AuthManagerIosImpl` |
| `core/database` | `getBlobAsBytes()` | ❌ Missing | Create `DocumentReferencesIos.kt` |
| `core/database` | `setEncoded()` | ❌ Missing | Create `DocumentReferencesIos.kt` |
| `core/database` | `DocumentReference.observeSnapshot()` | ❌ Missing | Create `DocumentReferencesIos.kt` |
| `core/database` | `Query.observeSnapshot()` | ❌ Missing | Create `DocumentReferencesIos.kt` |
| `core/ui` | `PlatformColorScheme.kt` | ❌ Missing | Create static light/dark scheme |
| `core/ui` | `WireInstantFactory.kt` | ❌ Missing | Mirror `jsMain` implementation |
| `composeApp` | `MainViewController.kt` | ❌ Missing | Create iOS entry point |

---

## 9. Build Configuration Changes Summary

### `settings.gradle.kts` — Add `iosApp` module (when Xcode project is created)
No change needed for the Gradle project itself — the Xcode project is standalone and references
the built KMP framework.

### `libs.versions.toml` — iOS additions
```toml
# Already present — no changes needed for core libs
# gitlive = "2.4.0"   ← wraps native Firebase iOS SDK automatically
# koin = "4.0.4"      ← KMP-compatible
# coil3 = "3.4.0"     ← KMP-compatible
```

### Each module's `build.gradle.kts`
Add iOS targets to the `kotlin { }` block:
```kotlin
iosX64()
iosArm64()
iosSimulatorArm64()
```

For modules with `iosMain` `actual` files, add the source set:
```kotlin
val iosMain by creating { dependsOn(commonMain.get()) }
val iosX64Main by getting { dependsOn(iosMain) }
val iosArm64Main by getting { dependsOn(iosMain) }
val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
```

---

## 10. Effort Estimate

| Task | Effort | Complexity |
|---|---|---|
| Add iOS targets to all `build.gradle.kts` files | 2-3 hrs | Low |
| Implement `core/database` iOS actuals | 1 hr | Low (copy from jsMain/adapt from androidMain) |
| Implement `core/ui` iOS actuals (`PlatformColorScheme`, `WireInstantFactory`) | 1 hr | Low |
| Create `iosApp/` Xcode project + Swift wrapper | 2-4 hrs | Medium |
| Add `GoogleService-Info.plist` + Firebase iOS SDK via SPM | 1 hr | Low |
| Implement real `AuthManagerIosImpl` (Google Sign-In) | 3-4 hrs | Medium |
| Implement `AuthManagerIosImpl` (Apple Sign-In) | 3-5 hrs | Medium-High |
| End-to-end testing on iOS Simulator | 4-8 hrs | Medium |
| **Total** | **~17-26 hrs** | |

The small estimate reflects how much was already done during Phase 1 (KMP migration). The shared
UI, business logic, navigation, data managers, and DI all compile for KMP targets today — iOS is
largely a matter of wiring up platform entry points and filling in the handful of `actual` stubs.

---

## 11. References

- [kmp_migration.md](./kmp_migration.md) — Full migration history and phase plan
- [database_schema_design.md](./database_schema_design.md) — Firestore schema details
- [PRD.md](./PRD.md) — Product requirements
- [GitLive Firebase KMP SDK](https://github.com/GitLiveApp/firebase-kotlin-sdk) — The Firebase wrapper in use
- [Compose Multiplatform iOS](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-ios-getting-started.html)
- [Koin Multiplatform](https://insert-koin.io/docs/setup/koin#multiplatform)
