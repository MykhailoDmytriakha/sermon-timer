plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.sermontimer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.sermontimer"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Shrink and optimize release to reduce size/startup cost
            isMinifyEnabled = true
            isShrinkResources = true
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

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.datastore.preferences)
    implementation(libs.guava.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.wear.tooling.preview)
    implementation(libs.wear.tiles)
    implementation(libs.wear.tiles.proto)
    implementation(libs.protolayout)
    implementation(libs.protolayout.material)
    implementation(libs.protolayout.expression)
    implementation(libs.wear.core)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.wear.ongoing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.truth)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
