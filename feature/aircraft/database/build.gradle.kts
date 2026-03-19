plugins {
  alias(libs.plugins.android.multiplatform.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  androidLibrary {
    namespace = "dev.fanfly.wingslog.feature.aircraft.database"
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
      implementation(libs.kotlinx.datetime)

      implementation(libs.koin.core)
      implementation(libs.kermit)

      implementation(project.dependencies.platform(libs.firebase.bom))
    }
    androidMain.dependencies {
    }
    commonTest.dependencies {
      implementation(libs.junit)
      implementation("io.mockk:mockk:1.13.10")
      implementation("com.google.truth:truth:1.4.2")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    }
  }
}