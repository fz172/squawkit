plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.attachment.datamanager"
    compileSdk = 37
    minSdk = 33

    withHostTest {
    }
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:storage"))
      api(project(":feature:attachment:model"))
      api(libs.kotlinx.coroutines.core)
      api(libs.koin.core)
      implementation(project(":core:auth"))
      implementation(project(":core:datetime"))
      implementation(project(":core:model"))
      implementation(libs.kermit)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

dependencies {
  "androidMainImplementation"(libs.koin.android)
  "androidMainImplementation"(libs.androidx.core.ktx)
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
  "androidHostTestImplementation"(libs.sqldelight.sqlite.driver)
}
