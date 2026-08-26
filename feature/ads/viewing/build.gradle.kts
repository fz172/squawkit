plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.ads.viewing"
  compileSdk = 37

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
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

  // This is the ONLY ads module permitted to reference an ad SDK (design N4). Google Mobile Ads is
  // declared in androidMain alone; iOS links via SPM in the Swift app rather than as a Gradle
  // dependency at all (design §7.2), and jsMain stays a no-op until phase 2.
  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:ads:model"))
      implementation(project(":feature:ads:datamanager"))
      implementation(project(":feature:ads:sharedassets"))
      implementation(project(":core:analytics"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(libs.compose.runtime)
      implementation(libs.compose.ui)
      implementation(libs.compose.foundation)
      implementation(libs.material3)
      implementation(libs.components.resources)
      implementation(libs.koin.compose)
    }
    androidMain.dependencies {
      implementation(libs.play.services.ads)
    }
  }
}
