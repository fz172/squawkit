plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.wire)
}

android {
    namespace = "dev.fanfly.wingslog.core.model"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

wire {
    sourcePath {
        srcDir("src/main/proto")
    }
    kotlin {}
}

kotlin {
    jvmToolchain(21)
    
    androidTarget {
        compilerOptions {
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.wire.runtime)
        }
    }
}
