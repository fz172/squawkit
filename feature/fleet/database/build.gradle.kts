plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

android {
    namespace = "dev.fanfly.wingslog.feature.fleet.database"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:database"))
            implementation(project(":core:auth"))

            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.koin.core)
            implementation(libs.kermit)
        }
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
    implementation(platform(libs.firebase.bom))
}
