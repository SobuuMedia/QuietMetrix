buildscript {
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") }
}
plugins { id("com.android.application") version "9.4.1" }
android {
    namespace = "com.quietmetrix.releaseproof"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.quietmetrix.releaseproof"
        minSdk = 24; targetSdk = 37
        versionCode = 1; versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation("io.github.sobuumedia:quietmetrix-sdk:0.7.0") }
