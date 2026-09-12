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

        // Release signing reads entirely from environment variables so the
        // real keystore/passwords never touch source control. GitHub
        // Actions decodes the keystore from a base64 secret to
        // app/release.keystore before the build runs (see
        // .github/workflows/build.yml) and exports the three secrets below
        // into the job's env — Gradle just reads them here.
        //
        // Guarded with `if (...)` rather than asserting: a local dev machine
        // with no release env vars set should still be able to run
        // `assembleDebug` / open the project in Android Studio without this
        // block blowing up the whole Gradle sync.
        //
        // IMPORTANT: resolved via rootProject.file(), not file(). A bare
        // file() call here resolves relative to THIS module's directory
        // (app/), not the repo root — so a path like "app/release.keystore"
        // silently pointed at the nonexistent app/app/release.keystore,
        // releaseStoreFile.exists() was false, no "release" signingConfig
        // was ever created, and the release build type quietly built an
        // UNSIGNED apk (no Gradle error) that only failed much later at the
        // separate "apksigner verify" CI step. rootProject.file() is always
        // relative to the repo root regardless of which module reads it,
        // matching exactly where the workflow's decode step writes the file.
        val releaseStorePath = System.getenv("KEY_STORE_PATH") ?: "app/release.keystore"
        val releaseStorePassword = System.getenv("KEY_STORE_PASSWORD")
        val releaseKeyAlias = System.getenv("KEY_ALIAS")
        val releaseKeyPassword = System.getenv("KEY_PASSWORD")
        val releaseStoreFile = rootProject.file(releaseStorePath)

        if (releaseStorePassword != null && releaseKeyAlias != null &&
            releaseKeyPassword != null && releaseStoreFile.exists()
        ) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Explicit, rather than leaving it to AGP's defaults: the CI
                // workflow's post-build "apksigner verify" step failed with
                // "DOES NOT VERIFY - Missing META-INF/MANIFEST.MF" because
                // the V1 (JAR) signing scheme wasn't applied to the output.
                // Forcing all three schemes on guarantees apksigner always
                // finds a valid signature to verify, on every SDK level.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        } else if (releaseStorePassword != null || releaseKeyAlias != null || releaseKeyPassword != null) {
            // Passwords/alias are present, meaning a signed release build was
            // clearly intended (a local dev sync with nothing set at all
            // would never reach this branch) — but the keystore file is
            // missing. Failing loudly here, at configuration time, is the
            // whole point: the previous behavior silently produced an
            // UNSIGNED release apk with no Gradle error at all, and the only
            // symptom was an "apksigner verify" failure minutes later in CI,
            // with no indication of the real cause.
            throw GradleException(
                "Release signing env vars are set but the keystore file was not found at " +
                    "${releaseStoreFile.absolutePath} (KEY_STORE_PATH=\"$releaseStorePath\", " +
                    "resolved from repo root). Check that the keystore-decoding step in " +
                    ".github/workflows/build.yml writes to this exact path."
            )
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only attach the release signing config when the environment
            // actually provided one (see signingConfigs above) — otherwise
            // fall back to Android Gradle Plugin's default behavior
            // (unsigned release output), so this still builds locally.
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    // ProcessLifecycleOwner — used to re-check subscription status whenever
    // the app returns to the foreground (see WayArsApplication).
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // RevenueCat — subscription management (Play Billing wrapper +
    // server-verified entitlements). See com.wayars.app.billing.RevenueCatConfig.
    implementation("com.revenuecat.purchases:purchases:10.21.1")
}
