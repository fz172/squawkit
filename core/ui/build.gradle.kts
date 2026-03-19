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
    namespace = "dev.fanfly.wingslog.core.ui"
    compileSdk = 36
    minSdk = 33
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:model"))
      api(compose.ui)
      api(compose.components.uiToolingPreview)
      api(compose.material3)
      api(compose.materialIconsExtended)
      api(compose.components.resources)
      api(libs.kotlinx.datetime)
      implementation(libs.coil.compose)
      implementation(libs.coil.network.ktor3)
      implementation(libs.ktor.client.core)
    }
    androidMain.dependencies {
      implementation(libs.ktor.client.okhttp)
      implementation(libs.androidx.compose.ui.tooling)
      implementation(libs.androidx.compose.ui.test.manifest)
    }
  }
}
