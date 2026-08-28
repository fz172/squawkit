plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.notifications.settings"
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

  // Single-screen settings surface — feature/sync/settings is the precedent (design §3), not the
  // canonical viewing/update pair: a surface with one screen does not earn a two-module split.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:auth"))
      implementation(project(":core:model"))
      implementation(project(":core:nav"))
      implementation(project(":core:sharedassets"))
      implementation(project(":feature:notifications:model"))
      implementation(project(":feature:notifications:permission"))
      implementation(project(":feature:notifications:datamanager"))
      implementation(project(":feature:notifications:sharedassets"))
      implementation(project(":feature:sync:data"))
      implementation(libs.gitlive.firebase.auth)

      // Compose
      implementation(libs.compose.runtime)
      implementation(libs.components.resources)

      // Navigation
      implementation(libs.androidx.navigation.compose)

      // Lifecycle & DI
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose.viewmodel)

      // Logging
      implementation(libs.kermit)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidMainImplementation"(platform(libs.androidx.compose.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

compose.resources {
  publicResClass = true
}
