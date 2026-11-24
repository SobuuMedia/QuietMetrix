buildscript {
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21") }
}
plugins {
    id("com.android.application") version "9.4.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
// Each invocation resolves published coordinates in an independent app at its SDK floor.
val withCompose = providers.gradleProperty("qmWithCompose").map(String::toBoolean).getOrElse(false)
if (withCompose) apply(plugin = "org.jetbrains.kotlin.plugin.compose")
val catalog = file("../../../gradle/libs.versions.toml").readText()
val baselineKey = if (withCompose) "compose" else "core"
val minimumCompileSdk = Regex("android-$baselineKey-minCompileSdk = \"(\\d+)\"")
    .find(catalog)!!.groupValues[1].toInt()
android {
    namespace = "com.quietmetrix.releaseproof"
    compileSdk = minimumCompileSdk
    defaultConfig {
        applicationId = "com.quietmetrix.releaseproof"
        minSdk = 24; targetSdk = minimumCompileSdk
        versionCode = 1; versionName = "1"
    }
    if (withCompose) sourceSets["main"].kotlin.directories.add("src/compose/kotlin")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
// Supplied by the release workflow so this fixture always tests the version being released.
val qmSdkVersion: String = providers.gradleProperty("qmSdkVersion").get()
dependencies {
    implementation("io.github.sobuumedia:quietmetrix-sdk:$qmSdkVersion")
    if (withCompose) implementation("io.github.sobuumedia:quietmetrix-sdk-compose:$qmSdkVersion")
}
