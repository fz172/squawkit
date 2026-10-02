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
  // SuggestionMapper arrive in T15. It depends only on what they need.
  sourceSets {
    commonMain.dependencies {
      api(project(":feature:tasks:suggestions:model"))
      implementation(project(":core:model"))
      implementation(libs.koin.core)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
}
