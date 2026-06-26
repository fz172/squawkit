plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.sqldelight)
}

android {
  namespace = "dev.fanfly.wingslog.core.storage"
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

  iosArm64()
  iosSimulatorArm64()

  js(IR) {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:model"))
      api(libs.sqldelight.runtime)
      api(libs.sqldelight.coroutines.extensions)
      api(libs.sqldelight.async.extensions)
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
    jsMain.dependencies {
      api(libs.sqldelight.web.worker.driver)
      // sql.js worker prebuilt by Cash App + the sql.js WASM engine it loads.
      implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.3.2"))
      implementation(npm("sql.js", "1.8.0"))
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
      // Required for the browser sql.js web-worker driver (async). Mobile sync drivers
      // wrap the async-generated schema via Schema.synchronous() in their DriverFactory.
      generateAsync.set(true)
    }
  }
}
