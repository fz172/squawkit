plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.aircraft.dashboard"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

compose.resources {
  publicResClass = true
}

kotlin {
  jvmToolchain(21)

  androidTarget {
  }

  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:attachments:datamanager"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:inspection:datamanager"))
      implementation(project(":feature:inspection:model"))
      implementation(project(":feature:inspection:viewing"))
      implementation(project(":feature:inspection:update"))
      implementation(project(":feature:inspection:sharedassets"))
      implementation(project(":feature:maintenance:datamanager"))
      implementation(project(":feature:maintenance:sharedassets"))
      implementation(project(":feature:maintenance:viewing"))

      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.androidx.navigation.compose)

      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.components.resources)
      implementation(libs.kermit)
    }
  }
}
