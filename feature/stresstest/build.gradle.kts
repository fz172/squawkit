plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.stresstest"
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

kotlin {
  jvmToolchain(21)

  androidTarget()
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:model"))
      implementation(project(":core:datetime"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:technician:datamanager"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:logs:datamanager"))

      implementation(libs.androidx.navigation.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.kermit)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(platform(libs.androidx.compose.bom))
}
