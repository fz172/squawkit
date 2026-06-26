plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.attachments.datamanager"
  compileSdk = 37
  defaultConfig { minSdk = 33 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  jvmToolchain(21)
  androidTarget()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.gitlive.firebase.storage)
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      implementation(libs.koin.android)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
}
