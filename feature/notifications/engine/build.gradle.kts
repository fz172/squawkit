plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.notifications.engine"
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
      implementation(project(":core:lifecycle"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:notifications:model"))
      implementation(project(":feature:notifications:analytics"))
      implementation(project(":feature:notifications:permission"))
      implementation(project(":feature:notifications:viewing"))
      implementation(project(":feature:notifications:datamanager"))
      implementation(project(":feature:notifications:sharedassets"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:squawk:model"))
      implementation(project(":feature:sharing:model"))
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.components.resources)

      implementation(libs.kermit)
    }
  }
}

dependencies {
  "androidMainImplementation"(libs.work.runtime.ktx)
  "androidMainImplementation"(libs.koin.android)
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
  "androidHostTestImplementation"(libs.sqldelight.sqlite.driver)
}
