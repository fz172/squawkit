plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.attachment.viewing"
  compileSdk = 36
  defaultConfig { minSdk = 33 }
  buildFeatures { compose = true }
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
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:model"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:attachment:sharedassets"))
      implementation(libs.compose.ui.backhandler)
    }
    androidMain.dependencies {
    }
  }
}

dependencies {
  debugImplementation(libs.androidx.compose.ui.tooling)
}
