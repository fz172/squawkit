plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kover)
}

android {
  namespace = "dev.fanfly.wingslog.feature.ads.model"
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

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  // Deliberately dependency-free. The placement core (P2) must stay pure Kotlin so the three list
  // surfaces can depend on it without pulling in Compose or an ad SDK (design §2, N4).
  sourceSets {
  }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
