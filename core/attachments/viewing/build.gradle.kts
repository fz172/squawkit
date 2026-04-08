plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.core.attachments.viewing"
  compileSdk = 36
  defaultConfig { minSdk = 33 }
  buildFeatures { compose = true }
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
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      implementation(project(":core:ui"))
      implementation(project(":core:attachments:model"))
      implementation(project(":core:attachments:sharedassets"))
      implementation(project(":core:attachments:datamanager"))

      implementation(libs.runtime)
      implementation(libs.ui)
      implementation(libs.foundation)
      implementation(libs.material3)
      implementation(libs.material.icons.extended)
      implementation(libs.components.resources)
    }
    androidMain.dependencies {
      implementation(libs.androidx.activity.compose)
    }
  }
}

dependencies {
  debugImplementation(libs.androidx.compose.ui.tooling)
}
