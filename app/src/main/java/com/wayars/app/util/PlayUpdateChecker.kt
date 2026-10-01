package com.wayars.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Asks Google Play whether a newer version of this app is published.
 * Only meaningful for installs that came from Google Play (a sideloaded APK has no store
 * listing to compare with), so every other install simply reports "no update".
 */
object PlayUpdateChecker {

    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun isInstalledFromPlay(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        installer == PLAY_STORE_PACKAGE
    }.getOrDefault(false)

    suspend fun isUpdateAvailable(context: Context): Boolean {
        if (!isInstalledFromPlay(context)) return false
        return suspendCancellableCoroutine { cont ->
            try {
                AppUpdateManagerFactory.create(context.applicationContext)
                    .appUpdateInfo
                    .addOnSuccessListener { info ->
                        if (cont.isActive) {
                            cont.resume(info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                        }
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(false)
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    /** Opens this app's page in Google Play (falls back to the web page if Play is missing). */
    fun openStorePage(context: Context) {
        val pkg = context.packageName
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .setPackage(PLAY_STORE_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
