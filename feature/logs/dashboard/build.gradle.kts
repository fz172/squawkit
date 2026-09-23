plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.logs.dashboard"
    compileSdk = 37
    minSdk = 33

    withHostTest {
      isIncludeAndroidResources = true
    }
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:search:model"))
      implementation(project(":core:datetime"))
      implementation(project(":core:model"))
      implementation(project(":core:template"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:datalog:model"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:logs:viewing"))
      implementation(project(":feature:squawk:dashboard"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(libs.components.resources)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.core)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(platform(libs.androidx.compose.bom))
  "androidHostTestImplementation"(libs.androidx.compose.ui.test.junit4)
  "androidHostTestImplementation"(libs.androidx.compose.ui.test.manifest)
  "androidHostTestImplementation"(libs.robolectric)
  "androidRuntimeClasspath"(libs.androidx.compose.ui.test.manifest)
}
