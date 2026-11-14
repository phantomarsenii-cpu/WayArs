package com.wayars.app.features

import android.content.Context

/** Short, human name of a source app for statistics and notifications ("Bolt", "Uber"…). */
object AppNames {
    private val known = mapOf(
        "ee.mtakso.driver" to "Bolt",
        "com.bolt.deliverycourier" to "Bolt Courier",
        "com.ubercab.driver" to "Uber",
        "taxi.android.driver" to "FreeNow",
        "com.wolt.courierapp" to "Wolt",
        "com.wolt.courier.app" to "Wolt",
        "com.stuart.courier" to "Stuart"
    )

    fun label(context: Context, pkg: String): String = known[pkg] ?: runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))
}
