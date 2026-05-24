plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.squawk.sharedassets"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

compose.resources {
  publicResClass = true
}

kotlin {
  jvmToolchain(21)

  androidTarget()

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      implementation(libs.compose.runtime)
      implementation(libs.material3)
      implementation(libs.components.resources)
    }
  }
}
