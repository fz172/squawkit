import java.util.Properties

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kover)
}

val versionPropsFile = rootProject.file("version.properties")

val generateExportVersionKt by tasks.registering {
  val outputDir = layout.buildDirectory.dir(
    "generated/exportVersion/commonMain/kotlin/dev/fanfly/wingslog/feature/export/datamanager/impl"
  )
  inputs.file(versionPropsFile)
  outputs.dir(outputDir)

  doFirst {
    val props = Properties().apply {
      if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
    }
    val versionName = "${props["major"]}.${props["minor"]}.${props["buildDate"]}.${props["patch"]}"
    val versionCode = props["versionCode"]?.toString().orEmpty()
    val displayVersion = if (versionCode.isBlank()) {
      "Hopply $versionName"
    } else {
      "Hopply $versionName ($versionCode)"
    }
    outputDir.get().asFile.also { it.mkdirs() }
      .resolve("GeneratedExportVersionInfo.kt")
      .writeText(
        "package dev.fanfly.wingslog.feature.export.datamanager.impl\n\n" +
          "const val GENERATED_EXPORT_APP_VERSION = \"$displayVersion\"\n"
      )
  }
}

android {
  namespace = "dev.fanfly.wingslog.feature.export.datamanager"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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
    commonMain {
      kotlin.srcDir(layout.buildDirectory.dir("generated/exportVersion/commonMain/kotlin"))
    }

    commonMain.dependencies {
      implementation(project(":core:datetime"))
      implementation(project(":core:storage"))
      implementation(project(":core:model"))
      implementation(project(":core:appinfo"))
      implementation(project(":feature:fleet:datamanager"))
      implementation(project(":feature:logs:datamanager"))
      implementation(project(":feature:tasks:datamanager"))
      implementation(project(":feature:tasks:model"))
      implementation(project(":feature:squawk:datamanager"))
      implementation(project(":feature:technician:datamanager"))
      implementation(project(":feature:attachment:datamanager"))
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.koin.core)
      implementation(libs.kermit)
    }
  }
}

tasks.configureEach {
  if (name.startsWith("compile") && name.contains("Kotlin")) {
    dependsOn(generateExportVersionKt)
  }
}

dependencies {
  implementation(platform(libs.firebase.bom))
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
