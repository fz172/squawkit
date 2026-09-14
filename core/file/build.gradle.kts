plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

// Byte-level file utilities shared by storage and features: hashing and compression, each an
// expect/actual over the platform's own implementation. No storage, no Firebase, no UI.
kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.file"
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
      implementation(libs.kotlinx.coroutines.core)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    sourceSets.getByName("androidHostTest")
      .dependencies {
        implementation(libs.junit)
        implementation(libs.truth)
        implementation(libs.kotlinx.coroutines.test)
      }
  }
}
