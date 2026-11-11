import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningFile = file(
    providers.environmentVariable("FANGKEMENG_RELEASE_SIGNING_PROPERTIES")
        .getOrElse("${System.getProperty("user.home")}/.pico/signing/fangkemeng/release.properties")
)
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
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

    signingConfigs {
        create("production") {
            storeFile = file(releaseSigning.getProperty("storeFile",
                "${System.getProperty("user.home")}/.pico/signing/fangkemeng/release.p12"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("production")
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
