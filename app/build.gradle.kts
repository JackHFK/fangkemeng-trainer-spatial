plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.fangkemeng"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fangkemeng"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.2"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            // Release APK 由 PICO Vibe Lab 平台统一签名。
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

configurations.all {
    resolutionStrategy {
        exclude(group = "androidx.compose.ui", module = "ui")
        exclude(group = "androidx.compose.ui", module = "ui-graphics")
        exclude(group = "androidx.compose.ui", module = "ui-text")
        exclude(group = "androidx.compose.foundation", module = "foundation")
    }
}

dependencies {
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.foundation)
    implementation(libs.spatial.tracking)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.design)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
}
