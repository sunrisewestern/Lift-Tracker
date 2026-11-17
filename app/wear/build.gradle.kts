plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lifttracker.app.wear"
    compileSdk = 36

    defaultConfig {
        // Must match the phone app's applicationId (and signing key) for the Wearable Data Layer.
        applicationId = "com.lifttracker.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    sourceSets["main"].java.srcDir("../shared/src/main/java")

    // Release signing: the keystore and passwords live in ~/.gradle/gradle.properties
    // (HEALTHANALYZER_STORE_FILE, _STORE_PASSWORD, _KEY_ALIAS, _KEY_PASSWORD), never in this project.
    // Phone and watch apps must share this key for the Wearable Data Layer.
    val storeFilePath = providers.gradleProperty("HEALTHANALYZER_STORE_FILE").orNull
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath.replaceFirst("~", System.getProperty("user.home")))
                storePassword = providers.gradleProperty("HEALTHANALYZER_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("HEALTHANALYZER_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("HEALTHANALYZER_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.foundation:foundation:1.7.0")
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    // Ongoing Activity: the workout chip on the watch face, recents and the One UI 8 Watch Now Bar
    implementation("androidx.wear:wear-ongoing:1.1.0")
    implementation("androidx.health:health-services-client:1.0.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
}
