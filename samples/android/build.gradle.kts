plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.quietmetrix.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.quietmetrix.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":quietmetrix-core"))
    implementation(libs.androidx.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
