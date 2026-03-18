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
