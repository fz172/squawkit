plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.auth"
    compileSdk = 37
    minSdk = 33

    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(libs.gitlive.firebase.auth)
      api(libs.gitlive.firebase.functions)
      api(libs.koin.core)
      implementation(libs.kermit)
    }
    jsMain.dependencies {
      // For kotlinx.coroutines' Promise.await() used by the Firebase JS popup sign-in.
      implementation(libs.kotlinx.coroutines.core)
    }
    androidMain.dependencies {
      api(project(":core:model"))
      // CurrentActivityProvider: Sign in with Apple is a Custom Tab flow here and needs a
      // foreground Activity (#408). api, not implementation — it is a constructor parameter of
      // AuthManagerImpl, so the DI module that builds it has to see the type.
      api(project(":core:lifecycle"))

      // Auth & Network
      api(libs.play.services.auth)
      api(libs.androidx.credentials)
      api(libs.googleid)

      // DI
      implementation(libs.koin.android)

      // Coroutines
      implementation(libs.androidx.core.ktx)

      implementation(project.dependencies.platform(libs.firebase.bom))
    }
  }
}
