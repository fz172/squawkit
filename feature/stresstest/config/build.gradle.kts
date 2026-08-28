plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.stresstest.config"
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
      api(project(":core:appinfo"))
      api(project(":feature:stresstest"))
      api(project(":feature:developeroptions:plugin"))
      implementation(project(":core:ui:theme"))
      implementation(libs.components.resources)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.material.icons.extended)
      implementation(libs.koin.core)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidMainImplementation"(platform(libs.androidx.compose.bom))
}

compose.resources {
  publicResClass = true
}
