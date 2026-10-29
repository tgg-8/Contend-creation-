plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.claudering"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.claudering"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    // Fester Test-Schlüssel (Standard-Passwort "android", kein Geheimnis).
    // Damit lassen sich neue Versionen einfach über die alte installieren.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
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
