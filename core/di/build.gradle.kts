plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.di"
    compileSdk = 37
    minSdk = 33
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(libs.koin.core)

      implementation(project(":core:analytics"))
      implementation(project(":core:auth"))
      // functionsModule — the shared Cloud Functions client.
      implementation(project(":core:firebase"))
      implementation(project(":core:lifecycle"))
      implementation(project(":core:storage"))
      implementation(project(":core:template"))
      implementation(project(":core:ui:theme"))
      // Features with more than one Koin module expose a feature/<name>/di uber module, so this
      // list carries one dependency per feature rather than one per submodule.
      implementation(project(":feature:ads:di"))
      implementation(project(":feature:attachment:di"))
      implementation(project(":feature:comments:datamanager"))
      implementation(project(":feature:export:di"))
      implementation(project(":feature:fleet:di"))
      implementation(project(":feature:login"))
      implementation(project(":feature:logs:di"))
      implementation(project(":feature:notifications:di"))
      implementation(project(":feature:search:datamanager"))
      implementation(project(":feature:settings"))
      implementation(project(":feature:sharing:di"))
      implementation(project(":feature:shell"))
      implementation(project(":feature:squawk:di"))
      implementation(project(":feature:subscription:di"))
      implementation(project(":feature:sync:di"))
      implementation(project(":feature:tasks:di"))
      implementation(project(":feature:technician:di"))
      implementation(project(":feature:thing:di"))
    }
    sourceSets.getByName("androidMain")
      .dependencies {
        implementation(project.dependencies.platform(libs.firebase.bom))
    }
  }
}
