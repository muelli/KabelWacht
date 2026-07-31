// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Tobias Mueller and KabelWacht contributors
//
// Thin wrapper that builds WireGuard's Apache-2.0 tunnel library FROM SOURCE, using
// the pinned upstream module under third_party/wireguard-android (a git submodule).
// This replaces the prebuilt com.wireguard.android:tunnel AAR from Maven, so every
// byte we ship is built here: the Java library, libwg-go.so (wireguard-go, MIT),
// and libwg.so / libwg-quick.so (wireguard-tools, GPL-2.0).

plugins {
    alias(libs.plugins.android.library)
}

// Upstream tunnel module source root.
val wg = "$rootDir/third_party/wireguard-android/tunnel"

// Package baked into the native binaries (wireguard-go socket dir, wg RUNSTATEDIR).
// MUST equal the app's applicationId or the tunnel can't reach its own processes.
val appPackage = "com.github.muelli.kabelwacht"

android {
    namespace = "com.wireguard.android.tunnel"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 29
        // Optionally restrict ABIs for faster local builds: -PtunnelAbis=arm64-v8a
        providers.gradleProperty("tunnelAbis").orNull?.let { abis ->
            ndk { abiFilters += abis.split(",").map(String::trim) }
        }
        externalNativeBuild {
            cmake {
                targets("libwg-go.so", "libwg.so", "libwg-quick.so")
                arguments(
                    "-DGRADLE_USER_HOME=${gradle.gradleUserHomeDir}",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DANDROID_PACKAGE_NAME=$appPackage",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("$wg/tools/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        disable += setOf("LongLogTag", "NewApi")
    }
}

// Reuse the upstream sources and manifest (which declares GoBackend$VpnService).
// Configured through the new-DSL extension type: the generated `android {}`
// accessor still types sourceSets with AGP's legacy interface, which AGP 9's
// source-set objects no longer implement (ClassCastException).
configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.named("main") {
        manifest.srcFile("$wg/src/main/AndroidManifest.xml")
        java.setSrcDirs(listOf("$wg/src/main/java"))
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.collection)
    compileOnly(libs.jsr305)
}
