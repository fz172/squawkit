plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.thing.update"
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

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:sharing:model"))
      implementation(project(":core:nav"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:logs:sharedassets"))

      // Compose
      implementation(libs.compose.ui)
      implementation(libs.compose.foundation)
      implementation(libs.material3)
      implementation(libs.material.icons.extended)
      implementation(libs.components.resources)

      // Navigation
      implementation(libs.androidx.navigation.compose)
      implementation(libs.compose.ui.backhandler)

      // Lifecycle & DI
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)

      // Logging
      implementation(libs.kermit)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.androidx.compose.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
}

compose.resources {
  publicResClass = true
}
