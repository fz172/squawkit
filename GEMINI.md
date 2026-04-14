# WingsLog - Gemini CLI Context

This file provides instructional context for Gemini CLI when working in the WingsLog repository.

## Project Overview
WingsLog is a **Kotlin Multiplatform (KMP)** application for aviation logbook and fleet management. It tracks aircraft maintenance logs, inspection compliance, and due status.

### Core Stack
- **UI:** Compose Multiplatform (sharing UI between Android and iOS)
- **Architecture:** MVVM + StateFlow
- **Dependency Injection:** Koin
- **Backend:** Firebase Firestore (real-time data) & Firebase Auth
- **Data Models:** Protocol Buffers (Wire 6)
- **Asynchronous:** Kotlin Coroutines & Flow

## Module Structure
The project follows a modular structure with a specific pattern for feature modules:

- `app/`: Android-specific entry point and Application class.
- `composeApp/`: Shared Compose UI, navigation graph (`AppEntry.kt`), and central Koin initialization.
- `core/`: Shared core functionality.
    - `model/`: Protobuf definitions and generated models.
    - `ui/`: Design system (Material 3), themes, and shared components.
    - `auth/`: Firebase Auth integration.
    - `database/`: Firestore repository helpers.
    - `datetime/`: Date and time utilities.
- `feature/`: Feature-specific modules (e.g., `fleet`, `maintenance`, `inspection`, `userprofile`).
    - Feature modules follow a canonical submodule pattern:
        - `model/`: Feature-specific domain models.
        - `datamanager/`: Business logic, repositories (Firestore), and Koin modules.
        - `sharedassets/`: Compose resources (strings, drawables) shared within the feature.
        - `viewing/`: Read-only UI components (cards, list items).
        - `update/`: Edit/Create screens and ViewModels.

## Building and Running

### Gradle Commands
- `./gradlew assembleDebug`: Build the Android debug APK.
- `./gradlew lint`: Run lint checks.
- `./gradlew testDebugUnitTest`: Run all unit tests for the Android target.
- `./gradlew :composeApp:compileKotlinIosX64`: Verify Kotlin compilation for iOS (X64).
- `./gradlew :composeApp:compileKotlinIosArm64`: Verify Kotlin compilation for iOS (Arm64).

### Running the App
- **Android:** Standard Android Studio run configuration for the `app` module.
- **iOS:** Requires Xcode or the Kotlin Multiplatform Mobile plugin in Android Studio to run the `iosApp`.

## Development Conventions

### Feature Module Rules (Strictly Enforced)
- **No Resource Bloat:** Do not add a dependency on a module just for its strings or drawables. Use `sharedassets/` within the feature.
- **Dependency Flow:**
    - `sharedassets` → Compose resources only.
    - `model` → `core:model`, kotlinx only.
    - `datamanager` → `:model`, `core:database`, `core:model`, Firebase, Koin, Coroutines.
    - `viewing` → `:model`, `:sharedassets`, `core:ui`, `core:model`.
    - `update` → `:model`, `:datamanager`, `:viewing`, `:sharedassets`, `core:*`.

### Architecture Patterns
- **ViewModel Injection:** Use `koinViewModel()` in `@Composable` screens.
- **State Management:** ViewModels expose `StateFlow<UiState>`. Use `collectAsStateWithLifecycle()` in screens.
- **Protobuf Serialization:** Data is stored in Firestore as binary blobs using Wire-generated adapters.
- **Navigation:** Handled in `composeApp/AppEntry.kt` using Jetpack Navigation (Compose version).

### Coding Style
- **Minimalism:** Prioritize clarity and readability over information density.
- **Theme:** Use spacing tokens from `dev.fanfly.wingslog.core.ui.theme.Spacing`.
- **Resources:** Use `org.jetbrains.compose.resources` for multiplatform resource access.

## Maintenance and CI
- CI runs on every push via `.github/workflows/ci.yml`.
- CI tasks include linting, building the debug APK, and running unit tests.
- Requires `GOOGLE_SERVICES_JSON` secret for Firebase configuration during the build process.
