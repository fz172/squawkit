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
    namespace = "dev.fanfly.wingslog.feature.fleet"
    compileSdk = 36
    minSdk = 33
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      implementation(project(":core:ui"))
      implementation(project(":feature:fleet:database"))
      implementation(project(":feature:aircraft:database"))

      // Firebase

      // Compose
      implementation(compose.ui)
      implementation(compose.material3)
      implementation(compose.materialIconsExtended)
      implementation(compose.components.resources)

      // Navigation & Lifecycle
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.androidx.navigation.compose)

      // DI
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)

      // Logging
      implementation(libs.kermit)

      // Tooling
      implementation(compose.components.uiToolingPreview)

      implementation(project.dependencies.platform(libs.firebase.bom))
    }
    commonTest.dependencies {
      implementation(libs.junit)
      implementation("io.mockk:mockk:1.13.10")
      implementation("com.google.truth:truth:1.4.2")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    }
  }
}