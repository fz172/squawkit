plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.fanfly.wingslog.feature.userprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
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
            implementation(project(":core:ui"))
            implementation(project(":core:model"))
            implementation(project(":core:database"))
            implementation(project(":core:auth"))
            implementation(project(":feature:userprofile:userprofilecard"))
            implementation(project(":feature:userprofile:database"))
            
            // Compose
            implementation(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.material3)
            
            // Navigation
            implementation(libs.androidx.navigation.compose)

            // Lifecycle & DI
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.koin.androidx.compose)
            
            // Logging
            implementation(libs.kermit)
        }
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.androidx.compose.bom))
}
