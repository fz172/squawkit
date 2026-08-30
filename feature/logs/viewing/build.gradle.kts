plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.logs.viewing"
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
      implementation(project(":feature:ads:model"))
      implementation(project(":feature:ads:datamanager"))
      implementation(project(":feature:ads:viewing"))
      implementation(project(":core:template"))
      implementation(project(":core:ui"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))

      implementation(project(":core:datetime"))

      implementation(project(":core:model"))
      implementation(project(":core:auth"))

      implementation(project(":feature:developeroptions:datamanager"))
      implementation(project(":feature:logs:datamanager"))
      // Authorship needs the share roster to name whoever wrote a log (design §7.5).
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:technician:datamanager"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(project(":feature:tasks:viewing"))
      implementation(project(":feature:squawk:datamanager"))

      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:attachment:sharedassets"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:attachment:viewing"))

      implementation(libs.kotlinx.datetime)

      // Compose
      implementation(libs.compose.ui)
      implementation(libs.material3)
      implementation(libs.material.icons.extended)
      implementation(libs.components.resources)

      // Navigation
      implementation(libs.androidx.navigation.compose)

      // Lifecycle & DI
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)

      // Logging
      implementation(libs.kermit)
      api(libs.compose.ui.tooling.preview)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidMainImplementation"(platform(libs.androidx.compose.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

compose.resources {
  publicResClass = true
}
