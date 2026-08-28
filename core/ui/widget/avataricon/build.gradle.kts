plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.ui.widget.avataricon"
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
      api(project(":core:ui:theme"))

      api(libs.material3)
      api(libs.compose.ui)
      api(libs.components.resources)

      implementation(libs.coil.compose)
      implementation(libs.coil.network.ktor3)
      implementation(libs.ktor.client.core)
    }
    androidMain.dependencies {
      implementation(libs.ktor.client.okhttp)
    }
  }
}

compose.resources {
  publicResClass = true
}
