plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.gllry.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.gllry.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 3
        versionName = "1.4"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
    }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.coil-kt:coil-video:2.6.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
}
