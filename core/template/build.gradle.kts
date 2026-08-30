plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.template"
    compileSdk = 37
    minSdk = 33

    withHostTest {
      isReturnDefaultValues = true
    }
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:model"))
      api(libs.koin.core)
      // Runtime only — this module provides a CompositionLocal, not UI. Nothing here draws.
      implementation(compose.runtime)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
}
