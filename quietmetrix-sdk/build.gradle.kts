@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.Sign
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("maven-publish")
    id("signing")
}

group = "com.quietmetrix"
version = "0.2.0"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.quietmetrix.analytics"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest { isIncludeAndroidResources = true }
    }

    jvm()

    wasmJs {
        browser()
        compilerOptions {
            optIn.add("kotlin.js.ExperimentalWasmJsInterop")
        }
    }

    val xcf = XCFramework("QuietMetrix")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "QuietMetrix"
            isStatic = true
            xcf.add(this)
        }
    }

    macosArm64()
    linuxX64()
    mingwX64()

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
        jvmMain.dependencies {
            implementation(libs.ktor.client.java)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
        }
        val macosArm64Main by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val linuxX64Main by getting {
            dependencies {
                implementation(libs.ktor.client.curl)
            }
        }
        val mingwX64Main by getting {
            dependencies {
                implementation(libs.ktor.client.winhttp)
            }
        }
    }
}

// Maven Central requires a javadoc jar for every published module. Kotlin
// Multiplatform has no real javadoc, so attach an empty one to each publication.
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(javadocJar)
        pom {
            name.set("QuietMetrix SDK")
            description.set("Lightweight privacy-respecting analytics SDK for Kotlin Multiplatform")
            url.set("https://github.com/SobuuMedia/QuietMetrix")
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
                url.set("https://github.com/SobuuMedia/QuietMetrix")
                connection.set("scm:git:git://github.com/SobuuMedia/QuietMetrix.git")
                developerConnection.set("scm:git:ssh://github.com/SobuuMedia/QuietMetrix.git")
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

        // Maven Central via the Sonatype Central Portal (OSSRH-compatible staging
        // API; the legacy s01.oss.sonatype.org host was sunset in 2025). Registered
        // unconditionally so `publishAllPublicationsToSonatypeRepository` always
        // exists. Credentials come from -PossrhUsername/-PossrhPassword or the
        // OSSRH_USERNAME/OSSRH_PASSWORD env vars; override the URLs with
        // -PsonatypeReleaseUrl / -PsonatypeSnapshotUrl if your account differs.
        maven {
            name = "sonatype"
            val isSnapshot = version.toString().endsWith("SNAPSHOT")
            val releaseUrl = (findProperty("sonatypeReleaseUrl") as String?)
                ?: "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
            val snapshotUrl = (findProperty("sonatypeSnapshotUrl") as String?)
                ?: "https://central.sonatype.com/repository/maven-snapshots/"
            url = uri(if (isSnapshot) snapshotUrl else releaseUrl)
            credentials {
                username = (findProperty("ossrhUsername") as String?) ?: System.getenv("OSSRH_USERNAME")
                password = (findProperty("ossrhPassword") as String?)
                    ?: (findProperty("ossrhToken") as String?)
                    ?: System.getenv("OSSRH_PASSWORD")
                    ?: System.getenv("OSSRH_TOKEN")
            }
        }
    }
}

// Kotlin Multiplatform generates one Sign task per publication, but a given
// publish task reads the signature files of *sibling* publications without
// Gradle wiring the dependency. On Gradle 8+/9 this fails the build with a
// task-validation error, so make every publish task run after every Sign task.
tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
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
