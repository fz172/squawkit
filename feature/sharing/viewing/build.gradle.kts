plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.sharing.viewing"
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
  }

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.material3)
      implementation(libs.qrose)
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      // Shared avatar (photo → initials → "?"), same widget the shell's account row uses.
      implementation(project(":core:ui:widget:avataricon"))
      implementation(project(":feature:sharing:model"))
      implementation(project(":feature:sharing:sharedassets"))
    }
  }
}
