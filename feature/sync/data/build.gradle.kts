plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.sync.data"
    compileSdk = 37
    minSdk = 33

    withHostTest {
      isReturnDefaultValues = true
    }
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:storage"))
      implementation(project(":core:template"))
      api(project(":core:firebase"))
      api(project(":feature:sync:logging"))
      api(libs.gitlive.firebase.auth)
      api(libs.gitlive.firebase.firestore)
      api(libs.gitlive.firebase.storage)
      implementation(libs.gitlive.firebase.functions)
      api(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.datetime)
      api(libs.koin.core)
      implementation(libs.ktor.client.core)
      implementation(libs.kermit)
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
  "androidMainImplementation"(libs.koin.android)
  "androidMainImplementation"(libs.ktor.client.okhttp)
  "androidMainImplementation"(libs.work.runtime.ktx)
  "androidMainImplementation"(libs.firebase.appcheck)
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
  "androidHostTestImplementation"(libs.sqldelight.sqlite.driver)
  // Reflects over the Wire proto's declared fields to prove the wire doc maps all of them.
  "androidHostTestImplementation"(kotlin("reflect"))
}
