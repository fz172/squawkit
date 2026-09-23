plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.comments.dashboard"
    compileSdk = 37
    minSdk = 33
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:comments:model"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:comments:datamanager"))
      implementation(project(":feature:comments:sharedassets"))
      implementation(project(":feature:comments:viewing"))
      implementation(libs.components.resources)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
  }
}
