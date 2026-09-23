plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.dashboard.api"
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
      implementation(project(":core:model"))
      implementation(project(":core:template"))
      implementation(project(":core:ui"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:sharing:model"))
      implementation(project(":feature:squawk:model"))
      implementation(project(":feature:tasks:model"))
      implementation(libs.kotlinx.datetime)
    }
  }
}
