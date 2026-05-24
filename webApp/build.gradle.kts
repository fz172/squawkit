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
            implementation(libs.compose.ui)
            implementation(libs.material3)
            implementation(libs.compose.foundation)
            implementation(libs.material.icons.extended)
            implementation(libs.components.resources)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.fanfly.wingslog.web.generated.resources"
}
