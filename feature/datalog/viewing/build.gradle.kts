plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.datalog.viewing"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }

    withHostTest {
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
      implementation(project(":core:ui"))
      implementation(project(":feature:datalog:model"))
      implementation(project(":feature:datalog:datamanager"))
      implementation(project(":feature:datalog:sharedassets"))
      implementation(libs.kotlinx.datetime)
      implementation(libs.kermit)
      implementation(libs.components.resources)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
}

compose.resources {
  publicResClass = true
}
