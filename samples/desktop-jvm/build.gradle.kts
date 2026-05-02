plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "com.quietmetrix.sample"
version = "1.0"

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.quietmetrix.sample.MainKt"
}

dependencies {
    implementation(project(":quietmetrix-core"))
    implementation(libs.kotlinx.coroutines.core)
}