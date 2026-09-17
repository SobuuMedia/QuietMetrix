plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "io.github.sobuumedia"
version = "0.2.0"

kotlin {
    js(IR) {
        // A stdio MCP server, launched by an agent's MCP client — see docs/agents/setup.md.
        nodejs()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // Reuses QuietMetrixClient so the CLI and the MCP server can never drift on
            // request/response handling — only on how the result is presented.
            implementation(project(":quietmetrix-cli"))
            implementation(libs.kotlinx.serialization.json)
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
// See quietmetrix-cli/build.gradle.kts for why this copies the whole per-module JS output
// directory rather than a single bundle file.
val npmPackageName = "@sobuumedia/quietmetrix-mcp"
val npmDistDir = layout.buildDirectory.dir("npm/quietmetrix-mcp")
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
              "description": "MCP server exposing QuietMetrix project provisioning as tools for AI agents.",
              "bin": { "quietmetrix-mcp": "./$entryFileName" },
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
    description = "Publishes the MCP server to npm as $npmPackageName."
    dependsOn(prepareNpmPackage)
    workingDir(npmDistDir)
    commandLine("npm", "publish", "--access", "public")
}
