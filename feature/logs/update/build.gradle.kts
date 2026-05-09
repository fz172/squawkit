plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.logs.update"
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
      implementation(project(":core:ui"))
      implementation(project(":core:model"))
      implementation(project(":core:datetime"))
      implementation(project(":core:auth"))

      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:logs:sharedassets"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:sharedassets"))
      implementation(project(":feature:tasks:viewing"))
      implementation(project(":feature:tasks:update"))
      implementation(project(":feature:technician:datamanager"))
      implementation(project(":feature:technician:manage"))
      implementation(project(":feature:technician:sharedassets"))
      implementation(project(":feature:featurelab:datamanager"))

      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:attachment:sharedassets"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:attachment:viewing"))

      implementation(libs.kotlinx.datetime)

      // Compose
      implementation(libs.compose.ui)
      implementation(libs.compose.foundation)
      implementation(libs.material3)
      implementation(libs.material.icons.extended)
      implementation(libs.components.resources)

      // Navigation
      implementation(libs.androidx.navigation.compose)
      implementation(libs.compose.ui.backhandler)

      // Lifecycle & DI
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)

      // Logging
      implementation(libs.kermit)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(platform(libs.androidx.compose.bom))
}
