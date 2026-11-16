plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.flowy.keyboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.flowy.keyboard"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
