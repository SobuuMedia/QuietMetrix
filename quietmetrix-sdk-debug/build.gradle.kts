@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "io.github.sobuumedia"
version = "0.1.0"

// A separate, optional artifact from quietmetrix-sdk on purpose: the core SDK has zero UI
// dependencies, and pulling in Compose Multiplatform for every consumer — even ones who never
// use this overlay — would be a real cost for a library whose whole pitch is being small and
// unintrusive. Only Android, iOS, JVM (desktop), and Web (wasmJs) are targeted: Compose
// Multiplatform has no rendering backend for bare Kotlin/Native linuxX64/macosArm64/mingwX64
// (those are native targets with no UI toolkit, distinct from the jvm() "desktop" target
// Compose Desktop actually runs on) — this is a hard platform constraint, not a scoping choice.
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.quietmetrix.analytics.debug"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    jvm()

    wasmJs {
        browser()
    }

    // iosX64 (the Intel simulator) is deliberately excluded: Compose Multiplatform 1.11.1
    // publishes no artifacts for it at all (JetBrains dropped it as Apple Silicon became
    // standard). quietmetrix-sdk itself still targets iosX64; this module just can't.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "QuietMetrixDebug"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":quietmetrix-sdk"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
