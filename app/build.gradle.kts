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
        versionCode = 3
        versionName = "0.3.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    ndkVersion = "27.2.12479018"
}
