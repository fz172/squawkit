plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.feature.attachment.datamanager"
  compileSdk = 37

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
      api(project(":feature:attachment:model"))
      api(libs.kotlinx.coroutines.core)
      api(libs.koin.core)
      implementation(project(":core:auth"))
      implementation(project(":core:datetime"))
      implementation(project(":core:model"))
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      implementation(libs.koin.android)
      implementation(libs.androidx.core.ktx)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
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
