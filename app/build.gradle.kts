plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zenn.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zenn.keyboard"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    // Kunci tetap (dibuat otomatis oleh workflow) supaya APK baru bisa dipasang
    // menimpa yang lama tanpa menghapus data. Hanya untuk pemakaian pribadi.
    signingConfigs {
        create("stable") {
            storeFile = file("zenn-debug.jks")
            storeType = "pkcs12"
            storePassword = "zennkeyboard"
            keyAlias = "zenn"
            keyPassword = "zennkeyboard"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
