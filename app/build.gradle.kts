plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pandyabhavik.blackicons"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pandyabhavik.blackicons"
        minSdk = 31
        targetSdk = 35
        // CI passes the run number, so every build installs over the last one.
        versionCode = (System.getenv("BLACKICONS_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0." + versionCode
    }

    buildTypes {
        release {
            // Launchers look icons up by name, so nothing may be shrunk or renamed.
            isMinifyEnabled = false
            isShrinkResources = false
            // Signed with the build machine's debug key; the copy that gets installed is re-signed
            // with BlackIcons' own key (see the README in the project files).
            signingConfig = signingConfigs.getByName("debug")
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
    androidResources {
        // The icons are already WebP; re-crunching 14,000 files only slows the build.
        noCompress += listOf("webp")
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
