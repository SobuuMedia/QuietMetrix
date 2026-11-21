plugins {
    id("org.jetbrains.compose") version "1.12.0"
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
}
// Supplied by the release workflow so this fixture always tests the version being released.
val qmSdkVersion: String = providers.gradleProperty("qmSdkVersion").get()
kotlin {
    wasmJs { browser(); binaries.executable() }
    sourceSets.wasmJsMain.dependencies {
        implementation("io.github.sobuumedia:quietmetrix-sdk:$qmSdkVersion")
        implementation("io.github.sobuumedia:quietmetrix-sdk-compose:$qmSdkVersion")
        implementation("org.jetbrains.compose.runtime:runtime:1.12.0")
    }
}
