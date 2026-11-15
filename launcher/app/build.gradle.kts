plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.mono.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.mono.launcher"
        minSdk = 33          // Android 13+, das Pixel 9 Pro läuft mit Android 15/16
        targetSdk = 36
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Bewusst schlank: nur Compose-Grundbausteine, kein Material, keine Netzwerk-/Bild-Bibliotheken.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")

    // Nur für die Logik-Tests auf dem PC (landet nicht in der App).
    testImplementation("junit:junit:4.13.2")
}
