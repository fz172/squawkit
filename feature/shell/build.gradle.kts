plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.shell"
  compileSdk = 37

  defaultConfig {
    minSdk = 33
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  jvmToolchain(21)

  androidTarget {
  }

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      // The shared app graph must reach every feature UI module it hosts — this module plays
      // the same aggregator role for composables/nav that core:di plays for Koin modules.
      implementation(project(":core:analytics"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:auth"))
      implementation(project(":core:nav"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":feature:aircraft:dashboard"))
      implementation(project(":feature:aircraft:update"))
      implementation(project(":feature:export:update"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:fleet:picker:data"))
      implementation(project(":feature:fleet:viewing"))
      implementation(project(":feature:logs:update"))
      implementation(project(":feature:settings"))
      implementation(project(":feature:subscription:datamanager"))
      implementation(project(":feature:subscription:viewing"))
      implementation(project(":feature:sharing:update"))
      // App-start retry of an owed technician-mirror publish (design §7.2).
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:squawk:update"))
      implementation(project(":feature:stresstest:config"))
      implementation(project(":core:sharedassets"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:sync:settings"))
      implementation(project(":feature:tasks:update"))
      implementation(project(":feature:technician:datamanager"))
      implementation(project(":feature:technician:manage"))

      implementation(libs.androidx.navigation.compose)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.gitlive.firebase.auth)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(platform(libs.androidx.compose.bom))

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
}
