# Migrate KMP Modules to `com.android.kotlin.multiplatform.library`

The project currently uses the legacy `com.android.library` plugin alongside `org.jetbrains.kotlin.multiplatform`. AGP 9.0 deprecates this combination, recommending the new `com.android.kotlin.multiplatform.library` plugin instead. This migration will resolve the build warnings and ensure compatibility with future AGP versions (AGP 10.0 will remove support for the legacy combination).

## Proposed Changes

The migration involves updating `libs.versions.toml`, the root `build.gradle.kts`, and every KMP module's `build.gradle.kts`.

### Global Configuration

#### [MODIFY] [libs.versions.toml](file:///Users/zhfan/git/squawkit/gradle/libs.versions.toml)
- Add the new plugin alias: `android-kmp-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }`.

#### [MODIFY] [build.gradle.kts](file:///Users/zhfan/git/squawkit/build.gradle.kts)
- Add the plugin with `apply false` to the root `plugins` block.

---

### Module-Level Migration

For each KMP module (e.g., `core/ui`, `feature/tasks/datamanager`, etc.):

#### [MODIFY] `build.gradle.kts`
- Replace `alias(libs.plugins.android.library)` with `alias(libs.plugins.android.kmp.library)`.
- Remove the top-level `android { ... }` block.
- Add an `android { ... }` block inside the `kotlin { ... }` block.
- Move `namespace`, `compileSdk`, and `minSdk` into the new `kotlin.android` block.
- Enable Android resources if the module uses them: `androidResources { enable = true }`.
- Remove `androidTarget()` call (the new plugin handles target creation).
- Update dependency configurations:
    - Replace `debugImplementation` with `androidRuntimeClasspath` in the top-level `dependencies` block (e.g., for `compose-ui-tooling`).
    - Move other Android-specific dependencies to the `kotlin.sourceSets.androidMain.dependencies` block.

#### [MOVE] Test Sources
- Move `src/test` to `src/androidHostTest`.
- Move `src/androidTest` to `src/androidDeviceTest`.
- Update test configuration in `kotlin.android`:
    ```kotlin
    withHostTest {
      isIncludeAndroidResources = true // if needed
    }
    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    ```

## Affected Modules

The migration will be applied to all modules currently using both plugins, including:
- `core/`: `ui`, `di`, `auth`, `storage`, `model`, `nav`, `sharedassets`, `appinfo`, `analytics`, etc.
- `feature/`: `sync`, `tasks`, `sharing`, `logs`, `squawk`, `fleet`, `attachment`, `subscription`, `login`, `ads`, `settings`, etc.
- `composeApp`

## Verification Plan

### Automated Tests
- Run `./gradlew assemble` to ensure the project builds successfully.
- Run unit tests: `./gradlew test` (which will now target `androidHostTest`).
- Verify that Compose Previews still work in the IDE (they use `androidRuntimeClasspath`).

### Manual Verification
- Deploy the app to an Android device/emulator to ensure runtime functionality is preserved.
- Check that resources (strings, drawables) are correctly loaded.
