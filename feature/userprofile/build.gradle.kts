plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.userprofile"
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

  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:auth"))
      implementation(project(":feature:userprofile:userprofilecard"))
      implementation(project(":feature:userprofile:database"))
      implementation(project(":feature:userprofile:sharedassets"))
      implementation(project(":feature:technician:sharedassets"))

      // Compose

      // Navigation
      implementation(libs.androidx.navigation.compose)
      implementation(libs.compose.foundation)

      // Lifecycle & DI
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose.viewmodel)

      // Logging
      implementation(libs.kermit)
    }
    androidMain.dependencies {
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(platform(libs.androidx.compose.bom))
}
