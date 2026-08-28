plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.ui.theme"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(libs.material3)
      api(libs.compose.ui)
      api(libs.components.resources)
      api(libs.koin.core)
      implementation(libs.kotlinx.coroutines.core)
    }

    androidMain.dependencies {
      implementation(libs.koin.android)
    }
  }
}

compose.resources {
  publicResClass = true
}
