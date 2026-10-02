plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.tasks.suggestions.model"
    compileSdk = 37
    minSdk = 33
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  // Suggested tasks (docs/ai/task_population_design.md §3): the screen's item and run types.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
    }
  }
}
