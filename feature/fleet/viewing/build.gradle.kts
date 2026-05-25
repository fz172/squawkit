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

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:auth"))
      implementation(libs.gitlive.firebase.auth)
      implementation(project(":feature:fleet:model"))
      implementation(project(":feature:technician:datamanager"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:fleet:sharedassets"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(project(":feature:logs:datamanager"))

      // Compose

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

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(platform(libs.androidx.compose.bom))
}
