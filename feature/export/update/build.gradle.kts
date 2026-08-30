plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.export.update"
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
      implementation(project(":feature:export:datamanager"))
      implementation(project(":feature:export:sharedassets"))
      implementation(project(":feature:subscription:datamanager"))
      implementation(project(":feature:subscription:viewing"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":core:template"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))

      implementation(libs.koin.compose.viewmodel)
      implementation(libs.compose.foundation)
      implementation(libs.components.resources)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.kotlinx.datetime)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
    }
  }
}

dependencies {
  "androidMainImplementation"(libs.androidx.core.ktx)
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

compose.resources {
  publicResClass = true
}
