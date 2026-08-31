import java.util.Properties

plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.appinfo"
    compileSdk = 37
    minSdk = 33
  }

  js {
    browser()
  }

  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    val iosMain =
      sourceSets.findByName("iosMain") ?: sourceSets.create("iosMain")
    iosMain.apply {
      dependsOn(commonMain.get())
    }
    sourceSets.findByName("iosX64Main")
      ?.dependsOn(iosMain)
    sourceSets.findByName("iosArm64Main")
      ?.dependsOn(iosMain)
    sourceSets.findByName("iosSimulatorArm64Main")
      ?.dependsOn(iosMain)

    commonMain {
      kotlin.srcDir(layout.buildDirectory.dir("generated/commonMain/kotlin"))
    }

    commonMain.dependencies {
      implementation(libs.compose.ui)
      // configureLogging() sets Kermit's min severity from the build flavor (#276).
      api(libs.kermit)
    }

    androidMain.dependencies {
      implementation(libs.compose.ui)
      implementation(project.dependencies.platform(libs.androidx.compose.bom))
    }
  }
}

val versionPropsFile = rootProject.file("version.properties")

// Generated into commonMain, not jsMain: every platform stamps its versionCode from this same file
// (Android in app/build.gradle.kts, iOS via the scheme pre-action that writes Version.xcconfig), so
// the constant is exact everywhere and needs no Context or NSBundle to read (#728).
val generateVersionKt by tasks.registering {
  val outputDir = layout.buildDirectory.dir(
    "generated/commonMain/kotlin/dev/fanfly/wingslog/core/appinfo"
  )
  outputs.dir(outputDir)
  inputs.file(versionPropsFile)
  doFirst {
    val props = Properties().apply {
      if (versionPropsFile.exists()) versionPropsFile.inputStream()
        .use { load(it) }
    }
    // One string on all three platforms: "1.0.260828(1400)".
    val marketingVersion = "${props["major"]}.${props["minor"]}.${props["buildDate"]}"
    val versionCode = (props["versionCode"] as? String)?.toIntOrNull() ?: 0

    outputDir.get().asFile.also { it.mkdirs() }
      .resolve("GeneratedVersionInfo.kt")
      .writeText(
        "package dev.fanfly.wingslog.core.appinfo\n\n" +
          "internal const val GENERATED_VERSION_NAME = \"$marketingVersion\"\n\n" +
          "/**\n" +
          " * This build's versionCode, as a number rather than a substring of the display name.\n" +
          " *\n" +
          " * `min_app_version` is a versionCode *floor*, and comparing against it is what stops a\n" +
          " * client rendering a template it has no code for (template_system_design.md §6).\n" +
          " */\n" +
          "const val APP_VERSION_CODE = $versionCode\n"
      )
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>()
  .configureEach { dependsOn(generateVersionKt) }
