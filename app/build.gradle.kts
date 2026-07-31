import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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

    kotlinOptions {
        jvmTarget = "17"
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

    // WireGuard userspace backend (GPL-2.0) + wg-quick config parser.
    implementation(libs.wireguard.tunnel)

    // QR scanning (Apache-2.0, no Google Play Services / ML Kit).
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
}
