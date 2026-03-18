plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.wire)
}

wire {
    sourcePath {
        srcDir("src/main/proto")
    }
    kotlin {}
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(libs.wire.runtime)
}
