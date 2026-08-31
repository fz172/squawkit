plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.template"
    compileSdk = 37
    minSdk = 33

    withHostTest {
      isReturnDefaultValues = true
    }
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:model"))
      // For APP_VERSION_CODE — the floor a template's min_app_version is compared against (#728).
      implementation(project(":core:appinfo"))
      api(libs.koin.core)
      api(libs.kotlinx.coroutines.core)
      // Runtime only — this module provides a CompositionLocal, not UI. Nothing here draws.
      implementation(compose.runtime)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
}

/**
 * Tells Gradle that `StringSnapshotTest` depends on every `strings.xml` **and every `.kt` file** in
 * the repo.
 *
 * The test reads them from the filesystem rather than through generated resource accessors (see
 * its KDoc for why). Gradle cannot see that, so without this the task is UP-TO-DATE whenever
 * `core/template`'s own sources are unchanged — which is precisely the commit that edits a string
 * in `feature/login`. The guard would be skipped on exactly the change it exists to catch, and a
 * skipped task reports success. Verified by mutating a string and watching the test not run.
 */
tasks.withType<Test>().configureEach {
  inputs.files(
    rootProject.fileTree(rootProject.projectDir) {
      include("**/src/**/values/strings.xml")
      exclude("**/build/**")
    },
  ).withPropertyName("repoStringResources").withPathSensitivity(PathSensitivity.RELATIVE)

  // The call-site guards read Kotlin, not just resources. Without this they are UP-TO-DATE whenever
  // core/template is unchanged — which is every commit that only touches a call site, exactly the
  // commits they exist to police. Found by mutating ProUpsellSheet and watching the suite pass in
  // two seconds without running.
  inputs.files(
    rootProject.fileTree(rootProject.projectDir) {
      include("**/src/**/*.kt")
      exclude("**/build/**")
    },
  ).withPropertyName("repoKotlinSources").withPathSensitivity(PathSensitivity.RELATIVE)
}
