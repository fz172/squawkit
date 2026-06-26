// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.kover) apply false
  // Firebase
  id("com.google.gms.google-services") version "4.4.4" apply false
  id("com.google.protobuf") version "0.9.5" apply false
}

// Align the core Compose Multiplatform runtime artifacts with the Compose plugin version.
// Some companion libraries (e.g. navigation-compose) still pin these to an older Compose
// (1.8.2), which in modules that don't declare compose-ui directly wins resolution and trips
// the plugin's "runtime dependencies' versions don't match" warning. Only the groups that are
// released in lockstep with the plugin are forced here — material3, material-icons, and
// components keep their own independent versions.
val composeCoreGroups = setOf(
  "org.jetbrains.compose.ui",
  "org.jetbrains.compose.foundation",
  "org.jetbrains.compose.runtime",
  "org.jetbrains.compose.animation",
)
val composeVersion = libs.versions.composeMultiplatform.get()
subprojects {
  configurations.configureEach {
    resolutionStrategy.eachDependency {
      if (requested.group in composeCoreGroups) {
        useVersion(composeVersion)
        because("keep core Compose runtime aligned with the Compose plugin ($composeVersion)")
      }
    }
  }
}