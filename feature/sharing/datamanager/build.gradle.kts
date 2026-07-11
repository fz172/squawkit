plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kover)
}

android {
  namespace = "dev.fanfly.wingslog.feature.sharing.datamanager"
  compileSdk = 37

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

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:storage"))
      implementation(project(":feature:sharing:model"))
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.gitlive.firebase.firestore)
      implementation(libs.gitlive.firebase.functions)
      implementation(libs.kermit)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
