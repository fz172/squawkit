plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.feature.userprofile.userprofilecard"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

kotlin {
  jvmToolchain(11)

  androidTarget {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
  }

  sourceSets {
    commonMain {}
    androidMain.dependencies {
      implementation(project(":core:ui"))
      implementation(project(":core:model"))

      implementation(libs.androidx.core.ktx)
      implementation(libs.androidx.lifecycle.runtime.ktx)
      
      // Compose
      implementation(libs.androidx.compose.ui)
      implementation(libs.androidx.compose.material3)
      implementation(libs.coil.compose)
    }
  }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
}
