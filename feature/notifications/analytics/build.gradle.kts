plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.notifications.analytics"
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

  androidTarget()

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  // WHAT gets reported about the scan, never what the scan decides. Deliberately does NOT depend on
  // :engine — the scanner calls this, not the other way round, and keeping the arrow one-way is what
  // stops `core:analytics` and the privacy gate from being pulled into everything that scans.
  // :model carries the shared vocabulary (ScanTrigger); nothing here needs a manager.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:notifications:model"))
      implementation(project(":core:analytics"))
      // CloudSyncSetting — the §12.3 privacy gate, without depending on feature:sync:data.
      implementation(project(":core:storage"))
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.koin.core)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
