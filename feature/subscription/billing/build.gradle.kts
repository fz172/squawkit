plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.subscription.billing"
  compileSdk = 37

  defaultConfig {
    minSdk = 33
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

/**
 * Android + iOS only — deliberately **no `js(IR)` target**, unlike every other module in the tree.
 *
 * The RevenueCat KMP SDK publishes Android and iOS variants only (verified against its Gradle module
 * metadata), and purchasing is an Android/iOS capability by product decision. Isolating the SDK in a
 * module without a web target is what keeps `webApp` building: the shared `BillingManager` contract
 * lives in `feature/subscription/model`, which every target compiles, and web binds
 * `UnsupportedBillingManager` instead of anything in here.
 *
 * Adding a web target to this module will not work — it will fail to resolve the dependency.
 */
kotlin {
  jvmToolchain(21)

  androidTarget {
    compilerOptions {
    }
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:appinfo"))
      implementation(project(":core:ui:theme"))
      api(project(":feature:subscription:model"))

      implementation(libs.purchases.kmp.core)
      implementation(libs.purchases.kmp.ui)

      implementation(libs.koin.core)
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.material3)
      implementation(libs.kermit)
    }

  }

  // The SDK's iOS actuals are cinterop bindings over the bundled purchases-ios binary. Applied to
  // every iOS source set, not just `iosMain`: Kotlin requires a source set to declare all opt-ins
  // its dependencies declare, so opting in on `iosMain` alone fails configuration for the leaves.
  sourceSets
    .matching { it.name.startsWith("ios") }
    .configureEach { languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi") }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
}
