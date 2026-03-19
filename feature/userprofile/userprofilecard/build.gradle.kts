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
    namespace = "dev.fanfly.wingslog.feature.userprofile.userprofilecard"
    compileSdk = 36
    minSdk = 33
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:model"))

      // Compose
      implementation(compose.ui)
      implementation(compose.material3)
      implementation(compose.components.resources)

      implementation(project.dependencies.platform(libs.androidx.compose.bom))
    }
  }
}