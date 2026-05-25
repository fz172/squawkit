plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.auth"
  compileSdk = 36

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

  iosArm64()
  iosSimulatorArm64()

  js(IR) {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(libs.gitlive.firebase.auth)
      api(libs.koin.core)
      implementation(libs.kermit)
    }
    jsMain.dependencies {
      // For kotlinx.coroutines' Promise.await() used by the Firebase JS popup sign-in.
      implementation(libs.kotlinx.coroutines.core)
    }
    androidMain.dependencies {
      api(project(":core:model"))

      // Auth & Network
      api(libs.play.services.auth)
      api(libs.androidx.credentials)
      api(libs.googleid)

      // DI
      implementation(libs.koin.android)

      // Coroutines
      implementation(libs.androidx.core.ktx)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
}
