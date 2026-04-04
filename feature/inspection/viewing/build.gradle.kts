plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.inspection.viewing"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
      implementation(project(":core:model"))
      implementation(project(":core:ui"))
      implementation(project(":feature:inspection:model"))
      implementation(project(":feature:inspection:sharedassets"))
      implementation(libs.kotlinx.datetime)


      implementation(libs.kotlinx.datetime)
      implementation(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.kermit)
      implementation(libs.gitlive.firebase.firestore)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)

      implementation(compose.runtime)
      implementation(compose.ui)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.materialIconsExtended)
      implementation(compose.components.resources)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
}
