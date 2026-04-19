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
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:tasks:viewing"))
      implementation(project(":feature:tasks:update"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:logs:viewing"))

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
