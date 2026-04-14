plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  id("com.google.gms.google-services")
}

kotlin {
  jvmToolchain(21)
}

android {
  namespace = "dev.fanfly.wingslog"
  compileSdk {
    version = release(36)
  }

  defaultConfig {
    applicationId = "dev.fanfly.wingslog"
    minSdk = 33
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures {
    compose = true
  }
}


dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.compose.ui.backhandler)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)

  implementation(libs.androidx.credentials)
  implementation(libs.googleid)
  implementation(libs.play.services.auth)

  implementation(libs.koin.android)
  implementation(libs.kermit)

  implementation(project(":composeApp"))
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}