plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.developeroptions.plugin"
  compileSdk = 37

  defaultConfig {
    minSdk = 33
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

  // The extension point a feature implements to contribute a Developer Options section, split into
  // its own module so that a contributor depends on THIS and not on the screen that hosts it.
  //
  // Deliberately tiny: one interface, `compose.runtime` and nothing else. It sits beside
  // `:datamanager` rather than inside it because the interface is Compose-bearing and a datamanager
  // must not be — the same split as core:lifecycle / core:lifecycle:compose.
  sourceSets {
    commonMain.dependencies {
      implementation(libs.compose.runtime)
    }
  }
}
