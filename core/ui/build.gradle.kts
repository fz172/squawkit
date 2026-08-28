plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

compose.resources {
  publicResClass = true
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.ui"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }

    withHostTest {
    }
    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:datetime"))
      api(project(":core:model"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      api(libs.compose.ui)
      api(libs.material3)
      api(libs.material3.adaptive.navigation.suite)
      api(libs.material.icons.extended)
      api(libs.components.resources)
      api(libs.kotlinx.datetime)
      api(libs.compose.ui.tooling.preview)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidRuntimeClasspath"(libs.androidx.compose.ui.tooling)
  "androidRuntimeClasspath"(libs.androidx.compose.ui.test.manifest)
}
