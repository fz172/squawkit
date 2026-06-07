plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.ui.adaptive"
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


  iosArm64()
  iosSimulatorArm64()

  js(IR) {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui:theme"))
      implementation(project(":core:ui:widget:avataricon"))
      implementation(project(":core:sharedassets"))
      api(libs.compose.ui)
      api(libs.compose.ui.backhandler)
      api(libs.material3)
      api(libs.material3.adaptive.navigation.suite)
      api(libs.components.resources)
      api(libs.material.icons.extended)
    }
  }
}


dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
