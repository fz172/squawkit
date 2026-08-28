plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.datetime"
    compileSdk = 37
    minSdk = 33
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kotlinx.datetime)
        implementation(libs.wire.runtime)
      }
    }
  }
}
