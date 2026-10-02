plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  // Load-bearing and silent when missing: the callable and job-document wire types are
  // `@Serializable`, and without this plugin that annotation generates nothing (see
  // feature/subscription/datamanager).
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.ai"
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
      // `api`: the client's surface is the job protos (AiJobKind, AiJobStatus, AiJobError) and ids.
      api(project(":core:model"))
      implementation(project(":core:firebase"))
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.gitlive.firebase.firestore)
      implementation(libs.gitlive.firebase.functions)
      implementation(libs.koin.core)
      implementation(libs.kermit)
      implementation(libs.kotlinx.coroutines.core)
    }
  }
}

dependencies {
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
}
