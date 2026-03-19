plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.fanfly.wingslog.core.ui"
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
            api(project(":core:model"))
            api(libs.androidx.compose.ui)
            api(libs.androidx.compose.ui.graphics)
            api(libs.androidx.compose.ui.tooling.preview)
            api(libs.androidx.compose.material3)
            api(libs.androidx.compose.material.icons.extended)
            
            // Image Loading
            implementation(libs.coil.compose)
        }
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
