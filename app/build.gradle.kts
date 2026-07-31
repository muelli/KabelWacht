import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Kotlin support is built into AGP 9; no standalone kotlin-android plugin.
    alias(libs.plugins.compose.compiler)
}

// Single source of truth for the version: one incrementing integer in
// version.properties drives both versionCode and versionName.
val appVersionCode = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}.getProperty("versionCode").trim().toInt()

android {
    namespace = "com.github.muelli.kabelwacht"
    compileSdk = 35
    // Needed so the app's packaging step can strip the native libraries built by
    // the :tunnel module (otherwise libwg-go.so ships unstripped, ~8 MB/ABI).
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.github.muelli.kabelwacht"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionCode.toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Reproducible builds: F-Droid rebuilds and signs from source, so we ship no
    // committed signing config. Debug uses the standard debug keystore.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Kotlin jvmTarget defaults to android.compileOptions.targetCompatibility (17).

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // WireGuard tunnel library, built FROM SOURCE by the :tunnel module
    // (Apache-2.0 Java + libwg-go.so/libwg.so/libwg-quick.so). No prebuilt AAR.
    implementation(project(":tunnel"))

    // QR scanning (Apache-2.0, no Google Play Services / ML Kit).
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
}
