plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.subscription.billing"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }

    withHostTest {
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
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}
