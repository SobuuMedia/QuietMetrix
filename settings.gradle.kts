pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "QuietMetrix"
include(":quietmetrix-core")
include(":dashboard")
include(":servers:ktor")
include(":samples:android")
include(":samples:desktop-jvm")