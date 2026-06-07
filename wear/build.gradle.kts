plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.gymapp.wear"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Must match the phone app's applicationId — the Wearable Data Layer
        // routes MessageClient/DataClient traffic by matching package + signing
        // cert between the two installed apps; mismatched IDs fail silently
        // with "Failed to deliver message to AppKey" in the system logs.
        applicationId = "com.example.gymapp"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.play.services.wearable)
    implementation(libs.horologist.compose.layout)
    implementation(libs.androidx.health.services.client)
    implementation(libs.guava)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
