plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
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

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain {}
        androidMain.dependencies {
            api(project(":core:model"))
            api(project(":core:network"))
            api(project(":core:ui"))
            
            // Firebase Data
            api(libs.firebase.firestore)

            // DI
            implementation(libs.koin.android)

            // Logging
            implementation(libs.flogger)
            implementation(libs.flogger.system.backend)
            
            // Coroutines
            implementation(libs.androidx.core.ktx)
        }
    }
}

dependencies {
    // Firebase BOM inside standard dependencies block
    implementation(platform(libs.firebase.bom))
}
