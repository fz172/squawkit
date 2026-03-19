plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.ui"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

compose.resources {
  publicResClass = true
}

kotlin {
  jvmToolchain(21)

  androidTarget {
    compilerOptions {
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:model"))
      api(compose.ui)
      api(compose.components.uiToolingPreview)
      api(compose.material3)
      api(compose.materialIconsExtended)
      api(compose.components.resources)
    }
    androidMain.dependencies {
      api(libs.androidx.compose.ui.tooling.preview)
      // Image Loading
      implementation(libs.coil.compose)
    }
  }
}

dependencies {
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
