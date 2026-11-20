plugins {
    id("org.jetbrains.compose") version "1.12.0"
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
}
kotlin {
    wasmJs { browser(); binaries.executable() }
    sourceSets.wasmJsMain.dependencies {
        implementation("io.github.sobuumedia:quietmetrix-sdk:0.7.0")
        implementation("io.github.sobuumedia:quietmetrix-sdk-compose:0.7.0")
        implementation("org.jetbrains.compose.runtime:runtime:1.12.0")
    }
}
