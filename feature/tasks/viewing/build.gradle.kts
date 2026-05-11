plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.tasks.viewing"
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
      implementation(project(":core:ui"))
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
  implementation(platform(libs.firebase.bom))
}
