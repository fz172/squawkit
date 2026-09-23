plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.tasks.dashboard"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }

    withHostTest {}
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:analytics"))
      implementation(project(":core:datetime"))
      implementation(project(":core:model"))
      implementation(project(":core:template"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:ads:datamanager"))
      implementation(project(":feature:ads:model"))
      implementation(project(":feature:ads:viewing"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:comments:dashboard"))
      implementation(project(":feature:comments:datamanager"))
      implementation(project(":feature:dashboard:api"))
      implementation(project(":feature:datalog:model"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:search:datamanager"))
      implementation(project(":feature:search:model"))
      implementation(project(":feature:search:sharedassets"))
      implementation(project(":feature:search:viewing"))
      implementation(project(":feature:squawk:dashboard"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(project(":feature:tasks:viewing"))
      implementation(libs.components.resources)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.core)
      implementation(libs.kotlinx.datetime)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

compose.resources {
  publicResClass = true
}
