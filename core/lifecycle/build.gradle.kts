plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kover)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.lifecycle"
    compileSdk = 37
    minSdk = 33

    withHostTest {
    }
    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  // No Compose, deliberately. The app-session signal is consumed by feature/ads/datamanager, and
  // AGENTS.md forbids a datamanager depending on UI — which is why this is its own module rather
  // than part of core/ui. The Compose-aware driver that pumps it lives in feature/shell.
  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.koin.core)
    }
    androidMain.dependencies {
      // androidContext() for CurrentActivityProvider's binding — the Activity and Application types
      // themselves come from the platform SDK, so there is nothing else to add here.
      implementation(libs.koin.android)
    }
    sourceSets.getByName("androidHostTest")
      .dependencies {
        implementation(libs.junit)
        implementation(libs.truth)
        implementation(libs.kotlinx.coroutines.test)
      }
  }
}
