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
  if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}

val major = versionProps.getProperty(
  "major",
  "1"
).toInt()
val minor = versionProps.getProperty(
  "minor",
  "0"
).toInt()
val today: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
val storedDate: String = versionProps.getProperty(
  "buildDate",
  ""
)
val patch = if (storedDate == today) versionProps.getProperty(
  "patch",
  "0"
).toInt() + 1 else 1
val nextVersionCode = versionProps.getProperty(
  "versionCode",
  "0"
).toInt() + 1

versionProps["buildDate"] = today
versionProps["patch"] = patch.toString()
versionProps["versionCode"] = nextVersionCode.toString()
versionPropsFile.outputStream().use {
  versionProps.store(
    it,
    null
  )
}

val computedVersionName = "$major.$minor.$today.$patch"

kotlin {
  jvmToolchain(21)
}

android {
  namespace = "dev.fanfly.wingslog"
  compileSdk {
    version = release(36)
  }

  defaultConfig {
    applicationId = "dev.fanfly.wingslog"
    minSdk = 33
    targetSdk = 36
    versionCode = nextVersionCode
    versionName = computedVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures {
    compose = true
  }
}


dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
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
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}