plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kover)
}

android {
  namespace = "dev.fanfly.wingslog.feature.ads.datamanager"
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
      implementation(project(":feature:ads:model"))
      // The app-session signal the 5-unit cap resets on. A non-UI module, deliberately: AGENTS.md
      // forbids a datamanager depending on UI, which is why it is not in core/ui.
      implementation(project(":core:lifecycle"))
      implementation(project(":core:appinfo"))
      // The tier half of the gate, and the developer force-override — the same two collaborators
      // subscriptionModule already wires together for its own force-status override.
      implementation(project(":feature:subscription:datamanager"))
      implementation(project(":feature:developeroptions:datamanager"))
      implementation(libs.kotlinx.coroutines.core)
      // Declared directly rather than inherited from `core:storage`'s api(koin-core): ads never
      // read or write the EntityStore, so depending on storage just to reach Koin would add a
      // dependency the module has no other use for.
      implementation(libs.koin.core)
    }
  }
}

dependencies {
  // Only the test needs this: AdsManagerImpl calls SubscriptionManager without ever naming its
  // types, but a fake that *implements* the interface has to spell out the proto in its signatures.
  // (feature:subscription:datamanager declares core:model as `implementation`, so it does not reach
  // consumers — arguably it should be `api`, since Subscription appears in its public API.)
  testImplementation(project(":core:model"))
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
