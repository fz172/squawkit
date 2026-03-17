plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  id("com.google.gms.google-services")
  id("com.google.devtools.ksp")
  id("com.google.dagger.hilt.android")
}

kotlin {
  jvmToolchain(11)
}

android {
  namespace = "dev.fanfly.wingslog"
  compileSdk {
    version = release(36)
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
  }
}


dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.text)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)
  implementation(libs.googleid)
  implementation(libs.hilt.android)
  implementation(libs.flogger)
  implementation(libs.flogger.system.backend)
  implementation(libs.play.services.auth)
  // Project Features
  implementation(project(":feature:settings"))
  implementation(project(":feature:aircraft"))
  implementation(project(":core:model"))
  implementation(project(":core:ui"))
  implementation(project(":core:network"))
  implementation(project(":core:database"))
  implementation(project(":feature:userprofile:userprofilecard"))
  implementation(project(":feature:userprofile"))
  implementation(project(":feature:aircraft:database"))
  implementation(project(":feature:fleet"))
  implementation(project(":feature:fleet:database"))

  ksp(libs.hilt.compiler)

  testImplementation(libs.junit)
  testImplementation("io.mockk:mockk:1.13.10")
  testImplementation("com.google.truth:truth:1.4.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}