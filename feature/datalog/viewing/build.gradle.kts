plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.datalog.viewing"
    compileSdk = 37
    minSdk = 33

    androidResources {
      enable = true
    }

    withHostTest {
    }
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":feature:ads:model"))
      implementation(project(":feature:ads:datamanager"))
      implementation(project(":feature:ads:viewing"))
      implementation(project(":core:nav"))
      implementation(libs.androidx.navigation.compose)
      implementation(libs.compose.ui.backhandler)
      implementation(project(":core:analytics"))
      implementation(project(":core:appinfo"))
      implementation(project(":core:model"))
      implementation(project(":core:auth"))
      implementation(project(":core:datetime"))
      implementation(project(":core:template"))
      implementation(project(":core:ui"))
      implementation(project(":core:sharedassets"))
      implementation(project(":core:ui:adaptive"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:attachment:model"))
      implementation(project(":feature:attachment:viewing"))
      implementation(project(":feature:search:sharedassets"))
      implementation(project(":feature:search:viewing"))
      implementation(project(":feature:datalog:model"))
      implementation(project(":feature:datalog:datamanager"))
      implementation(project(":feature:datalog:sharedassets"))
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.material3)
      implementation(libs.material.icons.extended)
      implementation(libs.components.resources)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.coil.compose)
      implementation(libs.coil.network.ktor3)
      implementation(libs.ktor.client.core)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.kermit)
    }
    androidMain.dependencies {
      implementation(libs.ktor.client.okhttp)
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
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidMainImplementation"(platform(libs.androidx.compose.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

compose.resources {
  publicResClass = true
}
