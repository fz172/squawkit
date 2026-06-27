plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dev.fanfly.wingslog.composeapp"
  compileSdk = 37

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
      implementation(project(":feature:featurelab:datamanager"))
      implementation(project(":feature:settings"))
      implementation(project(":feature:login"))
      implementation(project(":feature:export:datamanager"))
      implementation(project(":feature:export:update"))
      implementation(project(":core:auth"))
      implementation(project(":core:analytics"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:nav"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:storage"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:sync:settings"))
      implementation(project(":feature:userprofile:userprofilecard"))
      implementation(project(":feature:userprofile"))
      implementation(project(":feature:userprofile:database"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:logs:viewing"))
      implementation(project(":feature:logs:update"))
      implementation(project(":feature:tasks:update"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:squawk:update"))
      implementation(project(":feature:fleet:viewing"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:aircraft:dashboard"))
      implementation(project(":feature:technician:manage"))
      implementation(project(":feature:technician:datamanager"))

      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:attachment:sharedassets"))
      implementation(project(":feature:attachment:viewing"))

      implementation(libs.compose.ui)
      implementation(libs.material3)
      implementation(libs.components.resources)
      implementation(libs.compose.foundation)
      implementation(libs.material.icons.extended)

      implementation(libs.androidx.navigation.compose)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.kermit)
      implementation(libs.coil.compose)
      implementation(libs.coil.network.ktor3)
    }

    val iosMain =
      sourceSets.findByName("iosMain") ?: sourceSets.create("iosMain")
    iosMain.apply {
      dependsOn(commonMain.get())
      dependencies {
        implementation(libs.ktor.client.darwin)
        implementation(project(":feature:stresstest:config"))
      }
    }

    sourceSets.findByName("iosX64Main")
      ?.dependsOn(iosMain)
    sourceSets.findByName("iosArm64Main")
      ?.dependsOn(iosMain)
    sourceSets.findByName("iosSimulatorArm64Main")
      ?.dependsOn(iosMain)
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
}
