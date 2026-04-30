plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.sqldelight)
}

android {
  namespace = "dev.fanfly.wingslog.core.storage"
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

  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(libs.sqldelight.runtime)
      api(libs.sqldelight.coroutines.extensions)
      api(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.datetime)
      api(libs.koin.core)
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      api(libs.sqldelight.android.driver)
    }
    iosMain.dependencies {
      api(libs.sqldelight.native.driver)
    }
  }
}

sqldelight {
  databases {
    create("WingsLogDatabase") {
      packageName.set("dev.fanfly.wingslog.core.storage.db")
    }
  }
}
