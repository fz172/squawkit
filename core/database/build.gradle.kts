plugins {
  alias(libs.plugins.android.multiplatform.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)
  androidLibrary {
    namespace = "dev.fanfly.wingslog.core.database"
    compileSdk = 36
    minSdk = 33
  }

  sourceSets {
    commonMain.dependencies {
      api(libs.gitlive.firebase.firestore)
      api(libs.gitlive.firebase.auth)
      api(libs.kotlinx.datetime)

      // DI
      api(libs.koin.core)

      implementation(project.dependencies.platform(libs.firebase.bom))
    }
    androidMain.dependencies {
      api(project(":core:model"))
      api(project(":core:auth"))
      api(project(":core:ui"))

      // Firebase Data
      api(libs.firebase.firestore)

      // DI
      implementation(libs.koin.android)

      // Logging
      implementation(libs.kermit)

      // Coroutines
      implementation(libs.androidx.core.ktx)
    }
  }
}
