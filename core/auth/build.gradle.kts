plugins {
  alias(libs.plugins.android.multiplatform.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  androidLibrary {
    namespace = "dev.fanfly.wingslog.core.auth"
    compileSdk = 36
    minSdk = 33
  }

  sourceSets {
    commonMain.dependencies {
      api(libs.gitlive.firebase.auth)
      implementation(project.dependencies.platform(libs.firebase.bom))
    }
    androidMain.dependencies {
      api(project(":core:model"))

      // Auth & Network
      api(libs.play.services.auth)
      api(libs.androidx.credentials)
      api(libs.googleid)

      // DI
      implementation(libs.koin.android)

      // Logging
      implementation(libs.kermit)

      // Coroutines
      implementation(libs.androidx.core.ktx)
    }
  }
}
