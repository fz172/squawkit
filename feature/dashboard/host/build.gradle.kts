plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.dashboard.host"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }

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
      implementation(project(":core:analytics"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:datetime"))
      implementation(project(":core:model"))
      implementation(project(":core:nav"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:storage"))
      implementation(project(":core:template"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:comments:datamanager"))
      implementation(project(":feature:comments:model"))
      implementation(project(":feature:comments:sharedassets"))
      implementation(project(":feature:dashboard:api"))
      implementation(project(":feature:datalog:datamanager"))
      implementation(project(":feature:datalog:model"))
      implementation(project(":feature:datalog:viewing"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:logs:dashboard"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:logs:viewing"))
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:sharing:model"))
      implementation(project(":feature:squawk:dashboard"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:squawk:model"))
      implementation(project(":feature:squawk:sharedassets"))
      implementation(project(":feature:tasks:dashboard"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(project(":feature:tasks:viewing"))
      implementation(libs.androidx.navigation.compose)
      implementation(libs.components.resources)
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.core)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
  "androidHostTestImplementation"(platform(libs.androidx.compose.bom))
  "androidHostTestImplementation"(libs.androidx.compose.ui.test.junit4)
  "androidHostTestImplementation"(libs.androidx.compose.ui.test.manifest)
  "androidHostTestImplementation"(libs.robolectric)
  "androidRuntimeClasspath"(libs.androidx.compose.ui.test.manifest)
}

compose.resources {
  publicResClass = true
}
