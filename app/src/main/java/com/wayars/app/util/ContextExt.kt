package com.wayars.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * RevenueCat's purchase flow needs a real [Activity] to attach the Play
 * Billing sheet to. `LocalContext.current` inside Compose is usually the
 * Activity itself, but theming/attach wrappers can hand back a
 * [ContextWrapper] instead — unwrap defensively rather than assuming.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
