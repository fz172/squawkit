plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.developeroptions.aiecho"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }

    withHostTest {
    }
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  // The AI echo round trip in Developer Options: phase A's exit check
  // (docs/ai/task_population_design.md §15). Its own module so neither core:ai nor the settings
  // screen carries the fleet and scope dependencies it needs to pick a Thing.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ai"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:model"))
      implementation(project(":core:storage"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:developeroptions:plugin"))
      implementation(project(":feature:fleet:datamanager"))

      implementation(libs.compose.runtime)
      implementation(libs.koin.core)
      implementation(libs.components.resources)
      implementation(libs.kotlinx.coroutines.core)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.androidx.compose.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

compose.resources {
  publicResClass = true
}
