plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.squawk.update"
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

compose.resources {
  publicResClass = true
}

kotlin {
  jvmToolchain(21)

  androidTarget()

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:nav"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:analytics"))
      implementation(project(":core:ui"))
      implementation(project(":core:datetime"))
      implementation(project(":feature:squawk:model"))
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:squawk:sharedassets"))
      implementation(project(":feature:squawk:viewing"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:featurelab:datamanager"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:attachment:sharedassets"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:attachment:viewing"))
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.kermit)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui.backhandler)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.components.resources)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
