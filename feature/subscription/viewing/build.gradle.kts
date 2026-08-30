plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.subscription.viewing"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }

    withHostTest {
    }
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:template"))
      implementation(project(":core:model"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:auth"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:subscription:datamanager"))

      implementation(libs.koin.compose.viewmodel)
      implementation(libs.compose.foundation)
      implementation(project(":core:datetime"))
      implementation(libs.components.resources)
      implementation(libs.kotlinx.datetime)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
    // The RevenueCat paywall / Customer Center UI is Android+iOS only; jsMain's actual renders
    // nothing. See PaywallHost.kt.
    androidMain.dependencies {
      implementation(project(":feature:subscription:billing"))
    }
    iosMain.dependencies {
      implementation(project(":feature:subscription:billing"))
    }
  }
}

dependencies {
  // gitlive firebase-auth (via core:auth) resolves its Android artifact versions from the BOM.
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}
