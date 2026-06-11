import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing is resolved from a gitignored keystore.properties (see keystore.properties.example)
// or from environment variables — never from anything committed (r-03). When neither is present the
// release build still assembles, just unsigned, which is fine for F-Droid (it signs independently).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
fun resolveSigning(propKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: System.getenv(envKey)
val releaseStoreFile: String? = resolveSigning("storeFile", "EQUERRY_KEYSTORE_FILE")

android {
    namespace = "dev.equerry.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.equerry.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Populated only when a keystore is supplied out-of-repo; left empty otherwise so the
            // build configures cleanly with no secrets present.
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = resolveSigning("storePassword", "EQUERRY_KEYSTORE_PASSWORD")
                keyAlias = resolveSigning("keyAlias", "EQUERRY_KEY_ALIAS")
                keyPassword = resolveSigning("keyPassword", "EQUERRY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Explicit: no debug leftovers in a shippable build (c-6). Guarded by ReleaseBuildConfigTest.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign only when a keystore was provided out-of-repo; unsigned otherwise (F-Droid signs).
            signingConfig = if (releaseStoreFile != null) signingConfigs.getByName("release") else null
        }
    }

    // Compile to JDK 17 bytecode while running the build on JDK 21 — no Java toolchain
    // auto-provisioning, so the locally installed JDK is used as-is.
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
        unitTests {
            // Robolectric + Compose UI tests need the merged manifest/resources on the
            // JVM unit-test classpath.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Networking (provider drivers)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Storage / secrets
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // OCR (FOSS on-device Tesseract — screen-context fallback engine, via JitPack)
    implementation(libs.tesseract4android)

    // Audio playback (remote TTS clip playback — phase 08)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)

    // Testing (JVM / Robolectric — run under testDebugUnitTest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.navigation.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Instrumentation (connected) tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
