plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.crash"
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
    // One Crashlytics implementation for both mobile hosts. GitLive's binding publishes android and
    // ios artifacts but no js one — there is no Firebase Crashlytics web SDK to bind — so the SDK
    // and the reporter that wraps it live here rather than in commonMain, and jsMain answers the
    // same expect with NoOpCrashReporter.
    val mobileMain = sourceSets.create("mobileMain")
      .apply { dependsOn(commonMain.get()) }
    sourceSets.findByName("androidMain")
      ?.dependsOn(mobileMain)
    // Same explicit wiring core:appinfo needs: the default hierarchy's iosMain does not exist yet
    // while this block runs, so the intermediate set and its two leaves are connected by hand.
    val iosMain =
      sourceSets.findByName("iosMain") ?: sourceSets.create("iosMain")
    iosMain.dependsOn(mobileMain)
    sourceSets.findByName("iosArm64Main")
      ?.dependsOn(iosMain)
    sourceSets.findByName("iosSimulatorArm64Main")
      ?.dependsOn(iosMain)

    commonMain.dependencies {
      api(libs.koin.core)
      // CrashUserIdBinder follows FirebaseAuth.authStateChanged; api because the DI module that
      // builds it takes the type as a constructor parameter.
      api(libs.gitlive.firebase.auth)
      implementation(libs.kermit)
      implementation(libs.kotlinx.coroutines.core)
    }

    mobileMain.dependencies {
      implementation(libs.gitlive.firebase.crashlytics)
      implementation(libs.kermit)
    }

    androidMain.dependencies {
      implementation(project.dependencies.platform(libs.firebase.bom))
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}
