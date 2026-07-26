plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.subscription.model"
  compileSdk = 37

  defaultConfig {
    minSdk = 33
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  jvmToolchain(21)

  androidTarget()

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      // `api`, not `implementation`: BillingManager exposes Flow in its public signature, so every
      // consumer (datamanager, viewing, billing) needs the coroutines types on its compile path.
      api(libs.kotlinx.coroutines.core)
    }
  }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
