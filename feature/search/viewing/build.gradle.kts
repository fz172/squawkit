plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.search.viewing"
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
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:model"))
      implementation(project(":core:datetime"))
      implementation(project(":core:sharedassets"))
      implementation(project(":feature:search:model"))
      implementation(project(":feature:search:sharedassets"))
      implementation(libs.material3)
      implementation(libs.material.icons.extended)
      implementation(libs.components.resources)
      implementation(libs.kotlinx.datetime)
    }
  }
}

compose.resources {
  publicResClass = true
}
