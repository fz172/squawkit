plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.wire)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.model"
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
        api(libs.wire.runtime)
        implementation(libs.kotlinx.datetime)
      }
      kotlin.srcDir(layout.buildDirectory.dir("generated/source/wire/kmp"))
    }
  }
}

wire {
  sourcePath {
    srcDir("src/commonMain/proto")
  }
  kotlin {
    out = "build/generated/source/wire/kmp"
    android = false
  }
}

// Fix the "Implicit dependency" error by ensuring compile tasks depend on generateProtos
tasks.configureEach {
  if (name.startsWith("compile") && name.contains("Kotlin")) {
    dependsOn("generateProtos")
  }
}
