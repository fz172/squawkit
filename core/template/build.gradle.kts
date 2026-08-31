import java.util.Base64

plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "dev.fanfly.wingslog.core.template"
    compileSdk = 37
    minSdk = 33

    withHostTest {
      isReturnDefaultValues = true
    }
  }

  iosArm64()
  iosSimulatorArm64()

  js {
    browser()
  }

  sourceSets {
    commonMain {
      kotlin.srcDir(layout.buildDirectory.dir("generated/templates/kotlin"))
    }

    commonMain.dependencies {
      api(project(":core:model"))
      // For APP_VERSION_CODE — the floor a template's min_app_version is compared against (#728).
      implementation(project(":core:appinfo"))
      api(libs.koin.core)
      api(libs.kotlinx.coroutines.core)
      // Runtime only — this module provides a CompositionLocal, not UI. Nothing here draws.
      implementation(compose.runtime)
    }
  }
}

dependencies {
  "androidHostTestImplementation"(libs.junit)
  "androidHostTestImplementation"(libs.truth)
}

/**
 * Embeds each committed canonical template `.pb` into a Kotlin constant (#675).
 *
 * **No protoc here.** The `.pb` is compiled out of band by `templates/compile-template.sh` and
 * committed; this only base64s bytes that already exist, so a fresh clone and CI need no native
 * toolchain. See the script's header for why the compile is not a Gradle task.
 *
 * **Generated source rather than a bundled resource** because [dev.fanfly.wingslog.core.template.TemplateRegistry]
 * resolves synchronously, on every platform. Compose resources are read through a suspending API,
 * which would make template lookup async all the way up into `CurrentThingTemplate`'s constructor —
 * a large change to how the app starts, to load bytes that are known at build time.
 */
val generateTemplateAssets by tasks.registering {
  val templateDir = layout.projectDirectory.dir("templates")
  val outputDir = layout.buildDirectory.dir(
    "generated/templates/kotlin/dev/fanfly/wingslog/core/template/canonical"
  )
  inputs.files(project.fileTree("templates") { include("**/*.pb") })
    .withPropertyName("compiledTemplates")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  outputs.dir(outputDir)
  doLast {
    val assets = templateDir.asFile.listFiles()
      .orEmpty()
      .filter { it.extension == "pb" }
      .sortedBy { it.name }
    check(assets.isNotEmpty()) { "no compiled .pb templates in ${templateDir.asFile}" }

    val constants = assets.joinToString("\n\n") { file ->
      // "airplane.v1.pb" -> AIRPLANE_V1_BYTES
      val name = file.name.removeSuffix(".pb").replace('.', '_').uppercase()
      val base64 = Base64.getEncoder().encodeToString(file.readBytes())
      "/** `${file.name}`, ${file.length()} bytes. */\n" +
        "internal const val ${name}_BASE64: String =\n  \"$base64\""
    }

    outputDir.get().asFile.also { it.mkdirs() }
      .resolve("GeneratedTemplateAssets.kt")
      .writeText(
        "package dev.fanfly.wingslog.core.template.canonical\n\n" +
          "// Generated from core/template/templates/*.pb. Do not edit.\n\n" +
          constants + "\n",
      )
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>()
  .configureEach { dependsOn(generateTemplateAssets) }

/**
 * Tells Gradle that `StringSnapshotTest` depends on every `strings.xml` **and every `.kt` file** in
 * the repo.
 *
 * The test reads them from the filesystem rather than through generated resource accessors (see
 * its KDoc for why). Gradle cannot see that, so without this the task is UP-TO-DATE whenever
 * `core/template`'s own sources are unchanged — which is precisely the commit that edits a string
 * in `feature/login`. The guard would be skipped on exactly the change it exists to catch, and a
 * skipped task reports success. Verified by mutating a string and watching the test not run.
 */
tasks.withType<Test>().configureEach {
  inputs.files(
    rootProject.fileTree(rootProject.projectDir) {
      include("**/src/**/values/strings.xml")
      exclude("**/build/**")
    },
  ).withPropertyName("repoStringResources").withPathSensitivity(PathSensitivity.RELATIVE)

  // The call-site guards read Kotlin, not just resources. Without this they are UP-TO-DATE whenever
  // core/template is unchanged — which is every commit that only touches a call site, exactly the
  // commits they exist to police. Found by mutating ProUpsellSheet and watching the suite pass in
  // two seconds without running.
  inputs.files(
    rootProject.fileTree(rootProject.projectDir) {
      include("**/src/**/*.kt")
      exclude("**/build/**")
    },
  ).withPropertyName("repoKotlinSources").withPathSensitivity(PathSensitivity.RELATIVE)

  // AirplaneTemplateAssetTest reads templates/*.pb off the filesystem, which Gradle cannot see for
  // the same reason as above — and the commit that recompiles a template touches nothing else, so
  // without this the one check on the new bytes is the thing that gets skipped.
  inputs.files(project.fileTree("templates") { include("**/*.pb") })
    .withPropertyName("canonicalTemplateAssets")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}
