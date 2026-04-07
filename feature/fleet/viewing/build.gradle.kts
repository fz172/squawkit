plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.fleet.viewing"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  jvmToolchain(21)

  androidTarget {
  }

  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      implementation(project(":core:ui"))
      implementation(project(":feature:fleet:model"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:fleet:sharedassets"))
      implementation(project(":feature:inspection:model"))
      implementation(project(":feature:inspection:datamanager"))
      implementation(project(":feature:inspection:sharedassets"))
      implementation(project(":feature:maintenance:datamanager"))

      // Compose
      implementation(compose.ui)
      implementation(compose.material3)
      implementation(compose.materialIconsExtended)
      implementation(compose.components.resources)

      // Navigation & Lifecycle
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.androidx.navigation.compose)

      // DI
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)

      // Logging
      implementation(libs.kermit)
    }
  }
}
