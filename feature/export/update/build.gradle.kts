plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.export.update"
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
      implementation(project(":feature:export:datamanager"))
      implementation(project(":feature:export:sharedassets"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":core:ui"))

      implementation(libs.koin.compose.viewmodel)
      implementation(libs.compose.foundation)
      implementation(libs.components.resources)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.kotlinx.datetime)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
    }

    androidMain.dependencies {
      implementation(libs.androidx.core.ktx)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
