plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.inspection.datamanager"
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
      implementation(project(":core:database"))
      implementation(project(":core:model"))
      implementation(project(":feature:inspection:model"))
      implementation(libs.koin.core)
      implementation(libs.kermit)
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.gitlive.firebase.firestore)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.coroutines.core)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
}