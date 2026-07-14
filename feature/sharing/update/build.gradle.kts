plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.sharing.update"
  compileSdk = 37

  defaultConfig {
    minSdk = 33
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  jvmToolchain(21)

  androidTarget {
  }

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.material3)
      implementation(project(":core:appinfo"))
      implementation(project(":core:nav"))
      implementation(project(":core:storage"))
      implementation(project(":core:ui"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:sharing:model"))
      implementation(project(":feature:sharing:sharedassets"))
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:sharing:viewing"))
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.kermit)
    }
  }
}

dependencies {
  // gitlive firebase-auth's Android artifact is versioned by the Firebase BOM.
  implementation(platform(libs.firebase.bom))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
}
