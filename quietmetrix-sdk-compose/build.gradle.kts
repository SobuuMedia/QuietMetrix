plugins {
    id("maven-publish")
    id("signing")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "io.github.sobuumedia"
version = "0.7.0"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.quietmetrix.analytics.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    jvm()
    wasmJs { browser() }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "QuietMetrixCompose"
            isStatic = true
            binaryOption("bundleShortVersionString", project.version.toString())
            binaryOption("bundleVersion", project.version.toString())
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":quietmetrix-sdk"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
    }
}

val javadocJar by tasks.registering(Jar::class) { archiveClassifier.set("javadoc") }
afterEvaluate {
    publishing {
        publications.withType<MavenPublication>().configureEach {
            artifact(javadocJar)
            pom {
                name.set("QuietMetrix Compose SDK")
                description.set("Consent-aware Compose experiment elements for QuietMetrix")
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
            maven {
                name = "releaseStaging"
                url = rootProject.layout.buildDirectory.dir("sdk-release/maven").get().asFile.toURI()
            }

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
    // those sign*Publication tasks as dependencies of publication tasks. Depend on the owning
    // signature and order sibling signatures sharing output directories, without forcing
    // unsupported host publications into a host-specific release.
    tasks.withType<AbstractPublishToMaven>().configureEach {
        val publicationName = name.removePrefix("publish").substringBefore("PublicationTo")
        dependsOn(tasks.withType<Sign>().matching { it.name == "sign${publicationName}Publication" })
        mustRunAfter(tasks.withType<Sign>())
    }
}
