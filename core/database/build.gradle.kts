plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.database"
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
      api(libs.gitlive.firebase.firestore)
      api(libs.gitlive.firebase.auth)
      api(libs.kotlinx.datetime)
      api(libs.kotlinx.coroutines.core)

      // DI
      api(libs.koin.core)

      // Logging
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      api(project(":core:model"))
      api(project(":core:auth"))
      api(project(":core:ui"))

      // Firebase Data
      api(libs.firebase.firestore)

      // DI
      implementation(libs.koin.android)

      // Coroutines
      implementation(libs.androidx.core.ktx)
    }
  }
}

dependencies {
  // Firebase BOM inside standard dependencies block
  implementation(platform(libs.firebase.bom))

}
