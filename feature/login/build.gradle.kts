plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.login"
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

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:appinfo"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:auth"))
      // OnboardingPreferences (hasSeenWelcome flag) reads/writes the local store.
      implementation(project(":core:storage"))
      // The onboarding name step persists through the shared local-first technician manager.
      implementation(project(":feature:technician:datamanager"))
      // SyncEngine: the account upgrade hydrates and resyncs after re-keying local data.
      implementation(project(":feature:sync:data"))
      // The ads-consent priming step: showsAds() (tier gate) + AdConsentManager (background
      // isConsentRequired() check, then presentConsentForm() from the explainer's Continue).
      implementation(project(":feature:subscription:datamanager"))
      implementation(project(":feature:ads:datamanager"))
      // AdConsentManager.presentConsentForm()'s return type — datamanager doesn't api-export it.
      implementation(project(":feature:ads:model"))
      // The notification priming step: NotificationPermission (background UNDETERMINED check, then
      // request() — the real OS dialog — from the primer's Continue).
      implementation(project(":feature:notifications:permission"))

      // Compose resources (this module owns its login strings + Google icon)
      implementation(libs.components.resources)

      // Lifecycle & DI
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.compose)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.androidx.compose.bom))
  // GitLive firebase-auth-android pins its versions via the Firebase BOM, which
  // core:auth declares as `implementation` (not exposed transitively) — so declare it here too.
  "androidMainImplementation"(platform(libs.firebase.bom))

  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

compose.resources {
  publicResClass = true
}
