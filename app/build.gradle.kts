import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// La clave de Google Maps vive en local.properties (no se sube a git).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapsKey: String = localProps.getProperty("MAPS_API_KEY", "")

android {
    namespace = "com.ivan.tabiplan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ivan.tabiplan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["mapsApiKey"] = mapsKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
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
        buildConfig = true
    }

    packaging {
        // Avisos de licencia repetidos en las librerías de la API de Claude; no hacen falta en el APK.
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST")
        }
    }

    // La APK lleva el nombre de la app y la versión (TabiPlan-0.1.0-debug.apk), no app-debug.apk.
    base.archivesName = "TabiPlan-${defaultConfig.versionName}"
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Mapa dentro de la app (necesita Google Play services y MAPS_API_KEY).
    implementation("com.google.maps.android:maps-compose:6.4.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // Routes API y Places API (New) de Google: peticiones REST.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // IA: investigar lugares, armar el itinerario y el asistente de viaje (Claude).
    implementation("com.anthropic:anthropic-java:2.34.0")
}
