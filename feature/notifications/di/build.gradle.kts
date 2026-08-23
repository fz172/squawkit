plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.notifications.di"
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

  // The feature's uber Koin module: bundles every notification submodule's own Koin module into
  // the one entry commonAppModules lists, so core/di depends on this single module rather than on
  // all six notification submodules directly. Depends on every notification submodule that
  // contributes a Koin module; contains no business logic of its own.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:notifications:analytics"))
      implementation(project(":feature:notifications:permission"))
      implementation(project(":feature:notifications:viewing"))
      implementation(project(":feature:notifications:datamanager"))
      implementation(project(":feature:notifications:settings"))
      implementation(project(":feature:notifications:engine"))
      implementation(project(":feature:notifications:devoptions"))
      implementation(libs.koin.core)
    }
  }
}
