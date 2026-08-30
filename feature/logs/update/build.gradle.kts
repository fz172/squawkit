plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.logs.update"
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
      implementation(project(":core:template"))
      implementation(project(":core:nav"))
      implementation(project(":core:analytics"))
      implementation(project(":core:ui"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:model"))
      implementation(project(":core:datetime"))
      implementation(project(":core:auth"))

      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(project(":feature:tasks:viewing"))
      implementation(project(":feature:tasks:update"))
      implementation(project(":feature:squawk:model"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:squawk:sharedassets"))
      implementation(project(":feature:squawk:viewing"))
      implementation(project(":feature:technician:datamanager"))
      // Linked technicians in the picker come from this aircraft's share members (design §7.3).
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:technician:manage"))
      implementation(project(":feature:technician:sharedassets"))
      implementation(project(":feature:subscription:datamanager"))

      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:attachment:sharedassets"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:attachment:viewing"))

      implementation(libs.kotlinx.datetime)

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
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidMainImplementation"(platform(libs.androidx.compose.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

compose.resources {
  publicResClass = true
}
