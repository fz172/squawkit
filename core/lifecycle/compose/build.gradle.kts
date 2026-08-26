plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.lifecycle.compose"
  compileSdk = 37

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

  iosArm64()
  iosSimulatorArm64()

  js(IR) {
    browser()
  }

  // The Compose half of core:lifecycle, split out rather than folded into the parent so that
  // `:core:lifecycle` stays Compose-free. feature/ads/datamanager depends on the parent, and
  // AGENTS.md forbids a datamanager depending on UI — putting Compose in the parent would make that
  // violation transitive and invisible. Same split as core:ui / core:ui:adaptive / core:ui:theme.
  sourceSets {
    commonMain.dependencies {
      api(project(":core:lifecycle"))
      implementation(libs.compose.runtime)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
  }
}
