plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace   = "com.asd.firewall"
    compileSdk  = 35
    ndkVersion  = "27.0.12077973"

    defaultConfig {
        applicationId   = "com.asd.firewall"
        minSdk          = 26          // Android 8.0 Oreo — required for startForegroundService
        targetSdk       = 35          // Android 15
        versionCode     = 2
        versionName     = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // NDK ABI filters — arm64 required by Play since Aug 2019; x86_64 for emulator
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // CMake build for the JNI shared library
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -fexceptions -frtti"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-26"
                )
            }
        }
    }

    // Link to our CMakeLists.txt for the native layer
    externalNativeBuild {
        cmake {
            path    = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled       = true
            isShrinkResources     = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // replace with release key
        }
        debug {
            isDebuggable          = true
            isJniDebuggable       = true
        }
    }

    // Rename the output APK to "ASD-Firewall-v1.0.0.apk" for both debug and release
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "ASD-Firewall-v${variant.versionName}-${variant.buildType.name}.apk"
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose (using BOM for version alignment)
    // HorizontalPager / foundation-pager is included in androidx.ui (foundation)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    // Compose Foundation (explicit for HorizontalPager, pointerInput, Canvas, etc.)
    implementation("androidx.compose.foundation:foundation")

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // JSON
    implementation(libs.gson)

    // AppCompat + Material XML theme (required for themes.xml parent resolution)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)

    // Debug tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
