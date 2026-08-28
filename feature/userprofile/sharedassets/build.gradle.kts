plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.userprofile.sharedassets"
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
      // Compose
      implementation(libs.compose.runtime)
      implementation(libs.components.resources)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.androidx.compose.bom))
}

compose.resources {
  publicResClass = true
}
