plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.sharing.update"
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
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.material3)
      implementation(project(":core:template"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:nav"))
      implementation(project(":core:storage"))
      implementation(project(":core:ui"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:sharing:model"))
      implementation(project(":feature:sharing:sharedassets"))
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:sharing:viewing"))
      implementation(project(":feature:subscription:datamanager"))
      implementation(project(":feature:subscription:viewing"))
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.kermit)
    }
  }
}

dependencies {
  // gitlive firebase-auth's Android artifact is versioned by the Firebase BOM.
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}
