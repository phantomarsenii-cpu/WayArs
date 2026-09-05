plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Version auto-increments on every GitHub Actions build using the built-in
// GITHUB_RUN_NUMBER env var (1, 2, 3, ... forever, never resets). Locally
// (Android Studio, no CI env var) it falls back to patch "0" -> 0.1.0.
// To bump the major/minor line later, just change VERSION_MAJOR_MINOR below.
val versionMajorMinor = "0.1"
val ciPatch = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
val appVersionName = "$versionMajorMinor.$ciPatch"
val appVersionCode = ciPatch + 1 // versionCode must be >= 1 and strictly increasing

android {
    namespace = "com.wayars.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wayars.app"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // A fresh GitHub Actions runner has no ~/.android/debug.keystore, so
    // Gradle silently generates a NEW random one on every single CI build.
    // Each APK ends up signed with a different key, and Android refuses to
    // install an "update" signed by a different key than the one already on
    // the phone — hence needing to uninstall the old version every time.
    // Using this committed, fixed keystore for the debug build fixes that:
    // every CI build (and every local build) signs with the SAME key, so
    // installing a new APK over the old one works like a normal update.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Rename the output file itself (not just the artifact zip) to WayArs.apk
    // instead of the default app-debug.apk / app-release.apk.
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "WayArs.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
