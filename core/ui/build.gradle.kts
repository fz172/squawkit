plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.ui"
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

val versionPropsFile = rootProject.file("version.properties")

val generateIosVersionKt by tasks.registering {
  val outputDir = layout.buildDirectory.dir(
    "generated/iosMain/kotlin/dev/fanfly/wingslog/core/ui"
  )
  outputs.dir(outputDir)
  inputs.file(versionPropsFile)
  doFirst {
    val props = java.util.Properties().apply {
      if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
    }
    val versionName = "${props["major"]}.${props["minor"]}" +
      ".${props["buildDate"]}.${props["patch"]}"
    outputDir.get().asFile.also { it.mkdirs() }
      .resolve("GeneratedVersionInfo.kt")
      .writeText(
        "package dev.fanfly.wingslog.core.ui\n\n" +
          "internal const val GENERATED_VERSION_NAME = \"$versionName\"\n"
      )
  }
}

kotlin {
  jvmToolchain(21)

  androidTarget {
    compilerOptions {
    }
  }

  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    val iosMain by getting {
      kotlin.srcDir(layout.buildDirectory.dir("generated/iosMain/kotlin"))
    }

    commonMain.dependencies {
      api(project(":core:model"))
      api(project(":core:datetime"))
      api(libs.compose.ui)
      api(libs.material3)
      api(libs.material.icons.extended)
      api(libs.components.resources)
      api(libs.kotlinx.datetime)
      api(libs.compose.ui.tooling.preview)
      implementation(libs.coil.compose)
      implementation(libs.coil.network.ktor3)
      implementation(libs.ktor.client.core)
    }
    androidMain.dependencies {
      implementation(libs.ktor.client.okhttp)
    }
  }
}

tasks.configureEach {
  if (name.startsWith("compileKotlinIos")) dependsOn(generateIosVersionKt)
}

dependencies {
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
