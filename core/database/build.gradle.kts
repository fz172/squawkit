plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.fanfly.wingslog.core.database"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network")) // For AuthManager dependency
    api(project(":core:ui")) // For LicenseHelpers
    
    // Firebase Data
    implementation(platform(libs.firebase.bom))
    api(libs.firebase.firestore)

    // DI
    implementation(libs.koin.android)

    // Logging
    implementation(libs.flogger)
    implementation(libs.flogger.system.backend)
    
    // Coroutines
    implementation(libs.androidx.core.ktx)
}
