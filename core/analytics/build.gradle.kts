plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.analytics"
    compileSdk = 37
    minSdk = 33

    withHostTest {
      isReturnDefaultValues = true
    }
  }

  iosArm64()
  iosSimulatorArm64()
  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      // CompositionLocal + the NavController screen-view observer live here, so UI hosts
      // depend on this module the same way they depend on core:ui utilities.
      api(libs.compose.runtime)
      api(libs.androidx.navigation.compose)
      api(libs.koin.core)
      implementation(libs.kermit)
    }

    androidMain.dependencies {
      implementation(libs.firebase.analytics)
      implementation(libs.koin.android)
      implementation(project.dependencies.platform(libs.androidx.compose.bom))
      implementation(project.dependencies.platform(libs.firebase.bom))
    }

    iosMain.dependencies {
      // Kotlin binding over the FirebaseAnalytics framework the Xcode project links via SPM.
      implementation(libs.gitlive.firebase.analytics)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
}
