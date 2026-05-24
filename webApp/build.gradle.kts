plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)

    js(IR) {
        outputModuleName.set("webApp")
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // core:ui api-exports compose.ui, material3, and material-icons-extended.
            implementation(project(":core:ui"))
            implementation(project(":core:auth"))
            implementation(project(":core:storage"))
            implementation(project(":feature:login"))
            implementation(libs.compose.foundation)

            // Firebase init (FirebaseOptions / Firebase.initialize) + DI.
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
    }
}
