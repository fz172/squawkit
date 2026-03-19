plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

android {
    namespace = "dev.fanfly.wingslog.feature.userprofile.database"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)

    androidTarget {
        compilerOptions {
        }
    }

    sourceSets {
        commonMain {}
        androidMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:database"))
            implementation(project(":core:auth"))

            implementation(libs.firebase.firestore)
            implementation(libs.firebase.auth)

            // DI
            implementation(libs.koin.android)

            // Logging
            implementation(libs.flogger)
            implementation(libs.flogger.system.backend)
        }
    }
}

dependencies {
    // Firebase platform to resolve transitive dependency versions
    implementation(platform(libs.firebase.bom))
}
