@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("maven-publish")
    id("signing")
}

group = "io.github.sobuumedia"
version = "0.4.0"

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

    js(IR) {
        browser()
        // nodejs() is enabled so the test suite (jsNodeTest) runs in Node with lightweight
        // browser-global shims, avoiding a headless-browser dependency in CI. The published
        // npm library is the browser distribution.
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
        useEsModules()
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
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.core)
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
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

// --- npm packaging & publishing -------------------------------------------------------------
// Kotlin/JS `binaries.library()` emits a ready-to-publish npm package under
// build/dist/js/productionLibrary (compiled .js + bundled .d.ts). Its package.json `name`
// defaults to the Gradle module name, so rewrite it to the scoped public name, then run
// `npm publish` from that directory. This keeps the release repeatable without pulling in a
// third-party Gradle plugin.
val npmPackageName = "@sobuumedia/quietmetrix-sdk"
val npmDistDir = layout.buildDirectory.dir("dist/js/productionLibrary")

val prepareNpmPackage by tasks.registering {
    group = "publishing"
    description = "Builds the JS library distribution and sets its npm package name to $npmPackageName."
    dependsOn("jsBrowserProductionLibraryDistribution")
    doLast {
        val pkgJson = npmDistDir.get().file("package.json").asFile
        require(pkgJson.exists()) {
            "package.json not found at ${pkgJson.path}; did jsBrowserProductionLibraryDistribution run?"
        }
        val patched = pkgJson.readText()
            .replace(Regex("\"name\"\\s*:\\s*\"[^\"]*\""), "\"name\": \"$npmPackageName\"")
        pkgJson.writeText(patched)
        logger.lifecycle("npm package '$npmPackageName' prepared at ${npmDistDir.get().asFile.path}")
    }
}

// Requires npm auth: either `npm login`, an ~/.npmrc, or a NODE_AUTH_TOKEN-backed .npmrc.
tasks.register<Exec>("publishNpm") {
    group = "publishing"
    description = "Publishes the JS SDK to npm as $npmPackageName."
    dependsOn(prepareNpmPackage)
    workingDir(npmDistDir)
    commandLine("npm", "publish", "--access", "public")
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

afterEvaluate {
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

            val ossrhUsername = System.getenv("OSSRH_USERNAME") ?: findProperty("ossrhUsername") as? String
            val ossrhToken = System.getenv("OSSRH_TOKEN")
                ?: System.getenv("OSSRH_PASSWORD")
                ?: findProperty("ossrhToken") as? String
            if (ossrhUsername != null && ossrhToken != null) {
                maven {
                    name = "sonatype"
                    val isSnapshot = version.toString().endsWith("SNAPSHOT")
                    url = uri(
                        if (isSnapshot)
                            "https://central.sonatype.com/repository/maven-snapshots/"
                        else
                            "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                    )
                    credentials {
                        username = ossrhUsername
                        password = ossrhToken
                    }
                }
            }
        }
    }

    // Optional GPG signing — required for Maven Central, optional for GitHub Packages.
    // Set GPG_SIGNING_KEY and GPG_SIGNING_PASSWORD env vars to enable.
    val signingKey = System.getenv("GPG_SIGNING_KEY") ?: findProperty("signingKey") as? String
    val signingPassword =
        System.getenv("GPG_SIGNING_PASSWORD") ?: findProperty("signingPassword") as? String
    if (signingKey != null && signingPassword != null) {
        signing {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications)
        }
    }

    // KMP creates one signing task per publication, but Gradle does not automatically register
    // those sign*Publication tasks as dependencies of the publish*ToSonatype/MavenLocal tasks that
    // consume the generated .asc signatures. Without this, Gradle fails with an "implicit
    // dependency" validation error. Make every publish task depend on every signing task.
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}
