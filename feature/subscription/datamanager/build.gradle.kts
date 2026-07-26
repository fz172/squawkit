plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.subscription.datamanager"
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

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      implementation(project(":core:storage"))
      implementation(project(":core:appinfo"))
      // `api`: SubscriptionModule exposes BillingManager to consumers wiring the purchase UI.
      api(project(":feature:subscription:model"))
      implementation(project(":feature:developeroptions:datamanager"))
      implementation(libs.gitlive.firebase.auth)

      // Logging
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      // DI
      implementation(libs.koin.android)
      // RevenueCat lives behind this module's `platformBillingModule` actual. Declared per-platform
      // because the SDK publishes no Kotlin/JS variant — jsMain binds UnsupportedBillingManager.
      implementation(project(":feature:subscription:billing"))
    }
    iosMain.dependencies {
      implementation(project(":feature:subscription:billing"))
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
