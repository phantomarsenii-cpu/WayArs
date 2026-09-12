package com.wayars.app.domain.model

/**
 * A per-package parsing hint learned via Settings -> "Поддерживаемые
 * приложения" -> calibration, for a custom app where the built-in generic
 * money/km/min patterns in [com.wayars.app.util.ScreenTextParser] don't
 * match its screen (e.g. an unfamiliar currency notation, or a distance/
 * time unit word ScreenTextParser doesn't already recognize).
 *
 * Deliberately NOT a snapshot of exact values from one order (those change
 * every order) — only the literal UNIT TOKEN the user pointed at (a
 * currency marker like "CHF", a distance word like "mi", a time word like
 * "hrs"). ScreenTextParser turns that token into an extra regex tried
 * first for this specific package, on top of (not instead of) the
 * built-in patterns, so a screen layout change in the target app that
 * still uses the same unit words keeps working.
 */
data class PackageHint(
    val packageName: String,
    val moneyToken: String? = null,
    val moneyCurrency: Currency? = null,
    val distanceUnitToken: String? = null,
    val timeUnitToken: String? = null
) {
    val isEmpty: Boolean
        get() = moneyToken == null && distanceUnitToken == null && timeUnitToken == null
}
