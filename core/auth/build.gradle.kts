plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

android {
    namespace = "dev.fanfly.wingslog.core.auth"
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
        commonMain.dependencies {
            api(libs.gitlive.firebase.auth)
        }
        androidMain.dependencies {
            api(project(":core:model"))
            
            // Auth & Network
            api(libs.firebase.auth)
            api(libs.play.services.auth)
            api(libs.androidx.credentials)
            api(libs.googleid)

            // DI
            implementation(libs.koin.android)

            // Logging
            implementation(libs.kermit)
            
            // Coroutines
            implementation(libs.androidx.core.ktx)
        }
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
}
