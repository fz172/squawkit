plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

compose.resources {
    publicResClass = true
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
            implementation(project(":core:nav"))
            implementation(project(":core:ui"))
            implementation(project(":core:ui:adaptive"))
            implementation(project(":core:ui:theme"))
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
            implementation(project(":feature:logs:update"))
            implementation(project(":feature:tasks:datamanager"))
            implementation(project(":feature:tasks:update"))
            implementation(project(":feature:squawk:datamanager"))
            implementation(project(":feature:squawk:update"))
            implementation(project(":feature:export:datamanager"))
            implementation(project(":feature:export:update"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:sync:settings"))
            implementation(project(":feature:technician:manage"))
            implementation(project(":feature:stresstest:config"))
            implementation(libs.compose.foundation)
            implementation(libs.androidx.navigation.compose)

            // Firebase init (FirebaseOptions / Firebase.initialize) + DI.
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.compose.runtime)
            implementation(libs.components.resources)
        }
        jsMain.dependencies {
            // M7 durable storage: official SQLite WASM build, driven by the OPFS SAH-Pool worker
            // (src/jsMain/resources/sqlite-wasm-opfs.worker.js) behind SQLDelight's WebWorkerDriver.
            implementation(npm("@sqlite.org/sqlite-wasm", "3.53.0-build1"))
            // Copies sqlite3.wasm into the bundle root (see webpack.config.d/sqlite-wasm-copy.js).
            implementation(devNpm("copy-webpack-plugin", "9.1.0"))
            implementation(devNpm("os-browserify", "0.3.0"))
        }
    }
}
