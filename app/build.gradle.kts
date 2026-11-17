plugins {
    id("com.android.application")
}

android {
    namespace = "dev.flowy.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.flowy.keyboard"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
}
