plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.fanfly.wingslog.core.network"
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
    
    // Auth & Network
    implementation(platform(libs.firebase.bom))
    api(libs.firebase.auth)
    api(libs.play.services.auth)
    api(libs.androidx.credentials)
    api(libs.googleid)

    // DI
    implementation(libs.koin.android)

    // Logging
    implementation(libs.flogger)
    implementation(libs.flogger.system.backend)
    
    // Coroutines
    implementation(libs.androidx.core.ktx)
}
