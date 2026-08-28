plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.sqldelight)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.storage"
    compileSdk = 37
    minSdk = 33

    withHostTest {
    }
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
    sourceSets.getByName("jsMain")
      .dependencies {
      api(libs.sqldelight.web.worker.driver)
      // sql.js worker prebuilt by Cash App + the sql.js WASM engine it loads.
      implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.3.2"))
      implementation(npm("sql.js", "1.8.0"))
    }
    sourceSets.getByName("androidHostTest")
      .dependencies {
        implementation(libs.junit)
        implementation(libs.truth)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.sqldelight.sqlite.driver)
      }
  }
}

sqldelight {
  databases {
    create("WingsLogDatabase") {
      packageName.set("dev.fanfly.wingslog.core.storage.db")
      version = 6
      // Required for the browser sql.js web-worker driver (async). Mobile sync drivers
      // wrap the async-generated schema via Schema.synchronous() in their DriverFactory.
      generateAsync.set(true)
    }
  }
}
