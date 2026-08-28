plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.tasks.viewing"
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
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:attachment:viewing"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(libs.kotlinx.datetime)
      implementation(libs.kermit)
      implementation(libs.components.resources)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.firebase.bom))
}

compose.resources {
  publicResClass = true
}
