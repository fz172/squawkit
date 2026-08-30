import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  id("com.google.gms.google-services")
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
  if (versionPropsFile.exists()) versionPropsFile.inputStream()
    .use { load(it) }
}

val major = versionProps.getProperty(
  "major",
  "1"
)
  .toInt()
val minor = versionProps.getProperty(
  "minor",
  "0"
)
  .toInt()
val today: String = LocalDate.now()
  .format(DateTimeFormatter.ofPattern("yyMMdd"))
val storedDate: String = versionProps.getProperty(
  "buildDate",
  ""
)
val isReleaseBuild = gradle.startParameter.taskNames.any {
  it.contains("Release", ignoreCase = true)
}

val currentVersionCode = versionProps.getProperty("versionCode", "0")
  .toInt()

// Only a release build advances the counter; every other build reports what is already stamped.
val nextVersionCode = if (isReleaseBuild) currentVersionCode + 1 else currentVersionCode

if (isReleaseBuild) {
  versionProps["buildDate"] = today
  versionProps["versionCode"] = nextVersionCode.toString()
  versionPropsFile.outputStream()
    .use { versionProps.store(it, null) }
}

/**
 * The date the running build's `versionCode` was stamped, not today's date.
 *
 * A release build writes both together, so they agree. A debug build must read `buildDate` back
 * rather than using `today`, or Android shows a date the `versionCode` beside it was never paired
 * with — and drifts from iOS and web, which only ever read the file.
 */
val effectiveDate = if (isReleaseBuild) today else storedDate.ifBlank { today }

// "1.0.260828(1400)" — the same string iOS composes from MARKETING_VERSION and
// CURRENT_PROJECT_VERSION, and web from GENERATED_VERSION_NAME and GENERATED_VERSION_CODE (#672).
val computedVersionName = "$major.$minor.$effectiveDate($nextVersionCode)"

// Set via `-PdeveloperBuild=true` to produce a signed, distributable "dogfood-style" release
// build with developer tooling (Developer Options, stress test) turned on. Debug builds always have it on.
val developerBuild =
  (findProperty("developerBuild") as? String)?.toBoolean() ?: false

kotlin {
  jvmToolchain(21)
}

android {
  namespace = "dev.fanfly.wingslog"
  compileSdk {
    version = release(37)
  }

  defaultConfig {
    applicationId = "dev.fanfly.wingslog"
    minSdk = 33
    targetSdk = 37
    versionCode = nextVersionCode
    versionName = computedVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    debug {
      buildConfigField("boolean", "DEVELOPER_BUILD", "true")
    }
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      buildConfigField("boolean", "DEVELOPER_BUILD", developerBuild.toString())
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

}

// No variant suffix on the version name: all three platforms render the same string, and the
// build type is already knowable in-app from the DEVELOPER_BUILD BuildConfig field, which
// AppCapability reads. Putting it in the version name too made Android the odd one out for
// information the app already had.


dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.firebase.appcheck.playintegrity)
  implementation(libs.compose.ui.backhandler)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)

  implementation(libs.androidx.credentials)
  implementation(libs.googleid)
  implementation(libs.play.services.auth)

  implementation(libs.koin.android)
  implementation(libs.kermit)

  implementation(project(":composeApp"))
  implementation(project(":feature:sync:data"))
  implementation(project(":feature:login"))
  // EmailLinkDeepLinks: MainActivity hands the launch intent's URL to the shared auth channel.
  implementation(project(":core:auth"))
  implementation(project(":feature:sharing:datamanager"))
  // AndroidNotificationPermissionBridge: MainActivity registers the runtime-permission launcher
  // this actual needs, since registerForActivityResult must happen before STARTED.
  implementation(project(":feature:notifications:permission"))
  // NotificationTapRouter: MainActivity hands the launch intent's URL to the shared tap channel,
  // same as EmailLinkDeepLinks above (design §5.3).
  implementation(project(":feature:notifications:viewing"))
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
