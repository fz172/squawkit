plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.ui.adaptive"
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
      implementation(project(":core:ui:theme"))
      implementation(project(":core:ui:widget:avataricon"))
      implementation(project(":core:sharedassets"))
      // For LocalThingLexicon: the shell is where a Thing's lexicon enters the composition.
      api(project(":core:template"))
      api(libs.compose.ui)
      api(libs.compose.ui.backhandler)
      api(libs.material3)
      api(libs.material3.adaptive.navigation.suite)
      api(libs.components.resources)
      api(libs.material.icons.extended)
    }
    sourceSets.getByName("androidHostTest")
      .dependencies {
        implementation(libs.junit)
        implementation(libs.truth)
      }
  }
}

compose.resources {
  publicResClass = true
}
