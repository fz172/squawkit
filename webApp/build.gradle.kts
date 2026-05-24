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
            implementation(libs.compose.foundation)
            // components-resources must be declared directly: the Compose Resources
            // plugin only wires this module's generated Res class onto the compile
            // path when the dependency is present here, not transitively.
            implementation(libs.components.resources)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.fanfly.wingslog.web.generated.resources"
}
