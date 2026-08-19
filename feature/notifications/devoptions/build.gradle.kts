plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.notifications.devoptions"
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

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  // The notification Developer Options section — test sends, run-scan-now, reset watermarks, scan
  // diagnostics (design §11). Its own module, not folded into :engine or :settings, for the same
  // reason feature:stresstest:config is separate from feature:stresstest: it contributes
  // DeveloperOptionsExtra + DeveloperOptionsNavContributor, so it needs :engine's scanner and
  // :viewing's notifier — dependencies neither :settings nor feature:shell should carry.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:developeroptions:plugin"))
      implementation(project(":feature:notifications:model"))
      implementation(project(":feature:notifications:engine"))
      implementation(project(":feature:notifications:viewing"))

      implementation(libs.compose.runtime)
      implementation(libs.koin.core)
    }
  }
}
