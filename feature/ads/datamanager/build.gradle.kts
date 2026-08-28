plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kover)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.ads.datamanager"
    compileSdk = 37
    minSdk = 33

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
      implementation(project(":feature:ads:model"))
      implementation(project(":core:lifecycle"))
      implementation(project(":core:appinfo"))
      implementation(project(":feature:subscription:datamanager"))
      implementation(project(":feature:developeroptions:datamanager"))
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.koin.core)
    }
  }
}

dependencies {
  "androidMainImplementation"(libs.user.messaging.platform)
  "androidMainImplementation"(libs.koin.android)
  "androidHostTestImplementation"(project(":core:model"))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}
