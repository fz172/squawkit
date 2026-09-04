plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.comments.viewing"
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
      implementation(project(":core:datetime"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:comments:model"))
      implementation(project(":feature:comments:sharedassets"))
    }
  }
}

dependencies {
  "androidRuntimeClasspath"(libs.androidx.compose.ui.tooling)
}

compose.resources {
  publicResClass = true
}
