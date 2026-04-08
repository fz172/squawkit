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
      implementation(project(":core:attachments:viewing"))
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

      implementation(libs.runtime)
      implementation(libs.ui)
      implementation(libs.foundation)
      implementation(libs.material3)
      api(libs.compose.ui.tooling.preview)
      implementation(libs.material.icons.extended)
      implementation(libs.components.resources)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
}
