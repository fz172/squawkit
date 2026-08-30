plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.fleet.viewing"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:template"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui"))
      implementation(project(":feature:fleet:sharedassets"))
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.androidx.compose.bom))
}
