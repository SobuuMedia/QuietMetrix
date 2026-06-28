import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("maven-publish")
    id("signing")
}

group = "com.quietmetrix"
version = "0.1.2"

kotlin {
    android {
        namespace = "com.quietmetrix.analytics"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest { isIncludeAndroidResources = true }
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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
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
        mavenLocal()

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

        // Maven Central via Sonatype Central Portal — ready, but only active when
        // OSSRH_USERNAME / OSSRH_TOKEN are present. See PUBLISHING.md.
        val ossrhUsername = System.getenv("OSSRH_USERNAME")
        val ossrhToken = System.getenv("OSSRH_TOKEN")
        if (ossrhUsername != null && ossrhToken != null) {
            maven {
                name = "sonatype"
                val isSnapshot = version.toString().endsWith("SNAPSHOT")
                url = uri(
                    if (isSnapshot)
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    else
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                )
                credentials {
                    username = ossrhUsername
                    password = ossrhToken
                }
            }
        }
    }
}

// GPG signing — required for Maven Central, no-op without env vars.
signing {
    val signingKey = System.getenv("GPG_SIGNING_KEY") ?: findProperty("signingKey") as? String
    val signingPassword =
        System.getenv("GPG_SIGNING_PASSWORD") ?: findProperty("signingPassword") as? String
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
