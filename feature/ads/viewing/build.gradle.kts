plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.ads.viewing"
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

  // This is the ONLY ads module permitted to reference an ad SDK (design N4). Nothing is declared
  // yet: P1 ships no-op actuals on every host, Google Mobile Ads arrives in androidMain at P5, and
  // iOS links via SPM in the Swift app rather than as a Gradle dependency at all (design §7.2).
  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:ads:model"))
      implementation(libs.compose.runtime)
      implementation(libs.compose.ui)
    }
  }
}
