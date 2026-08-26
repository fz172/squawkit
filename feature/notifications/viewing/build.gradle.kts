plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.notifications.viewing"
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
    androidMain.dependencies {
      // NotificationManagerCompat / NotificationChannel / NotificationCompat.Builder.
      implementation(libs.androidx.core.ktx)
      implementation(libs.koin.android)
      // FirebaseMessagingService, for the N1 push receiver (design §5.5, §7.6). This module renders
      // the message — turning a data map into a PendingNotification is display, not a decision —
      // and forwards onNewToken to the PushTokenSink so it never has to know what a token is for.
      implementation(libs.firebase.messaging)
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
