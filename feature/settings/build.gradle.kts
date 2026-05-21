plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.settings"
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
      implementation(libs.gitlive.firebase.auth)
      implementation(project(":core:ui"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:auth"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:sync:sharedassets"))
      implementation(project(":feature:export:sharedassets"))
      implementation(project(":feature:technician:sharedassets"))
      implementation(project(":feature:userprofile:userprofilecard"))
      implementation(project(":feature:featurelab:datamanager"))
      implementation(project(":feature:technician:datamanager"))
      implementation(project(":core:datetime"))
      implementation(project(":core:model"))

      // Compose
      implementation(libs.components.resources)

      // Navigation
      implementation(libs.androidx.navigation.compose)

      // Lifecycle & DI
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose.viewmodel)

      // Logging
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      implementation(libs.firebase.functions)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
