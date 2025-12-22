plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.willbeeching.flix"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.willbeeching.flix"
        minSdk = 21
        targetSdk = 34

        // Version - Update these for each release (also update CHANGELOG.md)
        versionCode = 1
        versionName = "0.1.0-alpha"

        // Enable build config fields
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("int", "VERSION_CODE", "${versionCode}")
    }

    signingConfigs {
        // For release builds, configure your signing key:
        // 1. Create a keystore: keytool -genkey -v -keystore flix-release.jks ...
        // 2. Add to local.properties (not committed):
        //    RELEASE_STORE_FILE=/path/to/flix-release.jks
        //    RELEASE_STORE_PASSWORD=your_store_password
        //    RELEASE_KEY_ALIAS=your_key_alias
        //    RELEASE_KEY_PASSWORD=your_key_password
        // 3. Uncomment the signing config below

        // create("release") {
        //     val properties = java.util.Properties()
        //     val localProperties = rootProject.file("local.properties")
        //     if (localProperties.exists()) {
        //         localProperties.inputStream().use { properties.load(it) }
        //     }
        //     storeFile = file(properties.getProperty("RELEASE_STORE_FILE") ?: "release.jks")
        //     storePassword = properties.getProperty("RELEASE_STORE_PASSWORD")
        //     keyAlias = properties.getProperty("RELEASE_KEY_ALIAS")
        //     keyPassword = properties.getProperty("RELEASE_KEY_PASSWORD")
        // }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
            buildConfigField("boolean", "SHOW_FLIX_LOGO", "true")  // Show Flix logo in debug builds
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            buildConfigField("boolean", "SHOW_FLIX_LOGO", "false")  // Hide Flix logo in release
            // signingConfig = signingConfigs.getByName("release")  // Uncomment when signing config is set
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"  // Compatible with Kotlin 1.9.22
    }

    lint {
        // Don't abort build on lint warnings, only on errors
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material components (for XML themes compatibility)
    implementation("com.google.android.material:material:1.11.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON/XML Parsing
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")

    // Image Loading
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Palette for extracting colors from images
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Leanback for Android TV
    implementation("androidx.leanback:leanback:1.0.0")

    // NanoHTTPD for local settings server
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // QR Code generation
    implementation("com.google.zxing:core:3.5.2")
}

