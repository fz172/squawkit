plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.tasks.suggestions.update"
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

  // The suggestions screen (design §3, §9): today the template's starter pack, moved here from
  // feature:tasks:update; the AI review joins it in phase C.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:tasks:suggestions:model"))
      implementation(project(":feature:tasks:suggestions:datamanager"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":core:template"))
      implementation(project(":core:nav"))
      implementation(project(":core:analytics"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:ai"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:datetime"))

      implementation(libs.koin.compose.viewmodel)
      implementation(libs.kermit)
      implementation(libs.compose.foundation)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.compose.ui.backhandler)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.components.resources)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

compose.resources {
  publicResClass = true
}
