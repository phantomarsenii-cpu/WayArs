package com.wayars.app.util

import android.content.Context
import android.provider.Settings
import com.wayars.app.service.accessibility.OrderAccessibilityService

object AccessibilityUtils {

    /**
     * Checks the OS-level "is our AccessibilityService actually enabled in
     * Settings" status directly, rather than sending the user to Settings
     * unconditionally every time — per spec, this permission should only
     * ever be requested/asked-for once.
     */
    fun isServiceEnabled(context: Context): Boolean {
        val expectedComponent = "${context.packageName}/${OrderAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }
}
