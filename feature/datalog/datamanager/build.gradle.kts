plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kover)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.datalog.datamanager"
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
      implementation(project(":core:model"))
      implementation(project(":core:storage"))
      implementation(project(":core:file"))
      implementation(project(":core:datetime"))
      implementation(project(":core:auth"))
      implementation(project(":core:template"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:datalog:model"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.kotlinx.datetime)
      implementation(libs.koin.core)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kermit)
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
