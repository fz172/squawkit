plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.datalog.update"
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
      implementation(project(":feature:ads:model"))
      implementation(project(":feature:ads:datamanager"))
      implementation(project(":feature:ads:viewing"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:datalog:model"))
      implementation(project(":feature:datalog:datamanager"))
      implementation(project(":feature:datalog:viewing"))
      implementation(project(":feature:datalog:sharedassets"))
      implementation(project(":core:template"))
      implementation(project(":core:nav"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:analytics"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:datetime"))

      implementation(libs.koin.core)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.material3)
      implementation(libs.material.icons.extended)
      implementation(libs.kermit)
      implementation(libs.compose.foundation)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.compose.ui.backhandler)
      implementation(libs.jetbrains.lifecycle.runtime.compose)

      implementation(libs.kotlinx.datetime)
      implementation(libs.components.resources)
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

compose.resources {
  publicResClass = true
}
