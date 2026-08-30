plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.shell"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }

    withHostTest {
    }
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      // The shared app graph must reach every feature UI module it hosts — this module plays
      // the same aggregator role for composables/nav that core:di plays for Koin modules.
      implementation(project(":core:analytics"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:auth"))
      implementation(project(":core:nav"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":feature:thing:dashboard"))
      implementation(project(":feature:thing:update"))
      implementation(project(":feature:export:update"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:fleet:picker:data"))
      implementation(project(":feature:fleet:viewing"))
      implementation(project(":feature:logs:update"))
      implementation(project(":feature:settings"))
      // The DeveloperOptionsNavContributor interface only — NOT the features that implement it.
      // This is what replaced the dependency on feature:stresstest:config.
      implementation(project(":feature:developeroptions:plugin"))
      // AccountUpgradeFlow is hosted here so an upgrade email link is seen on any destination.
      implementation(project(":feature:login"))
      implementation(project(":feature:subscription:datamanager"))
      implementation(project(":feature:subscription:viewing"))
      implementation(project(":feature:sharing:update"))
      // App-start retry of an owed technician-mirror publish (design §7.2).
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:squawk:update"))
      implementation(project(":core:sharedassets"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:sync:settings"))
      implementation(project(":feature:notifications:settings"))
      // NotificationTapRouter (design §5.3) — HandleNotificationTaps navigates for Squawk/Task/Log;
      // AdaptiveShellViewModel handles Aircraft itself.
      implementation(project(":feature:notifications:viewing"))
      implementation(project(":feature:notifications:model"))
      implementation(project(":feature:tasks:update"))
      implementation(project(":feature:technician:datamanager"))
      implementation(project(":feature:technician:manage"))

      implementation(libs.androidx.navigation.compose)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.gitlive.firebase.auth)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidMainImplementation"(platform(libs.androidx.compose.bom))

  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

/**
 * Tells Gradle that `ScreenViewNotDoubleCountedTest` depends on every `.kt` file in the repo.
 *
 * It scans the filesystem for screens that log their own view, which Gradle cannot see. Without
 * this the task is UP-TO-DATE whenever `feature/shell`'s own sources are unchanged — which is
 * exactly the commit that adds a self-logging screen in `feature/squawk`. The guard would be
 * skipped on the change it exists to catch, and a skipped task reports success.
 */
tasks.withType<Test>().configureEach {
  inputs.files(
    rootProject.fileTree(rootProject.projectDir) {
      include("**/src/**/*.kt")
      exclude("**/build/**")
    },
  ).withPropertyName("repoKotlinSourcesForScreenViewGuard")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}
