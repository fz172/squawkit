plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.firebase"
    compileSdk = 37
    minSdk = 33
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(libs.gitlive.firebase.storage)
      api(libs.gitlive.firebase.functions)
      api(libs.koin.core)
    }
    androidMain.dependencies {
      implementation(project.dependencies.platform(libs.firebase.bom))
    }
  }
}
