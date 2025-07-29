import org.gradle.kotlin.dsl.androidTestImplementation
import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.quickchat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.quickchat"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.runtime.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

        // Core
        implementation("androidx.core:core-ktx:1.13.1")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

        // Compose (BOM + UI)
        implementation(platform("androidx.compose:compose-bom:2024.05.00"))
        implementation("androidx.activity:activity-compose:1.9.0")
        implementation("androidx.compose.ui:ui")
        implementation("androidx.compose.ui:ui-tooling-preview")
        implementation("androidx.compose.ui:ui-graphics")
        implementation("androidx.compose.foundation:foundation")
        implementation("androidx.compose.material3:material3")
        implementation("androidx.compose.runtime:runtime")
        implementation("androidx.compose.runtime:runtime-livedata")

        // Lifecycle
        implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
        implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")

        // Navigation (optional if you’re using Navigation Compose)
    implementation("androidx.navigation:navigation-compose:2.9.2") // or latest stable version
    implementation("androidx.navigation:navigation-compose:2.9.2")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui:1.8.3")
// or latest
        // Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

        // Koin
        implementation("io.insert-koin:koin-android:4.1.0")
        implementation("io.insert-koin:koin-androidx-compose:4.1.0")

        // Testing
        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.1.5")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
        androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
        androidTestImplementation("androidx.compose.ui:ui-test-junit4")
        debugImplementation("androidx.compose.ui:ui-tooling")
        debugImplementation("androidx.compose.ui:ui-test-manifest")




}