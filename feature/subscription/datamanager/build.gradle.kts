plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.subscription.datamanager"
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

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:firebase"))
      implementation(project(":core:model"))
      implementation(project(":core:storage"))
      implementation(project(":core:appinfo"))
      // `api`: SubscriptionModule exposes BillingManager to consumers wiring the purchase UI.
      api(project(":feature:subscription:model"))
      implementation(project(":feature:developeroptions:datamanager"))
      implementation(libs.gitlive.firebase.auth)
      // Calls the reconcileMyEntitlement callable (FirebaseEntitlementReconciler).
      implementation(libs.gitlive.firebase.functions)

      // Logging
      implementation(libs.kermit)
    }

    androidMain.dependencies {
      implementation(libs.koin.android)
      implementation(project(":feature:subscription:billing"))
    }

    iosMain.dependencies {
      implementation(project(":feature:subscription:billing"))
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}
