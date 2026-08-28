plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.notifications.sharedassets"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  // Settings-screen furniture — PermissionBanner, NotificationClassRow — reused only by :settings.
  // The onboarding primer shares none of it (design §10.1) and depends on :permission alone.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:notifications:model"))
      implementation(project(":feature:notifications:permission"))

      implementation(libs.compose.runtime)
      implementation(libs.components.resources)
    }
  }
}

compose.resources {
  publicResClass = true
}
