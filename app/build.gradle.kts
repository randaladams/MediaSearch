plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.mediasearch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mediasearch"
        minSdk = 26
        targetSdk = 35
        // Bump both for each release; versionName is shown at the bottom of Settings.
        versionCode = 3
        versionName = "1.2"
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
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true // exposes BuildConfig.VERSION_NAME for the Settings footer
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // JSch and Bouncy Castle both ship these OSGi manifests; Android doesn't use them.
            excludes += "META-INF/versions/*/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Offline database
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Locally saved settings
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // SSH (maintained JSch fork: supports ed25519, rsa-sha2, OpenSSH-format keys)
    implementation("com.github.mwiede:jsch:0.2.21")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
}
