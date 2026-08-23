plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.notifications.engine"
  compileSdk = 37

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

  // WHAT to show and WHEN (design §3) — the wide fan-in is deliberately contained to this one
  // module: it is a CONSUMER of the existing per-feature managers, never a second implementation of
  // due-status or priority logic. Everything below feeds UrgencyScanner or the N1 web detector.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:storage"))
      implementation(project(":core:lifecycle"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:notifications:model"))
      implementation(project(":feature:notifications:analytics"))
      implementation(project(":feature:notifications:permission"))
      implementation(project(":feature:notifications:viewing"))
      implementation(project(":feature:notifications:datamanager"))
      implementation(project(":feature:notifications:sharedassets"))
      // UrgencyRank's ladder mappings live on DueStatus (:tasks:model) and SquawkWithStatus
      // (:squawk:model) — datamanager modules expose their own :model transitively as
      // `implementation`, not `api`, so these need to be declared directly here too.
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:squawk:model"))
      implementation(libs.gitlive.firebase.auth)
      // getString(Res.string.…) — the notification bodies in :sharedassets are read from a
      // background scan, never a @Composable, so this module needs the resources runtime but not
      // the Compose UI plugin itself.
      implementation(libs.components.resources)

      // Logging
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      // UrgencyScanScheduler's PeriodicWorkRequest, and androidContext() to reach WorkManager.
      implementation(libs.work.runtime.ktx)
      implementation(libs.koin.android)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.sqldelight.sqlite.driver)
}
