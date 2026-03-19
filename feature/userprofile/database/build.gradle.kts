plugins {
  alias(libs.plugins.android.multiplatform.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  androidLibrary {
    namespace = "dev.fanfly.wingslog.feature.userprofile.database"
    compileSdk = 36
    minSdk = 33
  }

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

      // Firebase BOM for transitive dependency version resolution (required by core:database)
      implementation(project.dependencies.platform(libs.firebase.bom))
    }
    androidMain.dependencies {
      // DI
      implementation(libs.koin.android)
    }
  }
}