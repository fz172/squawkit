plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.fanfly.wingslog.feature.aircraft.database"
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
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))

    // Firebase platform to resolve transitive dependency versions
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)

    // DI
    implementation(libs.koin.android)

    // Logging
    implementation(libs.flogger)
    implementation(libs.flogger.system.backend)

    testImplementation(project(":core:model"))
    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}
