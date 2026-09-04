plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.comments.datamanager"
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
      implementation(project(":core:model"))
      implementation(project(":core:storage"))
      implementation(project(":feature:comments:model"))
      // The author's display name comes from their own technician record — the same precedence
      // SharingManagerImpl publishes to the share roster.
      implementation(project(":feature:technician:datamanager"))
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.kermit)
      // api, not implementation: commonAppModules names commentsModule, whose type is Koin's.
      api(libs.koin.core)
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
