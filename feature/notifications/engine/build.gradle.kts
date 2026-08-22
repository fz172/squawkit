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
      implementation(project(":feature:notifications:permission"))
      implementation(project(":feature:notifications:viewing"))
      implementation(project(":feature:notifications:datamanager"))
      implementation(libs.gitlive.firebase.auth)

      // Logging
      implementation(libs.kermit)
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
