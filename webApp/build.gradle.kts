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
            implementation(project(":feature:sync:data"))
            implementation(project(":feature:technician:datamanager"))
            implementation(project(":feature:attachment:datamanager"))
            implementation(project(":feature:featurelab:datamanager"))
            implementation(project(":feature:fleet:datamanager"))
            implementation(project(":feature:fleet:viewing"))
            implementation(project(":feature:aircraft:dashboard"))
            implementation(project(":feature:logs:datamanager"))
            implementation(project(":feature:logs:viewing"))
            implementation(project(":feature:tasks:datamanager"))
            implementation(project(":feature:squawk:datamanager"))
            implementation(libs.compose.foundation)
            implementation(libs.androidx.navigation.compose)

            // Firebase init (FirebaseOptions / Firebase.initialize) + DI.
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        jsMain.dependencies {
            // Copies sql.js's sql-wasm.wasm into the bundle (see webpack.config.d).
            implementation(devNpm("copy-webpack-plugin", "9.1.0"))
        }
    }
}
