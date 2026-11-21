plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
}
kotlin { jvmToolchain(21) }
// Supplied by the release workflow so this fixture always tests the version being released.
val qmSdkVersion: String = providers.gradleProperty("qmSdkVersion").get()
dependencies {
    implementation("io.github.sobuumedia:quietmetrix-sdk:$qmSdkVersion")
    implementation("io.github.sobuumedia:quietmetrix-sdk-compose:$qmSdkVersion")
    implementation("io.github.sobuumedia:quietmetrix-sdk-debug:$qmSdkVersion")
    implementation("org.jetbrains.compose.runtime:runtime:1.12.0")
}
