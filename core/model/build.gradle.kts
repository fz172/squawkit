plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.wire)
}

android {
  namespace = "dev.fanfly.wingslog.core.model"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  jvmToolchain(21)

  androidTarget {
    compilerOptions {
    }
  }

  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain {
      dependencies {
        api(libs.wire.runtime)
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
