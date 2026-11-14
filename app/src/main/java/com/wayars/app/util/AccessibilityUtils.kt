package com.wayars.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.wayars.app.service.accessibility.OrderAccessibilityService

object AccessibilityUtils {

    /**
     * Checks the OS-level "is our AccessibilityService actually enabled in
     * Settings" status directly, rather than sending the user to Settings
     * unconditionally every time — per spec, this permission should only
     * ever be requested/asked-for once.
     */
    fun isServiceEnabled(context: Context): Boolean {
        // 1) The service is running right now -> it is certainly enabled.
        if (OrderAccessibilityService.isConnected) return true

        val expected = ComponentName(context, OrderAccessibilityService::class.java)

        // 2) The secure setting. Android stores each entry either in the
        //    full form ("com.wayars.app/com.wayars.app.service.….Service")
        //    or the SHORT form ("com.wayars.app/.service.….Service") —
        //    which one depends on who wrote it (Settings app, the system
        //    after a reboot/update/re-enable, OEM skins). Comparing the raw
        //    string only against the full form made the app think the
        //    service was OFF while it was ON, and ask for it again on every
        //    launch. Parse each entry instead, so both forms match.
        val enabledSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val inSetting = enabledSetting.split(':').any { entry ->
            ComponentName.unflattenFromString(entry.trim()) == expected
        }
        if (inSetting) return true

        // 3) What the AccessibilityManager itself reports as enabled.
        return runCatching {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
                val si = info.resolveInfo?.serviceInfo
                si != null && si.packageName == expected.packageName && si.name == expected.className
            }
        }.getOrDefault(false)
    }

    /** Same idea as [isServiceEnabled] but for the notification-listener backstop. */
    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
