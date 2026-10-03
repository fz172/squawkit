plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.connectivity"
    compileSdk = 37
    minSdk = 33
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  // Whether the device can reach the network now, for actions that need a connection (AI
  // suggestions, PRD R51) to show disabled offline rather than fail after a timeout.
  sourceSets {
    commonMain.dependencies {
      implementation(libs.koin.core)
      implementation(libs.kotlinx.coroutines.core)
    }
    androidMain.dependencies {
      implementation(libs.koin.android)
    }
  }
}
