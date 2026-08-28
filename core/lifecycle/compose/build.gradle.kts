plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.lifecycle.compose"
    compileSdk = 37
    minSdk = 33
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:lifecycle"))
      implementation(libs.compose.runtime)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
  }
}
