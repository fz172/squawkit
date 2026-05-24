plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.attachment.model"
  compileSdk = 36
  defaultConfig { minSdk = 33 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  jvmToolchain(21)
  androidTarget()

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(project(":core:model"))
    }
  }
}
