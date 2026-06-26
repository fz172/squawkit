plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.aircraft.dashboard"
  compileSdk = 37

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

dependencies {
  implementation(platform(libs.firebase.bom))
}

kotlin {
  jvmToolchain(21)

  androidTarget {
  }

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:analytics"))
      implementation(project(":core:nav"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:featurelab:datamanager"))
      implementation(libs.gitlive.firebase.auth)
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:tasks:viewing"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:logs:viewing"))
      implementation(project(":feature:squawk:model"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:squawk:sharedassets"))
      implementation(project(":feature:squawk:viewing"))

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
