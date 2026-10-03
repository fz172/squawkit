plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.tasks.suggestions.datamanager"
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

  // Suggested tasks' data layer (design §7): TaskSuggestionManager, SuggestionContextBuilder and
  // SuggestionMapper.
  sourceSets {
    commonMain.dependencies {
      api(project(":feature:tasks:suggestions:model"))
      // `api`: the manager's surface speaks AiEligibility, AiStartResult and DueMetadata.
      api(project(":core:ai"))
      api(project(":feature:tasks:model"))
      implementation(project(":core:model"))
      implementation(project(":core:storage"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:template"))
      implementation(project(":core:datetime"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(libs.koin.core)
      implementation(libs.kermit)
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.coroutines.core)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}
