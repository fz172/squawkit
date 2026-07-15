plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.di"
  compileSdk = 37
  defaultConfig { minSdk = 33 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  jvmToolchain(21)
  androidTarget()

  js(IR) {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(libs.koin.core)

      implementation(project(":core:analytics"))
      implementation(project(":core:auth"))
      implementation(project(":core:storage"))
      implementation(project(":core:ui:theme"))
      implementation(project(":feature:aircraft:dashboard"))
      implementation(project(":feature:aircraft:update"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(project(":feature:export:datamanager"))
      implementation(project(":feature:export:update"))
      implementation(project(":feature:featurelab:datamanager"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:fleet:picker:data"))
      implementation(project(":feature:login"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:logs:update"))
      implementation(project(":feature:logs:viewing"))
      implementation(project(":feature:settings"))
      implementation(project(":feature:shell"))
      implementation(project(":feature:sharing:datamanager"))
      implementation(project(":feature:sharing:update"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:squawk:update"))
      implementation(project(":feature:sync:data"))
      implementation(project(":feature:sync:settings"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:update"))
      implementation(project(":feature:technician:datamanager"))
      implementation(project(":feature:technician:manage"))
    }
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
}
