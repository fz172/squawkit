plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.sync.settings"
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

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:auth"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:sync:sharedassets"))
      implementation(project(":feature:featurelab:datamanager"))
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
  implementation(platform(libs.firebase.bom))
  implementation(platform(libs.androidx.compose.bom))
}
