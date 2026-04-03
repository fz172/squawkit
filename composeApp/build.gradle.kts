plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.composeapp"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

compose.resources {
  publicResClass = true
}

kotlin {
  jvmToolchain(21)

  androidTarget {
    compilerOptions {
    }
  }

  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach {
    it.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:settings"))
      implementation(project(":feature:aircraft"))
      implementation(project(":core:model"))
      implementation(project(":core:ui"))
      implementation(project(":core:auth"))
      implementation(project(":core:database"))
      implementation(project(":feature:userprofile:userprofilecard"))
      implementation(project(":feature:userprofile"))
      implementation(project(":feature:userprofile:database"))
      implementation(project(":feature:aircraft:database"))
      implementation(project(":feature:aircraft:inspection"))
      implementation(project(":feature:fleet"))
      implementation(project(":feature:fleet:database"))

      implementation(compose.ui)
      implementation(compose.material3)
      implementation(compose.components.resources)
      implementation(compose.foundation)
      implementation(compose.materialIconsExtended)

      implementation(libs.androidx.navigation.compose)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.kermit)
      implementation(libs.coil.compose)
      implementation(libs.coil.network.ktor3)
    }

    val iosMain = sourceSets.findByName("iosMain") ?: sourceSets.create("iosMain")
    iosMain.apply {
      dependsOn(commonMain.get())
      dependencies {
        implementation(libs.ktor.client.darwin)
      }
    }

    sourceSets.findByName("iosX64Main")?.dependsOn(iosMain)
    sourceSets.findByName("iosArm64Main")?.dependsOn(iosMain)
    sourceSets.findByName("iosSimulatorArm64Main")?.dependsOn(iosMain)
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
}
