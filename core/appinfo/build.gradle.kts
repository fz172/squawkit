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

    jsMain {
      kotlin.srcDir(layout.buildDirectory.dir("generated/jsMain/kotlin"))
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

val generateJsVersionKt by tasks.registering {
  val outputDir = layout.buildDirectory.dir(
    "generated/jsMain/kotlin/dev/fanfly/wingslog/core/appinfo"
  )
  outputs.dir(outputDir)
  inputs.file(versionPropsFile)
  doFirst {
    val props = Properties().apply {
      if (versionPropsFile.exists()) versionPropsFile.inputStream()
        .use { load(it) }
    }
    // One string on all three platforms: "1.0.260828(1400)". iOS composes it from
    // MARKETING_VERSION and CURRENT_PROJECT_VERSION, Android from version.properties in
    // app/build.gradle.kts, and web here. Web previously carried no versionCode at all — see
    // below for why that mattered beyond display.
    val marketingVersion = "${props["major"]}.${props["minor"]}.${props["buildDate"]}"
    val versionCode = (props["versionCode"] as? String)?.toIntOrNull() ?: 0

    outputDir.get().asFile.also { it.mkdirs() }
      .resolve("GeneratedVersionInfo.kt")
      .writeText(
        "package dev.fanfly.wingslog.core.appinfo\n\n" +
          "internal const val GENERATED_VERSION_NAME = \"$marketingVersion\"\n\n" +
          "/**\n" +
          " * The shared versionCode, as a number rather than a substring of the display name.\n" +
          " *\n" +
          " * `min_app_version` in the template system is a versionCode *floor*, and it is what stops\n" +
          " * a client rendering a template it has no code for. Android reads its own from\n" +
          " * PackageInfo and iOS from CFBundleVersion; web has no platform source, so without this\n" +
          " * constant there is nothing on web for a floor to compare against and the gate silently\n" +
          " * does not apply there. See template_system_design.md §6 and §11.\n" +
          " */\n" +
          "internal const val GENERATED_VERSION_CODE = $versionCode\n"
      )
  }
}

tasks.configureEach {
  if (name == "compileKotlinJs") dependsOn(generateJsVersionKt)
}
