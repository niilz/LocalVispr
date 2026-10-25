plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.localvispr"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.localvispr"
        // API 33 is the floor: text injection relies on AccessibilityService.getInputMethod().
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Phones and the Apple-silicon emulator are arm64; x86_64 covers Intel emulators.
        // Dropping 32-bit ABIs keeps the native ONNX/LiteRT payload small.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // TODO: enable R8 once keep rules for the JNI-backed sherpa-onnx and LiteRT-LM
            // classes are verified.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // The native libraries of sherpa-onnx and LiteRT-LM must be extracted to disk
    // so that their dlopen()-based loaders find each other.
    packaging { jniLibs { useLegacyPackaging = true } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.sherpa.onnx)
    implementation(libs.litertlm.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
