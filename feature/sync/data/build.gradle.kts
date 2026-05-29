plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "dev.fanfly.wingslog.feature.sync.data"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
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
      api(project(":core:storage"))
      api(project(":core:firebase"))
      api(project(":feature:attachment:datamanager"))
      api(libs.gitlive.firebase.auth)
      api(libs.gitlive.firebase.firestore)
      api(libs.gitlive.firebase.storage)
      api(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.datetime)
      api(libs.koin.core)
      implementation(libs.ktor.client.core)
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      implementation(libs.koin.android)
      implementation(libs.ktor.client.okhttp)
      implementation(libs.work.runtime.ktx)
    }
    iosMain.dependencies {
      implementation(libs.ktor.client.darwin)
    }
    jsMain.dependencies {
      implementation(libs.ktor.client.js)
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.sqldelight.sqlite.driver)
}
