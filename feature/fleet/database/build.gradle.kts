plugins {
  alias(libs.plugins.android.multiplatform.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)
  androidLibrary {
    namespace = "dev.fanfly.wingslog.feature.fleet.database"
    compileSdk = 36
    minSdk = 33
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      implementation(project(":core:database"))
      implementation(project(":core:auth"))

      implementation(libs.gitlive.firebase.auth)
      implementation(libs.gitlive.firebase.firestore)
      implementation(libs.koin.core)
      implementation(libs.kermit)

      implementation(project.dependencies.platform(libs.firebase.bom))
    }
    androidMain.dependencies {}
  }
}
