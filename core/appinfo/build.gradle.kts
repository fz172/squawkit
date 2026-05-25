import java.util.Properties

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

android {
  namespace = "dev.fanfly.wingslog.core.appinfo"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

val versionPropsFile = rootProject.file("version.properties")

val generateIosVersionKt by tasks.registering {
  val outputDir = layout.buildDirectory.dir(
    "generated/iosMain/kotlin/dev/fanfly/wingslog/core/appinfo"
  )
  outputs.dir(outputDir)
  inputs.file(versionPropsFile)
  doFirst {
    val props = Properties().apply {
      if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
    }
    val versionName = "${props["major"]}.${props["minor"]}" +
      ".${props["buildDate"]}.${props["patch"]}"
    outputDir.get().asFile.also { it.mkdirs() }
      .resolve("GeneratedVersionInfo.kt")
      .writeText(
        "package dev.fanfly.wingslog.core.appinfo\n\n" +
          "internal const val GENERATED_VERSION_NAME = \"$versionName\"\n"
      )
  }
}

val generateJsVersionKt by tasks.registering {
  val outputDir = layout.buildDirectory.dir(
    "generated/jsMain/kotlin/dev/fanfly/wingslog/core/appinfo"
  )
  outputs.dir(outputDir)
  inputs.file(versionPropsFile)
  doFirst {
    val props = Properties().apply {
      if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
    }
    val versionName = "${props["major"]}.${props["minor"]}" +
      ".${props["buildDate"]}.${props["patch"]}"
    outputDir.get().asFile.also { it.mkdirs() }
      .resolve("GeneratedVersionInfo.kt")
      .writeText(
        "package dev.fanfly.wingslog.core.appinfo\n\n" +
          "internal const val GENERATED_VERSION_NAME = \"$versionName\"\n"
      )
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
    val iosMain = sourceSets.findByName("iosMain") ?: sourceSets.create("iosMain")
    iosMain.apply {
      dependsOn(commonMain.get())
      kotlin.srcDir(layout.buildDirectory.dir("generated/iosMain/kotlin"))
    }
    sourceSets.findByName("iosX64Main")?.dependsOn(iosMain)
    sourceSets.findByName("iosArm64Main")?.dependsOn(iosMain)
    sourceSets.findByName("iosSimulatorArm64Main")?.dependsOn(iosMain)

    jsMain {
      kotlin.srcDir(layout.buildDirectory.dir("generated/jsMain/kotlin"))
    }

    commonMain.dependencies {
      implementation(libs.compose.ui)
    }

    androidMain.dependencies {
      implementation(libs.compose.ui)
    }
  }
}

tasks.configureEach {
  if (name.startsWith("compileKotlinIos")) dependsOn(generateIosVersionKt)
  if (name == "compileKotlinJs") dependsOn(generateJsVersionKt)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
}
