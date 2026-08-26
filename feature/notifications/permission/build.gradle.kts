plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.notifications.permission"
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
    androidMain.dependencies {
      // NotificationManagerCompat / ActivityCompat.shouldShowRequestPermissionRationale, and
      // ActivityResultLauncher for the bridge MainActivity attaches into.
      implementation(libs.androidx.core.ktx)
      implementation(libs.androidx.activity.ktx)
      implementation(libs.koin.android)
      implementation(libs.kermit)
    }
  }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
