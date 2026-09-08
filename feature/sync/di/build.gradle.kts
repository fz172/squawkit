plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.sync.di"
    compileSdk = 37
    minSdk = 33
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  // The feature's uber Koin module: bundles every submodule's own Koin module into the one entry
  // commonAppModules lists. Depends on each submodule that contributes a Koin module; no business
  // logic of its own.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:sync:logging"))
      implementation(project(":feature:sync:settings"))
      implementation(libs.koin.core)
    }
  }
}

dependencies {
  // Pins the Firebase Android artifacts the bundled submodules pull in transitively.
  "androidMainImplementation"(platform(libs.firebase.bom))
}
