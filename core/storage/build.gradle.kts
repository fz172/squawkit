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

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(project(":core:model"))
      api(libs.sqldelight.runtime)
      api(libs.sqldelight.coroutines.extensions)
      api(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.datetime)
      api(libs.koin.core)
      api(libs.wire.runtime)
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      api(libs.sqldelight.android.driver)
      implementation(libs.koin.android)
    }
    iosMain.dependencies {
      api(libs.sqldelight.native.driver)
    }
  }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.sqldelight.sqlite.driver)
}

sqldelight {
  databases {
    create("WingsLogDatabase") {
      packageName.set("dev.fanfly.wingslog.core.storage.db")
      version = 3
    }
  }
}
