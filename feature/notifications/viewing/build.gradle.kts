plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.notifications.viewing"
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

  // HOW a notification is shown, never WHY (design §3). Rule: core:* and :model only — not a
  // feature datamanager, not feature:sync:data, not :engine. If something here needs to know about
  // a task or a squawk, it belongs in :engine instead.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:nav"))
      implementation(project(":feature:notifications:model"))
      // The N1 bodies a push message names by key (design §7.6). Allowed by §3 — viewing may depend
      // on :sharedassets — and shared with P5's iOS extension, which renders the same keys.
      implementation(project(":feature:notifications:sharedassets"))

      implementation(libs.compose.runtime)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.koin.core)
      implementation(libs.kermit)
    }
  }
}

dependencies {
  "androidMainImplementation"(libs.androidx.core.ktx)
  "androidMainImplementation"(libs.koin.android)
  "androidMainImplementation"(libs.firebase.messaging)
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}
