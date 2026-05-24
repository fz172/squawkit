plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.login"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

compose.resources {
  publicResClass = true
}

kotlin {
  jvmToolchain(21)

  androidTarget {
    compilerOptions {
    }
  }


  iosArm64()
  iosSimulatorArm64()

  js(IR) {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:auth"))

      // Compose resources (this module owns its login strings + Google icon)
      implementation(libs.components.resources)

      // Lifecycle & DI
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose.viewmodel)
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // GitLive firebase-auth-android pins its versions via the Firebase BOM, which
  // core:auth declares as `implementation` (not exposed transitively) — so declare it here too.
  implementation(platform(libs.firebase.bom))
}
