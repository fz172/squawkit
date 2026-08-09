plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kover)
}

android {
  namespace = "dev.fanfly.wingslog.core.lifecycle"
  compileSdk = 37

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
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

  // No Compose, deliberately. The app-session signal is consumed by feature/ads/datamanager, and
  // AGENTS.md forbids a datamanager depending on UI — which is why this is its own module rather
  // than part of core/ui. The Compose-aware driver that pumps it lives in feature/shell.
  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.koin.core)
    }
  }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
