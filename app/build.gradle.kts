import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.canok.kargotycoon"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.canok.kargotycoon"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "0.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    // Upload credentials are supplied only for publication builds, never committed.
    val uploadVariables = listOf("PARCELRISE_UPLOAD_STORE", "PARCELRISE_UPLOAD_STORE_PASSWORD", "PARCELRISE_UPLOAD_ALIAS", "PARCELRISE_UPLOAD_KEY_PASSWORD")
    val uploadValues = uploadVariables.map { System.getenv(it)?.takeIf(String::isNotBlank) }
    require(uploadValues.all { it == null } || uploadValues.all { it != null }) {
        "Set all four PARCELRISE_UPLOAD_* signing variables, or leave all unset."
    }
    val uploadSigning = if (uploadValues.all { it != null }) {
        signingConfigs.create("playUpload") {
            storeFile = file(requireNotNull(uploadValues[0]))
            storePassword = uploadValues[1]
            keyAlias = uploadValues[2]
            keyPassword = uploadValues[3]
        }
    } else null

    buildTypes {
        release {
            signingConfig = uploadSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":game"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}

