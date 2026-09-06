plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.search.datamanager"
    compileSdk = 37
    minSdk = 33

    withHostTest {
    }
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":feature:search:model"))
      api(project(":core:model"))
      implementation(project(":core:datetime"))
      api(libs.koin.core)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
}
