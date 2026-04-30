import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("maven-publish")
}

group = "com.quietmetrix"
version = "0.1.0"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    val xcf = XCFramework("QuietMetrix")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "QuietMetrix"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.quietmetrix.analytics"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("QuietMetrix Core")
            description.set("Lightweight privacy-respecting analytics SDK for Kotlin Multiplatform")
            url.set("https://github.com/sobuumedia/quietmetrix")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("sobuumedia")
                    name.set("Sobuu Media")
                }
            }
            scm {
                url.set("https://github.com/sobuumedia/quietmetrix")
                connection.set("scm:git:git://github.com/sobuumedia/quietmetrix.git")
                developerConnection.set("scm:git:ssh://github.com/sobuumedia/quietmetrix.git")
            }
        }
    }
    repositories {
        // Local Maven (~/.m2) — always available, useful for local integration testing.
        // Run: ./gradlew :quietmetrix-core:publishToMavenLocal
        mavenLocal()

        // GitHub Packages — set GITHUB_ACTOR and GITHUB_TOKEN env vars to publish.
        val githubActor = System.getenv("GITHUB_ACTOR")
        val githubToken = System.getenv("GITHUB_TOKEN")
        if (githubActor != null && githubToken != null) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/sobuumedia/quietmetrix")
                credentials {
                    username = githubActor
                    password = githubToken
                }
            }
        }
    }
}
