plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.tasks.update"
  compileSdk = 37

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

  androidTarget {
    compilerOptions {
    }
  }

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:featurelab:datamanager"))
      implementation(project(":feature:tasks:model"))
      implementation(libs.gitlive.firebase.auth)
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(project(":feature:tasks:viewing"))
      implementation(project(":core:nav"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:analytics"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:datetime"))
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:attachment:sharedassets"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:attachment:viewing"))

      implementation(libs.koin.compose.viewmodel)
      implementation(libs.kermit)
      implementation(libs.compose.foundation)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.compose.ui.backhandler)
      implementation(libs.jetbrains.lifecycle.runtime.compose)

      implementation(libs.kotlinx.datetime)
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
