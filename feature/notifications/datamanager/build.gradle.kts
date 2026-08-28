plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.notifications.datamanager"
    compileSdk = 37
    minSdk = 33

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
      implementation(project(":core:storage"))
      implementation(project(":core:model"))
      implementation(project(":core:auth"))
      implementation(libs.gitlive.firebase.firestore)
      implementation(libs.koin.core)
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:notifications:model"))
      implementation(libs.gitlive.firebase.auth)

      implementation(libs.kermit)
    }
  }
}

dependencies {
  "androidMainImplementation"(libs.koin.android)
  "androidMainImplementation"(libs.firebase.messaging)
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
  "androidHostTestImplementation"(libs.sqldelight.sqlite.driver)
}
