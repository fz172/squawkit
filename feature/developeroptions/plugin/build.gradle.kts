plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.developeroptions.plugin"
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

  // The extension point a feature implements to contribute a Developer Options section, split into
  // its own module so that a contributor depends on THIS and not on the screen that hosts it.
  //
  // Deliberately tiny: one interface, `compose.runtime` and nothing else. It sits beside
  // `:datamanager` rather than inside it because the interface is Compose-bearing and a datamanager
  // must not be — the same split as core:lifecycle / core:lifecycle:compose.
  sourceSets {
    commonMain.dependencies {
      implementation(libs.compose.runtime)
      // DeveloperOptionsNavContributor's signature only. No screens and no NavHost live here.
      implementation(libs.androidx.navigation.compose)
    }
  }
}
