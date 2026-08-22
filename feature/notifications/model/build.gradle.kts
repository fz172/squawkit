plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.notifications.model"
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
      // UrgencyRank's ladder mappings (design §6.1) need the domain enums directly — DueStatus and
      // SquawkStatus/SquawkPriority — not a second copy of them. Both are lightweight model modules
      // (enums and data classes, no manager/business logic), so this stays the same kind of
      // dependency as :core:model, just two of them.
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:squawk:model"))
    }
  }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
