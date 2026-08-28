plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.attachment.viewing"
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
      implementation(project(":core:appinfo"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:subscription:viewing"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:model"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:attachment:sharedassets"))
      implementation(libs.compose.ui.backhandler)
      implementation(libs.koin.compose)
    }
  }
}

dependencies {
  "androidMainImplementation"(libs.androidx.documentfile)
  "androidRuntimeClasspath"(libs.androidx.compose.ui.tooling)
}

compose.resources {
  publicResClass = true
}
