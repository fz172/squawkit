plugins {
  alias(libs.plugins.android.multiplatform.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.wire)
}

wire {
  sourcePath {
    srcDir("src/main/proto")
  }
  kotlin {}
}

kotlin {
  jvmToolchain(21)

  androidLibrary {
    namespace = "dev.fanfly.wingslog.core.model"
    compileSdk = 36
    minSdk = 33
  }

  sourceSets {
    commonMain.dependencies {
      api(libs.wire.runtime)
    }
  }
}
