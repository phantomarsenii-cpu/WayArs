// AGP 9.0+ has built-in Kotlin support (see android.builtInKotlin / the
// migration notes in gradle.properties and app/build.gradle.kts), so the
// classic org.jetbrains.kotlin.android plugin is no longer applied here —
// AGP now brings in Kotlin Gradle Plugin 2.2.10 on its own. The Compose
// compiler moved into the Kotlin repo as of Kotlin 2.0 and needs its own
// plugin (it replaces the old composeOptions.kotlinCompilerExtensionVersion
// setting); its version must match the Kotlin compiler AGP is using, hence
// "2.2.10" here. KSP's version is bumped alongside it — AGP 9.0.1 requires
// KSP 2.2.10-2.0.2 or newer to work with built-in Kotlin's KGP 2.2.10.
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}
