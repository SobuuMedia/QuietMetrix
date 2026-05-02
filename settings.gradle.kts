pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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