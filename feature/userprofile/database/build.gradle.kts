plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.userprofile.database"
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
      implementation(project(":core:model"))
      implementation(project(":core:database"))
      implementation(project(":core:auth"))

      implementation(libs.gitlive.firebase.firestore)
      implementation(libs.gitlive.firebase.auth)

      // DI
      implementation(libs.koin.core)

      // Logging
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      // DI
      implementation(libs.koin.android)
    }
  }
}

dependencies {
  // Firebase BOM for transitive dependency version resolution (required by core:database)
  implementation(platform(libs.firebase.bom))
}
