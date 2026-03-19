plugins {
  alias(libs.plugins.android.multiplatform.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

compose.resources {
  publicResClass = true
}

kotlin {
  jvmToolchain(21)

  androidLibrary {
    namespace = "dev.fanfly.wingslog.feature.userprofile"
    compileSdk = 36
    minSdk = 33
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:model"))
      implementation(project(":core:database"))
      implementation(project(":core:auth"))
      implementation(project(":feature:userprofile:userprofilecard"))
      implementation(project(":feature:userprofile:database"))

      // Compose
      implementation(compose.ui)
      implementation(compose.material3)
      implementation(compose.components.resources)

      // Navigation
      implementation(libs.androidx.navigation.compose)

      // Lifecycle & DI
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)

      // Logging
      implementation(libs.kermit)

      implementation(project.dependencies.platform(libs.firebase.bom))
      implementation(project.dependencies.platform(libs.androidx.compose.bom))
    }
    androidMain.dependencies {
    }
  }
}