plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kover)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.tasks.datamanager"
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
      implementation(project(":core:datetime"))
      implementation(project(":core:storage"))
      // readingFor / MeterKeys — a rule schedules against a meter key now (#759).
      implementation(project(":core:template"))
      implementation(libs.gitlive.firebase.auth)
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:comments:datamanager"))
      implementation(project(":feature:comments:model"))
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
