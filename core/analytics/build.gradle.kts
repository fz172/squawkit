plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.analytics"
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

  androidTarget()
  iosArm64()
  iosSimulatorArm64()
  js(IR) {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      // CompositionLocal + the NavController screen-view observer live here, so UI hosts
      // depend on this module the same way they depend on core:ui utilities.
      api(libs.compose.runtime)
      api(libs.androidx.navigation.compose)
      api(libs.koin.core)
      implementation(libs.kermit)
    }

    androidMain.dependencies {
      implementation(libs.firebase.analytics)
      implementation(libs.koin.android)
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
}
