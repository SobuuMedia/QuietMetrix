plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "io.github.sobuumedia"
version = "0.2.0"

kotlin {
    js(IR) {
        // A Node executable, not a browser library — an agent/CLI runs this via
        // `node build/dist/js/productionExecutable/quietmetrix-cli.js`, wrapped by the
        // npm package's bin entry. See docs/agents/setup.md.
        nodejs()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

// --- npm packaging & publishing -------------------------------------------------------------
// Kotlin/JS `binaries.executable()` without a bundler emits one CommonJS file per module
// (this module + kotlin-stdlib + every ktor/kotlinx dependency) under
// build/compileSync/js/main/productionExecutable/kotlin/ — the entry file `require()`s its
// siblings by relative path, so the whole directory has to ship together. There is no
// single-file bundle step here (unlike the SDK's browser `binaries.library()`, which uses
// Webpack); this path was found by inspecting compileProductionExecutableKotlinJs's output; it
// may move if the Kotlin/JS Gradle plugin changes its layout.
val npmPackageName = "@sobuumedia/quietmetrix-cli"
val npmDistDir = layout.buildDirectory.dir("npm/quietmetrix-cli")
val entryFileName = "${rootProject.name}-${project.name}.js"

val prepareNpmPackage by tasks.registering(Copy::class) {
    group = "publishing"
    description = "Assembles the npm package: the JS executable + its sibling modules + package.json."
    dependsOn("compileProductionExecutableKotlinJs")
    from(layout.buildDirectory.dir("compileSync/js/main/productionExecutable/kotlin"))
    into(npmDistDir)
    doLast {
        val distDir = npmDistDir.get().asFile
        val entry = distDir.resolve(entryFileName)
        check(entry.exists()) { "Expected entry file not found: ${entry.path}" }
        if (!entry.readText().startsWith("#!")) {
            entry.writeText("#!/usr/bin/env node\n" + entry.readText())
        }
        entry.setExecutable(true)

        distDir.resolve("package.json").writeText(
            """
            {
              "name": "$npmPackageName",
              "version": "${project.version}",
              "description": "CLI for provisioning QuietMetrix projects and access tokens from agents/scripts.",
              "bin": { "quietmetrix": "./$entryFileName" },
              "license": "MIT",
              "repository": { "type": "git", "url": "https://github.com/SobuuMedia/QuietMetrix.git" }
            }
            """.trimIndent()
        )
        logger.lifecycle("npm package '$npmPackageName' prepared at ${distDir.path}")
    }
}

tasks.register<Exec>("publishNpm") {
    group = "publishing"
    description = "Publishes the CLI to npm as $npmPackageName."
    dependsOn(prepareNpmPackage)
    workingDir(npmDistDir)
    commandLine("npm", "publish", "--access", "public")
}
