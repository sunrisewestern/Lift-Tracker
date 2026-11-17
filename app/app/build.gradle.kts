plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lifttracker.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lifttracker.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Optional real files for ImportExportTest: ./gradlew test -PsampleCsv=… -PsampleRoutines=…
        unitTests.all { test ->
            listOf("sampleCsv", "sampleRoutines").forEach { key ->
                (project.findProperty(key) as String?)?.let { test.systemProperty(key, it) }
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.ui:ui-graphics:1.7.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
    implementation("androidx.compose.foundation:foundation:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-core:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.wear:wear-remote-interactions:1.1.0")
    implementation("com.google.guava:guava:33.3.1-android")
    // AI providers: official Anthropic SDK + OkHttp for OpenAI-compatible and Gemini endpoints
    implementation("com.anthropic:anthropic-java:3.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    // Scheduled automatic backups, and writing them to a folder the user picked
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // Espresso before 3.6 calls InputManager.getInstance(), which newer Android versions removed.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.0")
}
