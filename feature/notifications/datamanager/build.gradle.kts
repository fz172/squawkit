plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  // PushTokenRegistrarImpl's two @Serializable wire classes. Without this the annotation compiles
  // fine and no serializer is generated, so the push_devices write fails at *runtime* with
  // "Serializer for class 'PushDeviceTokenWire' is not found" — caught only by an on-device test.
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "dev.fanfly.wingslog.feature.notifications.datamanager"
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
      implementation(project(":core:storage"))
      // generateRandomId, for the InstallIdStore's stable per-device id (design §7.1).
      implementation(project(":core:model"))
      // PushTokenRegistrar writes users/{uid}/push_devices/{installationId} directly — plain
      // fields, not the entity sync path, because the server must read them (design §7.1). Same
      // documented exception SharingManager takes for the share ACL.
      implementation(libs.gitlive.firebase.firestore)
      implementation(libs.koin.core)
      // SyncCursorStore + CloudSyncSetting: NotificationPrefsManager must distinguish "hydration
      // has not landed yet" from "genuinely never set" (design §4.3) — DeveloperOptionsManagerImpl
      // is NOT the precedent here, because DeveloperOptions never hydrates and this does.
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:notifications:model"))
      implementation(libs.gitlive.firebase.auth)

      // Logging
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      // androidContext(), for the app version the push token doc carries.
      implementation(libs.koin.android)
      // FirebaseMessaging.getToken(), for PushTokenBootstrap. The registrar itself is
      // platform-agnostic; only reading the device's existing token needs the messaging SDK.
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
  // In-memory WingsLogDatabase for InstallIdStoreTest, same as the engine module's store tests.
  testImplementation(libs.sqldelight.sqlite.driver)
}
