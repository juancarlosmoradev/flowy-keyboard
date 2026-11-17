plugins {
    id("com.android.application")
}

val signingStorePath = System.getenv("FLOWY_KEYSTORE_PATH")
val signingStorePassword = System.getenv("FLOWY_KEYSTORE_PASSWORD")
val signingKeyAlias = System.getenv("FLOWY_KEY_ALIAS")
val signingKeyPassword = System.getenv("FLOWY_KEY_PASSWORD")
val persistentSigningReady = listOf(
    signingStorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.flowy.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.flowy.keyboard"
        minSdk = 23
        targetSdk = 35
        versionCode = 12
        versionName = "0.6.2"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release")
            }
        }
    }

    if (persistentSigningReady) {
        signingConfigs {
            create("persistent") {
                storeFile = file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (persistentSigningReady) {
                signingConfig = signingConfigs.getByName("persistent")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (persistentSigningReady) {
                signingConfig = signingConfigs.getByName("persistent")
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
