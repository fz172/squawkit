plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.notifications.permission"
    compileSdk = 37
    minSdk = 33

    withHostTest {
    }
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  // The deliberate leaf of feature/notifications (design §5.1): "may we notify" is a question about
  // this build/device, not about aircraft, and answering it needs nothing else the feature owns —
  // not even :model. Rule from design §3: this module may depend on core:* only, nothing else.
  // core:lifecycle supplies Android's CurrentActivityProvider for the runtime permission prompt;
  // core:appinfo is for parity with the rest of the tree's platform-capability plumbing.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:lifecycle"))
      implementation(project(":core:appinfo"))
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.koin.core)
    }
  }
}

dependencies {
  "androidMainImplementation"(libs.androidx.core.ktx)
  "androidMainImplementation"(libs.androidx.activity.ktx)
  "androidMainImplementation"(libs.koin.android)
  "androidMainImplementation"(libs.kermit)
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}
