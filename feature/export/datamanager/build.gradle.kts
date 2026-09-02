import java.util.Properties

plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kover)
}

val versionPropsFile = rootProject.file("version.properties")
val readmeTemplateFile =
  layout.projectDirectory.file("src/commonMain/resources/export_readme_template.txt")

val generateExportVersionKt by tasks.registering {
  val outputDir = layout.buildDirectory.dir(
    "generated/exportVersion/commonMain/kotlin/dev/fanfly/wingslog/feature/export/datamanager/impl"
  )
  inputs.file(versionPropsFile)
  outputs.dir(outputDir)

  doFirst {
    val props = Properties().apply {
      if (versionPropsFile.exists()) versionPropsFile.inputStream()
        .use { load(it) }
    }
    val versionName =
      "${props["major"]}.${props["minor"]}.${props["buildDate"]}"
    val versionCode = props["versionCode"]?.toString()
      .orEmpty()
    val displayVersion = if (versionCode.isBlank()) {
      "SquawkIt $versionName"
    } else {
      "SquawkIt $versionName ($versionCode)"
    }
    outputDir.get().asFile.also { it.mkdirs() }
      .resolve("GeneratedExportVersionInfo.kt")
      .writeText(
        "package dev.fanfly.wingslog.feature.export.datamanager.impl\n\n" +
          "const val GENERATED_EXPORT_APP_VERSION = \"$displayVersion\"\n"
      )
  }
}

val generateExportReadmeTemplateKt by tasks.registering {
  val outputDir = layout.buildDirectory.dir(
    "generated/exportReadmeTemplate/commonMain/kotlin/dev/fanfly/wingslog/feature/export/datamanager/impl"
  )
  inputs.file(readmeTemplateFile)
  outputs.dir(outputDir)

  doFirst {
    outputDir.get().asFile.also { it.mkdirs() }
      .resolve("GeneratedExportReadmeTemplate.kt")
      .writeText(
        "package dev.fanfly.wingslog.feature.export.datamanager.impl\n\n" +
          "const val GENERATED_EXPORT_README_TEMPLATE = ${
            readmeTemplateFile.asFile.readText()
              .toKotlinStringLiteral()
          }\n"
      )
  }
}

fun String.toKotlinStringLiteral(): String =
  buildString {
    append("\"")
    this@toKotlinStringLiteral.forEach { char ->
      when (char) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> append(char)
      }
    }
    append("\"")
  }

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.feature.export.datamanager"
    compileSdk = 37
    minSdk = 33

    withHostTest {
    }
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain {
      // `builtBy`, not a bare directory. A plain path carries no task dependency, so every
      // compilation consuming these sources had to be named in a `tasks.configureEach` filter
      // matching "compile*Kotlin*" — and the AGP KMP Android task is `compileAndroidMain`, which
      // that pattern never matched. Gradle 9 fails the gap as an implicit dependency, so any build
      // that actually had to regenerate these files could not then compile them. Declaring the
      // dependency on the source itself is what removes the filter, and the class of bug with it.
      kotlin.srcDir(
        files(layout.buildDirectory.dir("generated/exportVersion/commonMain/kotlin"))
          .builtBy(generateExportVersionKt)
      )
      kotlin.srcDir(
        files(layout.buildDirectory.dir("generated/exportReadmeTemplate/commonMain/kotlin"))
          .builtBy(generateExportReadmeTemplateKt)
      )
    }

    commonMain.dependencies {
      implementation(project(":core:datetime"))
      implementation(project(":core:template"))
      implementation(project(":core:storage"))
      implementation(project(":core:firebase"))
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
      implementation(libs.ktor.client.core)
      implementation(libs.gitlive.firebase.auth)
      implementation(libs.gitlive.firebase.firestore)
      implementation(libs.gitlive.firebase.functions)
      implementation(libs.gitlive.firebase.storage)
    }
  }
}

dependencies {
  "androidMainImplementation"(libs.koin.android)
  "androidMainImplementation"(platform(libs.firebase.bom))
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.mockk)
  "androidHostTestImplementation"(libs.truth)
  "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
  // Reflects over Wire proto fields to prove the export wire docs map all of them.
  "androidHostTestImplementation"(kotlin("reflect"))
}
