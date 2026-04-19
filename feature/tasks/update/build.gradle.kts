plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.tasks.update"
  compileSdk = 36

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

  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(project(":feature:tasks:viewing"))

      implementation(project(":core:ui"))
      implementation(project(":core:database"))
      implementation(project(":core:datetime"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":core:attachments:model"))
      implementation(project(":core:attachments:datamanager"))
      implementation(project(":core:attachments:viewing"))

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
}
