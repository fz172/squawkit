plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

compose.resources {
  publicResClass = true
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.composeapp"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }

    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
      // The whole feature nav graph comes in via feature:shell (composables) + core:di (Koin
      // modules); this host only declares what its own sources touch.
      implementation(project(":core:template"))
      implementation(project(":core:auth"))
      implementation(project(":core:analytics"))
      implementation(project(":core:di"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:nav"))
      implementation(project(":core:lifecycle:compose"))
      implementation(project(":core:ui"))
      implementation(project(":core:ui:theme"))
      implementation(project(":core:storage"))
      implementation(project(":feature:login"))
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:sharing:update"))
      implementation(project(":feature:shell"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:stresstest:config"))
      // IosAdConsentBridge / IosAdViewBridge — wired from MainViewController.kt, same shape as the
      // App Check bridge.
      implementation(project(":feature:ads:datamanager"))
      implementation(project(":feature:ads:viewing"))
      // IosNotificationTapDelegate — installed from MainViewController.kt before launch finishes.
      implementation(project(":feature:notifications:viewing"))
      // For SignOutCoordinator, which the corruption-recovery dialog signs out through (#550).
      implementation(project(":feature:notifications:datamanager"))
      // BgTaskUrgencyScanScheduler — its BGTaskScheduler identifier is registered from
      // MainViewController.kt before launch finishes, same as the blob scan's.
      implementation(project(":feature:notifications:engine"))

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

    androidMain.dependencies {
      implementation(project.dependencies.platform(libs.androidx.compose.bom))
      implementation(project.dependencies.platform(libs.firebase.bom))
    }

    val iosMain =
      sourceSets.findByName("iosMain") ?: sourceSets.create("iosMain")
    iosMain.apply {
      dependsOn(commonMain.get())
      dependencies {
        implementation(libs.ktor.client.darwin)
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
