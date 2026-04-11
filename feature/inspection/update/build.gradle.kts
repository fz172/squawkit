plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.inspection.update"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compilerOptions {
    }
  }

  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:inspection:model"))
      implementation(project(":feature:inspection:datamanager"))
      implementation(project(":feature:inspection:sharedassets"))
      implementation(project(":feature:inspection:viewing"))

      implementation(project(":core:ui"))
      implementation(project(":core:database"))
      implementation(project(":feature:maintenance:datamanager"))
      implementation(project(":core:attachments:model"))
      implementation(project(":core:attachments:datamanager"))
      implementation(project(":core:attachments:viewing"))

      implementation(libs.koin.compose.viewmodel)
      implementation(libs.kermit)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)

      implementation(libs.kotlinx.datetime)
      implementation(libs.components.resources)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
}
