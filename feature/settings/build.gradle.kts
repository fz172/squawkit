plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.settings"
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
      implementation(libs.gitlive.firebase.auth)
      implementation(project(":core:nav"))
      implementation(project(":core:sharedassets"))
      // The guest-upgrade experience lives in feature/login; Settings owns only its entry point.
      implementation(project(":feature:login"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:analytics"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:auth"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:login"))
      implementation(project(":feature:sync:sharedassets"))
      implementation(project(":feature:export:sharedassets"))
      implementation(project(":feature:technician:sharedassets"))
      implementation(project(":feature:ads:datamanager"))
      implementation(project(":feature:developeroptions:datamanager"))
      // The Notifications row's live subtitle (design §9.1).
      implementation(project(":feature:notifications:model"))
      implementation(project(":feature:notifications:permission"))
      implementation(project(":feature:notifications:datamanager"))
      // DeveloperOptionsExtra: the contributed-section interface DeveloperOptionsScreen resolves
      // from Koin, so contributors depend on it rather than on this module.
      implementation(project(":feature:developeroptions:plugin"))
      implementation(project(":feature:technician:datamanager"))
      implementation(project(":core:datetime"))
      implementation(project(":core:model"))

      // Compose
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
  "androidMainImplementation"(libs.firebase.functions)
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
