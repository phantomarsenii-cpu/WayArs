#!/usr/bin/env bash
# update_project-11-subscription-from-settings.sh  (WayArs)
# Google Play rejected the app because reviewers could not get past the paywall shown at start.
#  1) No paywall at launch: Splash/Terms go straight to Onboarding/Main for everyone.
#  2) Settings: new card at the top - "Activate subscription" (opens the paywall) or
#     "Subscription active" when already subscribed. Tour step indices shifted by +1.
#  3) Dashboard "Active" switch still requires a verified subscription; without it the
#     paywall opens on top of Main (back / X returns to the app). Toast text reworded.
#  4) Paywall: close (X) button; on success it just returns to Main.
#  5) First-run tour and update card no longer require a subscription.
#  6) ONE-TIME FREE TRIAL: 30 minutes of scanning-ON time per install (new FreeTrial.kt).
#     Counted only while scanning runs without a subscription, survives restarts, and is
#     never reset. When it runs out, scanning is stopped (even in background), a toast
#     is shown and the paywall opens. Dashboard shows the minutes left.
#  7) "Active" tapped without the needed permissions: the usual toast, then the app jumps to
#     Settings, scrolls to the permissions card, opens it and (if screen reading is off)
#     shows the Accessibility consent / prominent-disclosure dialog right away.
#  8) Consent text (en/ru/pl) now matches what the service really does: it receives system
#     events from all apps (supported packages + user-added ones are checked in code, so a
#     static android:packageNames list is not possible) but ignores every other app
#     immediately; optional notification access is disclosed too. Stale XML comment fixed.
#  9) In-app Privacy Policy (ALL 17 languages) brought in line with the website policy and the app:
#     no clicks/typing, other apps ignored, consent shown first, optional notification access,
#     fuel cost / net profit in history, local free-trial counter, RevenueCat technical data.
# 10) Play Console warning "DEX optimization below threshold: obfuscation 0%": release builds now
#     run R8 (isMinifyEnabled = true) with readable crash stack traces (mapping goes to Play
#     inside the AAB automatically). TEST the release APK on a phone before uploading the AAB.
# 11) Overlay widget now also shows net profit after fuel: "Чисто X (топливо Y)" under the rate badge,
#     ONLY when the user has entered fuel consumption AND fuel price in the vehicle settings
#     (the built-in default profile does not count) and the order is >= 1 km (under 1 km the badge
#     already shows the net profit). The same rule applies to the verdict card in the app.
# 12) "Set up your vehicle" prompt: when the user taps Active, all permissions are granted and no vehicle
#     profile was ever saved, a dialog offers [Set up] (-> Settings, vehicle card opens) or [Skip]
#     (scanning starts on the default profile). Shown at most 2 times in total. It never overlaps other
#     UI: not during the first-run tour, and when the Android notification-permission dialog is up it
#     waits until that one is answered. Texts are translated into all 17 languages.
# 13) Statistics screen: choose Today / 7 days / Month / Year (choice is saved), totals (earned, orders,
#     distance, average rate per km), a tappable bar chart of earnings and the recent orders of the period.
#     Uses all accepted orders, not just today's. Texts translated into all 17 languages.
# 14) Order overlay glows around its contour in the verdict colour (green = good, amber = average,
#     red = bad); smooth colour change; plain thin border while no order is shown.
#     The glow PULSES (slow breathing of brightness and reach, ~2.6 s per cycle); it stays steady when
#     the user turned system animations off (reduced motion).
# 16) Google Play rejection (AccessibilityService disclosure not detailed enough): the consent dialog now names the
#     AccessibilityService API, the feature it serves (order scanning + overlay verdict), exactly what is read, what is
#     NOT done (no taps/typing, no call recording), where data goes (stays on device), and how to turn it off.
#     Rewritten in all 17 languages; the dialog text is scrollable so the buttons are always reachable.
# 15) Verdict fix: when the source app shows no trip time (Uber Eats / Stuart: only "2.8 km"), minutes arrive
#     as 0, so EUR/min was 0 and EVERY such order was marked BAD even at 8 zl/km. Now, whenever the trip
#     time is unknown, the verdict is judged on net money per km only (any distance).
#     The overlay and the verdict card no longer print a misleading "0 min".
# Idempotent: safe to run twice.  Run BEFORE git add/commit/push.
set -e
cd "$(dirname "$0")"
[ -f app/src/main/AndroidManifest.xml ] || { echo "Run this script from the WayArs repo root"; exit 1; }

python3 - <<'PYEOF'
import io, re

BASE = "app/src/main/java/com/wayars/app/presentation/"

def rw(path, fn):
    s = io.open(path, encoding="utf-8").read()
    out = fn(s)
    if out != s:
        io.open(path, "w", encoding="utf-8").write(out)
        print("updated", path)
    else:
        print("already OK", path)

def sub(s, old, new, marker=None):
    if (marker or new) in s:
        return s
    if old not in s:
        raise SystemExit("PATTERN NOT FOUND: " + old[:70])
    return s.replace(old, new, 1)

def add_import(s, line):
    # exact whole-line match (a plain substring test would treat ...animateFloat as present because of ...animateFloatAsState)
    if re.search("^" + re.escape(line) + "$", s, re.M):
        return s
    m = re.search(r"^import .*$", s, re.M)
    return s[:m.start()] + line + "\n" + s[m.start():]

# ---------------------------------------------------------------- NavHost
def nav(s):
    if "fun openPaywall()" in s:
        return s
    # 1) gate no longer decides anything: always continue into the app
    s, n = re.subn(
        r"    fun navigateFromGate\(fromRoute: String\) \{.*?\n    \}\n",
        "    fun navigateFromGate(fromRoute: String) {\n"
        "        // No paywall at launch (Google Play reviewers must be able to open the app).\n"
        "        // Subscription is only required to switch scanning ON (Dashboard \"Active\").\n"
        "        navController.navigate(destinationAfterGate()) {\n"
        "            popUpTo(fromRoute) { inclusive = true }\n"
        "        }\n"
        "    }\n", s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit("navigateFromGate not found")
    s = sub(s, "LaunchedEffect(splashAnimationDone, termsAcceptedAt, gateState)",
            "LaunchedEffect(splashAnimationDone, termsAcceptedAt)")
    s = sub(s, "LaunchedEffect(termsAcceptedAt, gateState) {\n                    if (termsAcceptedAt == null)",
            "LaunchedEffect(termsAcceptedAt) {\n                    if (termsAcceptedAt == null)")
    # 2) paywall: leave by popping back to Main when it was opened from there
    s = sub(s,
        "                    if (gateState is SubscriptionState.Subscribed) {\n"
        "                        navController.navigate(destinationAfterGate()) {\n"
        "                            popUpTo(Routes.PAYWALL) { inclusive = true }\n"
        "                        }\n"
        "                    }\n",
        "                    if (gateState is SubscriptionState.Subscribed) {\n"
        "                        if (navController.previousBackStackEntry?.destination?.route == Routes.MAIN) {\n"
        "                            navController.popBackStack()\n"
        "                        } else {\n"
        "                            navController.navigate(destinationAfterGate()) {\n"
        "                                popUpTo(Routes.PAYWALL) { inclusive = true }\n"
        "                            }\n"
        "                        }\n"
        "                    }\n", "previousBackStackEntry?.destination?.route")
    s = sub(s, "                    onRestore = { subscriptionViewModel.restorePurchases() },\n",
            "                    onRestore = { subscriptionViewModel.restorePurchases() },\n"
            "                    onClose = { navController.popBackStack() },\n", "onClose = { navController.popBackStack() }")
    # 3) Main: paywall opens ON TOP of Main; no forced redirect any more
    s, n = re.subn(
        r"                fun goToPaywall\(\) \{.*?\n                \}\n\n                LaunchedEffect\(gateState\) \{.*?\n                \}\n",
        "                fun openPaywall() {\n"
        "                    navController.navigate(Routes.PAYWALL) { launchSingleTop = true }\n"
        "                }\n\n"
        "                fun goToPaywall() {\n"
        "                    ScanningState.setActive(false, mainContext)\n"
        "                    openPaywall()\n"
        "                }\n\n"
        "                // A lapsed subscription only switches scanning off (the user stays in the app).\n"
        "                LaunchedEffect(gateState) {\n"
        "                    if (gateState is SubscriptionState.NotSubscribed) {\n"
        "                        ScanningState.setActive(false, mainContext)\n"
        "                    }\n"
        "                }\n", s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit("goToPaywall block not found")
    s = sub(s, "                    onSubscriptionRequired = { goToPaywall() },\n",
            "                    onSubscriptionRequired = { goToPaywall() },\n"
            "                    onOpenPaywall = { openPaywall() },\n", "onOpenPaywall = { openPaywall() }")
    return s
rw(BASE + "ui/navigation/WayArsNavHost.kt", nav)

# ---------------------------------------------------------------- MainScreen
def main(s):
    s = sub(s, "    onSubscriptionRequired: () -> Unit,\n    tour: TourController,",
            "    onSubscriptionRequired: () -> Unit,\n    onOpenPaywall: () -> Unit,\n    tour: TourController,", "onOpenPaywall: () -> Unit")
    s = sub(s, "if (tourDone == false && isSubscribed && !tour.running)", "if (tourDone == false && !tour.running)")
    s = sub(s, "if (updateChecked || !isSubscribed || tourDone != true || updateSnoozeUntil == null)",
            "if (updateChecked || tourDone != true || updateSnoozeUntil == null)")
    s = sub(s, "                    onReplayTour = { tour.start() }\n",
            "                    onReplayTour = { tour.start() },\n"
            "                    isSubscribed = isSubscribed,\n"
            "                    onOpenPaywall = onOpenPaywall\n", "onOpenPaywall = onOpenPaywall")
    return s
rw(BASE + "ui/navigation/MainScreen.kt", main)

# ---------------------------------------------------------------- Tour indices (+1)
def tour(s):
    if "MainTab.SETTINGS, 6, R.string.tour_apps_title" in s:
        return s
    for tgt, a, b in (("SETTINGS_APPS", 5, 6), ("SETTINGS_PERMISSIONS", 4, 5),
                      ("SETTINGS_VEHICLE", 3, 4), ("SETTINGS_THRESHOLDS", 2, 3)):
        s = sub(s, "TourTarget.%s, MainTab.SETTINGS, %d," % (tgt, a),
                "TourTarget.%s, MainTab.SETTINGS, %d," % (tgt, b), "TourTarget.%s, MainTab.SETTINGS, %d," % (tgt, b))
    return s
rw(BASE + "ui/tour/TourController.kt", tour)

# ---------------------------------------------------------------- Settings
def settings(s):
    s = add_import(s, "import androidx.compose.material.icons.filled.Check")
    s = add_import(s, "import androidx.compose.material.icons.filled.Star")
    s = sub(s, "    onReplayTour: () -> Unit = {}\n) {\n    LazyColumn(",
            "    onReplayTour: () -> Unit = {},\n    isSubscribed: Boolean = false,\n    onOpenPaywall: () -> Unit = {}\n) {\n    LazyColumn(",
            "onOpenPaywall: () -> Unit = {}")
    s = sub(s, "        item {\n            GeneralSettingsCard(",
            "        item {\n            SubscriptionSection(isSubscribed = isSubscribed, onOpenPaywall = onOpenPaywall)\n        }\n\n        item {\n            GeneralSettingsCard(",
            "SubscriptionSection(isSubscribed = isSubscribed")
    if "private fun SubscriptionSection" not in s:
        s = s.rstrip("\n") + '''

/**
 * Where a not-yet-subscribed user starts the subscription. The app itself is open to everyone;
 * only turning order scanning ON (Dashboard "Active") needs an active subscription.
 */
@Composable
private fun SubscriptionSection(isSubscribed: Boolean, onOpenPaywall: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
    ) {
        if (isSubscribed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = com.wayars.app.presentation.ui.theme.WaNeonGreen)
                Text(
                    stringResource(R.string.settings_subscription_active),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            InfoRow(Icons.Filled.Star, stringResource(R.string.settings_subscription_activate)) { onOpenPaywall() }
            Text(
                stringResource(R.string.settings_subscription_hint),
                color = WaTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 14.dp)
            )
        }
    }
}
'''
    return s
rw(BASE + "ui/screen/settings/SettingsScreen.kt", settings)

# ---------------------------------------------------------------- Paywall close button
def paywall(s):
    for imp in ("import androidx.compose.foundation.layout.Arrangement",
                "import androidx.compose.foundation.layout.Row",
                "import androidx.compose.material.icons.Icons",
                "import androidx.compose.material.icons.filled.Close",
                "import androidx.compose.material3.Icon",
                "import androidx.compose.material3.IconButton"):
        s = add_import(s, imp)
    s = sub(s, "    onRetryGateCheck: () -> Unit = {}\n) {\n    // RevenueCat hands back",
            "    onRetryGateCheck: () -> Unit = {},\n    onClose: (() -> Unit)? = null\n) {\n    // RevenueCat hands back",
            "onClose: (() -> Unit)? = null")
    s = sub(s, "            gateErrorMessage?.let { message ->\n",
            "            onClose?.let { close ->\n"
            "                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {\n"
            "                    IconButton(onClick = close) {\n"
            "                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)\n"
            "                    }\n"
            "                }\n"
            "            }\n"
            "            gateErrorMessage?.let { message ->\n", "onClose?.let { close ->")
    return s
rw(BASE + "ui/screen/paywall/PaywallScreen.kt", paywall)

# ---------------------------------------------------------------- FreeTrial.kt (new)
FT = "app/src/main/java/com/wayars/app/service/accessibility/FreeTrial.kt"
FT_SRC = """package com.wayars.app.service.accessibility

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One-time free trial of scanning: [TRIAL_MS] of scanning-ON time per install, in total, ever.
 * Only time spent scanning WITHOUT a subscription is counted. Persisted in SharedPreferences
 * (same approach as [ActiveSessionTracker]); a session cut short by process death is credited
 * up to the last heartbeat. There is deliberately no way to reset it in the app.
 */
object FreeTrial {

    const val TRIAL_MS = 30L * 60_000L

    private const val PREFS = "free_trial"
    private const val KEY_USED = "used_ms"
    private const val KEY_START = "session_start"
    private const val KEY_BEAT = "last_beat"

    @Volatile
    private var appContext: Context? = null

    /** Mirrors the verified subscription state; set from WayArsApplication. */
    @Volatile
    private var subscribed = false

    private val _expired = MutableStateFlow(false)
    /** True after the trial ran out while scanning; the UI shows the paywall and calls [consumeExpired]. */
    val expired: StateFlow<Boolean> = _expired

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        val p = prefs() ?: return
        val start = p.getLong(KEY_START, 0L)
        if (start > 0L) {
            val beat = maxOf(p.getLong(KEY_BEAT, start), start)
            credit(start, beat)
        }
    }

    @Synchronized
    fun setSubscribed(value: Boolean) {
        subscribed = value
        if (value) closeSession(System.currentTimeMillis())
    }

    @Synchronized
    fun onActiveChanged(active: Boolean, context: Context) {
        if (appContext == null) appContext = context.applicationContext
        val p = prefs() ?: return
        val now = System.currentTimeMillis()
        if (active) {
            if (!subscribed && p.getLong(KEY_START, 0L) == 0L) {
                p.edit().putLong(KEY_START, now).putLong(KEY_BEAT, now).apply()
            }
        } else {
            closeSession(now)
        }
    }

    /** Scanning is running without a subscription but no trial session is open yet: open one. */
    @Synchronized
    fun ensureCounting(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        val p = prefs() ?: return
        if (!subscribed && p.getLong(KEY_START, 0L) == 0L) {
            val now = System.currentTimeMillis()
            p.edit().putLong(KEY_START, now).putLong(KEY_BEAT, now).apply()
        }
    }

    @Synchronized
    fun heartbeat() {
        val p = prefs() ?: return
        if (p.getLong(KEY_START, 0L) > 0L) {
            p.edit().putLong(KEY_BEAT, System.currentTimeMillis()).apply()
        }
    }

    @Synchronized
    fun usedMillis(now: Long = System.currentTimeMillis()): Long {
        val p = prefs() ?: return 0L
        val start = p.getLong(KEY_START, 0L)
        val running = if (start > 0L) maxOf(0L, now - start) else 0L
        return minOf(TRIAL_MS, p.getLong(KEY_USED, 0L) + running)
    }

    fun remainingMillis(): Long = TRIAL_MS - usedMillis()

    fun hasTimeLeft(): Boolean = remainingMillis() > 0L

    /** Whole minutes left, rounded up (0 only when the trial is over). */
    fun minutesLeft(): Int = ((remainingMillis() + 59_999L) / 60_000L).toInt()

    fun signalExpired() { _expired.value = true }

    fun consumeExpired() { _expired.value = false }

    private fun closeSession(now: Long) {
        val p = prefs() ?: return
        val start = p.getLong(KEY_START, 0L)
        if (start > 0L) credit(start, now)
    }

    private fun credit(start: Long, end: Long) {
        val p = prefs() ?: return
        val used = minOf(TRIAL_MS, p.getLong(KEY_USED, 0L) + maxOf(0L, end - start))
        p.edit().putLong(KEY_USED, used).putLong(KEY_START, 0L).apply()
    }
}
"""
import os
if not os.path.exists(FT):
    io.open(FT, "w", encoding="utf-8").write(FT_SRC)
    print("created", FT)
else:
    print("already OK", FT)

SVC = "app/src/main/java/com/wayars/app/service/accessibility/ScanningState.kt"
def scanning(s):
    return sub(s, "        ActiveSessionTracker.onActiveChanged(active, context)\n",
               "        ActiveSessionTracker.onActiveChanged(active, context)\n        FreeTrial.onActiveChanged(active, context)\n",
               "FreeTrial.onActiveChanged(active, context)")
rw(SVC, scanning)

# ---------------------------------------------------------------- Application
def app(s):
    s = add_import(s, "import com.wayars.app.service.accessibility.FreeTrial")
    s = sub(s, "        ActiveSessionTracker.init(this)\n",
            "        ActiveSessionTracker.init(this)\n        FreeTrial.init(this)\n", "FreeTrial.init(this)")
    s = sub(s, "        observeAppForeground()\n        persistResolvedLanguageIfMissing()\n",
            "        observeAppForeground()\n"
            "        applicationScope.launch {\n"
            "            container.subscriptionRepository.subscriptionState.collect { state ->\n"
            "                FreeTrial.setSubscribed(state is SubscriptionState.Subscribed)\n"
            "            }\n"
            "        }\n"
            "        persistResolvedLanguageIfMissing()\n", "FreeTrial.setSubscribed(")
    s = sub(s, "                if (active) startPollingIfNeeded()\n",
            "                if (active) {\n"
            "                    startPollingIfNeeded()\n"
            "                    scheduleTrialWatch()\n"
            "                } else {\n"
            "                    trialJob?.cancel()\n"
            "                }\n", "scheduleTrialWatch()")
    s = sub(s, "        if (!stillSubscribed && ScanningState.isActive.value) {\n            ScanningState.setActive(false, this@WayArsApplication)\n        }\n",
            "        if (!stillSubscribed && ScanningState.isActive.value) {\n"
            "            // Not subscribed: scanning may only continue while the one-time free trial has time left.\n"
            "            FreeTrial.ensureCounting(this@WayArsApplication)\n"
            "            if (!FreeTrial.hasTimeLeft()) expireTrial()\n"
            "        }\n", "if (!FreeTrial.hasTimeLeft()) expireTrial()")
    s = sub(s, "    private fun startPollingIfNeeded() {\n",
            "    private var trialJob: Job? = null\n\n"
            "    private fun expireTrial() {\n"
            "        ScanningState.setActive(false, this@WayArsApplication)\n"
            "        FreeTrial.signalExpired()\n"
            "    }\n\n"
            "    /** While scanning runs without a subscription, stops it the moment the free trial is used up. */\n"
            "    private fun scheduleTrialWatch() {\n"
            "        trialJob?.cancel()\n"
            "        trialJob = applicationScope.launch {\n"
            "            while (isActive && ScanningState.isActive.value) {\n"
            "                val state = container.subscriptionRepository.subscriptionState.value\n"
            "                if (state is SubscriptionState.Subscribed) {\n"
            "                    delay(5_000L)\n"
            "                    continue\n"
            "                }\n"
            "                FreeTrial.ensureCounting(this@WayArsApplication)\n"
            "                val left = FreeTrial.remainingMillis()\n"
            "                if (left <= 0L) {\n"
            "                    expireTrial()\n"
            "                    break\n"
            "                }\n"
            "                delay(minOf(left + 100L, 5_000L))\n"
            "            }\n"
            "        }\n"
            "    }\n\n"
            "    private fun startPollingIfNeeded() {\n", "private fun scheduleTrialWatch()")
    s = sub(s, "                ActiveSessionTracker.heartbeat()\n",
            "                ActiveSessionTracker.heartbeat()\n                FreeTrial.heartbeat()\n", "FreeTrial.heartbeat()")
    return s
rw("app/src/main/java/com/wayars/app/WayArsApplication.kt", app)

# ---------------------------------------------------------------- NavHost: trial-aware lapse handling + expiry -> paywall
def nav2(s):
    s = add_import(s, "import com.wayars.app.service.accessibility.FreeTrial")
    s = sub(s,
        "                LaunchedEffect(gateState) {\n"
        "                    if (gateState is SubscriptionState.NotSubscribed) {\n"
        "                        ScanningState.setActive(false, mainContext)\n"
        "                    }\n"
        "                }\n",
        "                LaunchedEffect(gateState) {\n"
        "                    if (gateState is SubscriptionState.NotSubscribed && !FreeTrial.hasTimeLeft()) {\n"
        "                        ScanningState.setActive(false, mainContext)\n"
        "                    }\n"
        "                }\n\n"
        "                // Free trial ran out while scanning: scanning is already stopped (WayArsApplication);\n"
        "                // tell the user and offer the subscription.\n"
        "                val trialExpired by FreeTrial.expired.collectAsState()\n"
        "                val trialEndedToast = stringResource(R.string.active_requires_subscription)\n"
        "                LaunchedEffect(trialExpired) {\n"
        "                    if (trialExpired) {\n"
        "                        Toast.makeText(mainContext, trialEndedToast, Toast.LENGTH_LONG).show()\n"
        "                        FreeTrial.consumeExpired()\n"
        "                        openPaywall()\n"
        "                    }\n"
        "                }\n", "val trialExpired by FreeTrial.expired.collectAsState()")
    return s
rw(BASE + "ui/navigation/WayArsNavHost.kt", nav2)

# ---------------------------------------------------------------- Dashboard
def dash(s):
    s = add_import(s, "import com.wayars.app.service.accessibility.FreeTrial")
    s = sub(s, "        if (!isSubscribed) {\n            Toast.makeText(context, requiresSubscriptionToast",
            "        if (!isSubscribed && !FreeTrial.hasTimeLeft()) {\n            Toast.makeText(context, requiresSubscriptionToast",
            "if (!isSubscribed && !FreeTrial.hasTimeLeft())")
    s = sub(s, "val needsNotificationPermission = isSubscribed &&",
            "val needsNotificationPermission = (isSubscribed || FreeTrial.hasTimeLeft()) &&",
            "(isSubscribed || FreeTrial.hasTimeLeft())")
    s = sub(s, "    val requiresSubscriptionToast = stringResource(R.string.active_requires_subscription)\n",
            "    val requiresSubscriptionToast = stringResource(R.string.active_requires_subscription)\n"
            "    // Minutes of the one-time free trial left (only shown to a user without a subscription).\n"
            "    val trialLeftMinutes by produceState(initialValue = FreeTrial.minutesLeft(), isActive, isSubscribed) {\n"
            "        while (true) {\n"
            "            value = FreeTrial.minutesLeft()\n"
            "            delay(15_000L)\n"
            "        }\n"
            "    }\n", "val trialLeftMinutes by produceState")
    s = sub(s, "        // \"Share log\" row hidden from the dashboard UI per product decision",
            "        if (!isSubscribed && trialLeftMinutes > 0) {\n"
            "            Text(\n"
            "                stringResource(R.string.dashboard_trial_left, trialLeftMinutes),\n"
            "                color = WaNeonGreen,\n"
            "                fontSize = 13.sp\n"
            "            )\n"
            "        }\n\n"
            "        // \"Share log\" row hidden from the dashboard UI per product decision",
            "R.string.dashboard_trial_left")
    return s
rw(BASE + "ui/screen/dashboard/DashboardScreen.kt", dash)

# ---------------------------------------------------------------- "Active" without permissions -> Settings + consent
def dash_perm(s):
    s = sub(s, "    onSubscriptionRequired: () -> Unit,\n    modifier: Modifier = Modifier\n) {",
            "    onSubscriptionRequired: () -> Unit,\n    onPermissionsRequired: () -> Unit = {},\n    modifier: Modifier = Modifier\n) {",
            "onPermissionsRequired: () -> Unit = {}")
    s = sub(s, "                context.getString(R.string.active_requires_permissions),\n                Toast.LENGTH_LONG\n            ).show()\n",
            "                context.getString(R.string.active_requires_permissions),\n                Toast.LENGTH_LONG\n            ).show()\n"
            "            // Take the user straight to the permissions (and the screen-reading consent dialog).\n"
            "            onPermissionsRequired()\n", "            onPermissionsRequired()\n")
    return s
rw(BASE + "ui/screen/dashboard/DashboardScreen.kt", dash_perm)

def main_perm(s):
    s = sub(s, "    val settingsListState = rememberLazyListState()\n",
            "    val settingsListState = rememberLazyListState()\n    var showPermissionsPrompt by remember { mutableStateOf(false) }\n    var permissionsRequestId by remember { mutableStateOf(0) }\n",
            "var showPermissionsPrompt by remember")
    s = sub(s, "    // When the tour ends, land on the Home tab again.\n",
            "    // \"Active\" tapped without permissions: the caller switches to the Settings tab; bring the\n"
            "    // permissions card into view (it opens itself and shows the consent dialog).\n"
            "    LaunchedEffect(permissionsRequestId) {\n"
            "        if (permissionsRequestId > 0) {\n"
            "            delay(80L)\n"
            "            runCatching { settingsListState.animateScrollToItem(5) }\n"
            "        }\n"
            "    }\n\n"
            "    // When the tour ends, land on the Home tab again.\n", "LaunchedEffect(permissionsRequestId)")
    s = sub(s, "                    onSubscriptionRequired = onSubscriptionRequired\n                )\n",
            "                    onSubscriptionRequired = onSubscriptionRequired,\n"
            "                    onPermissionsRequired = {\n"
            "                        tab = MainTab.SETTINGS\n"
            "                        showPermissionsPrompt = true\n"
            "                        permissionsRequestId++\n"
            "                    }\n"
            "                )\n", "onPermissionsRequired = {")
    s = sub(s, "                    isSubscribed = isSubscribed,\n                    onOpenPaywall = onOpenPaywall\n",
            "                    isSubscribed = isSubscribed,\n                    onOpenPaywall = onOpenPaywall,\n"
            "                    permissionsPrompt = showPermissionsPrompt,\n"
            "                    onPermissionsPromptHandled = { showPermissionsPrompt = false }\n",
            "permissionsPrompt = showPermissionsPrompt")
    return s
rw(BASE + "ui/navigation/MainScreen.kt", main_perm)

def settings_perm(s):
    s = add_import(s, "import androidx.compose.runtime.LaunchedEffect")
    s = sub(s, "    onOpenPaywall: () -> Unit = {}\n) {\n    LazyColumn(",
            "    onOpenPaywall: () -> Unit = {},\n    permissionsPrompt: Boolean = false,\n    onPermissionsPromptHandled: () -> Unit = {}\n) {\n    LazyColumn(",
            "permissionsPrompt: Boolean = false")
    s = sub(s, "                    onOpenNotificationSettings = onOpenNotificationSettings\n                )\n",
            "                    onOpenNotificationSettings = onOpenNotificationSettings,\n"
            "                    openRequest = permissionsPrompt,\n"
            "                    onRequestHandled = onPermissionsPromptHandled\n"
            "                )\n", "openRequest = permissionsPrompt")
    s = sub(s, "    onOpenNotificationSettings: () -> Unit\n) {\n    var expanded by remember { mutableStateOf(false) }",
            "    onOpenNotificationSettings: () -> Unit,\n    openRequest: Boolean = false,\n    onRequestHandled: () -> Unit = {}\n) {\n    var expanded by remember { mutableStateOf(false) }",
            "openRequest: Boolean = false")
    s = sub(s, "    var showAccessibilityConsent by remember { mutableStateOf(false) }\n    val states = rememberPermissionStates()\n",
            "    var showAccessibilityConsent by remember { mutableStateOf(false) }\n    val states = rememberPermissionStates()\n\n"
            "    // Opened from the Dashboard (\"Active\" without permissions): expand the card and, if screen\n"
            "    // reading is still off, show the prominent-disclosure dialog straight away.\n"
            "    LaunchedEffect(openRequest) {\n"
            "        if (openRequest) {\n"
            "            expanded = true\n"
            "            if (!states.accessibility) showAccessibilityConsent = true\n"
            "            onRequestHandled()\n"
            "        }\n"
            "    }\n", "LaunchedEffect(openRequest)")
    return s
rw(BASE + "ui/screen/settings/SettingsScreen.kt", settings_perm)

# ---------------------------------------------------------------- Strings (en / ru / pl)
T = {
 "values":    ("Your free 30 minutes of scanning are used up — subscribe to keep going",
               "Activate subscription",
               "You get 30 free minutes of scanning, once. After that, scanning needs a subscription.",
               "Subscription active",
               "Free trial: %1$d min of scanning left"),
 "values-ru": ("Бесплатные 30 минут сканирования закончились — оформите подписку, чтобы продолжить",
               "Активировать подписку",
               "Даётся 30 бесплатных минут сканирования — один раз. Дальше сканирование работает по подписке.",
               "Подписка активна",
               "Пробный период: осталось %1$d мин сканирования"),
 "values-pl": ("Darmowe 30 minut skanowania zostało wykorzystane — wykup subskrypcję, aby kontynuować",
               "Aktywuj subskrypcję",
               "Dostajesz 30 darmowych minut skanowania — jednorazowo. Potem skanowanie wymaga subskrypcji.",
               "Subskrypcja aktywna",
               "Okres próbny: zostało %1$d min skanowania"),
}
for d, (req, act, hint, on, left) in T.items():
    def f(s, req=req, act=act, hint=hint, on=on, left=left):
        s = re.sub(r'(<string name="active_requires_subscription">).*?(</string>)',
                   lambda m: m.group(1) + req + m.group(2), s, count=1)
        if "settings_subscription_activate" not in s:
            line = re.search(r'^.*name="active_requires_subscription".*$', s, re.M).group(0)
            add = ('\n    <string name="settings_subscription_activate">%s</string>'
                   '\n    <string name="settings_subscription_hint">%s</string>'
                   '\n    <string name="settings_subscription_active">%s</string>') % (act, hint, on)
            s = s.replace(line, line + add, 1)
        s = re.sub(r'(<string name="settings_subscription_hint">).*?(</string>)',
                   lambda m: m.group(1) + hint + m.group(2), s, count=1)
        if "dashboard_trial_left" not in s:
            line = re.search(r'^.*name="settings_subscription_active".*$', s, re.M).group(0)
            s = s.replace(line, line + '\n    <string name="dashboard_trial_left">%s</string>' % left, 1)
        return s
    rw("app/src/main/res/%s/strings.xml" % d, f)

# ---------------------------------------------------------------- Consent text vs. real behaviour
def xml_comment(s):
    if "Package filtering happens in code" in s:
        return s
    new_c = ("<!--\n"
             "Package filtering happens in code (OrderAccessibilityService.isSupportedPackage): the built-in\n"
             "supported apps plus the apps the user adds in Settings, which cannot be listed statically here.\n"
             "The service therefore receives events from all apps but returns immediately for every app that is\n"
             "not supported/added, before reading any window content. The user-facing consent text says so.\n"
             "-->")
    s2, n = re.subn(r"<!--\s*TEMPORARY: android:packageNames.*?-->", new_c, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit("xml comment not found")
    return s2
rw("app/src/main/res/xml/accessibility_service_config.xml", xml_comment)

CONSENT = {
 "values": (
  "• WayArs only reads order details from the supported apps you choose. It never reads passwords, messages, or anything you type.",
  "• WayArs only reads order details from the supported apps (and any apps you add yourself). Everything shown by other apps is ignored immediately — nothing is read, kept or stored from them. It never reads passwords, messages, or anything you type.",
  "\\n\\nYou can turn this off at any time in Settings.",
  "\\n• If you also enable the optional notification access, WayArs reads only notifications of the supported apps about new orders."),
 "values-ru": (
  "• WayArs считывает только детали заказа из выбранных вами поддерживаемых приложений. Никогда не читает пароли, сообщения или то, что вы вводите.",
  "• WayArs считывает детали заказа только из поддерживаемых приложений (и тех, которые вы добавили сами). Всё, что показывают другие приложения, сразу игнорируется — ничего не читается и не сохраняется. Пароли, сообщения и то, что вы вводите, не читаются никогда.",
  "\\n\\nВы можете отключить это в любой момент в Настройках.",
  "\\n• Если вы дополнительно включите необязательный доступ к уведомлениям, WayArs читает только уведомления поддерживаемых приложений о новых заказах."),
 "values-pl": (
  "• WayArs odczytuje tylko szczegóły zlecenia z wybranych przez Ciebie obsługiwanych aplikacji. Nigdy nie odczytuje haseł, wiadomości ani tego, co wpisujesz.",
  "• WayArs odczytuje szczegóły zlecenia tylko z obsługiwanych aplikacji (oraz tych, które sam dodasz). Wszystko, co wyświetlają inne aplikacje, jest natychmiast ignorowane — nic nie jest odczytywane ani zapisywane. Haseł, wiadomości ani tego, co wpisujesz, nie odczytuje nigdy.",
  "\\n\\nMożesz to wyłączyć w dowolnym momencie w Ustawieniach.",
  "\\n• Jeśli dodatkowo włączysz opcjonalny dostęp do powiadomień, WayArs odczytuje tylko powiadomienia obsługiwanych aplikacji o nowych zleceniach."),
}
for d, (old_b, new_b, tail, notif) in CONSENT.items():
    def g(s, old_b=old_b, new_b=new_b, tail=tail, notif=notif):
        if "ignored immediately" in s or "сразу игнорируется" in s or "natychmiast ignorowane" in s:
            return s
        if old_b not in s or tail not in s:
            raise SystemExit("consent text not found in " + d)
        s = s.replace(old_b, new_b, 1)
        return s.replace(tail, notif + tail, 1)
    rw("app/src/main/res/%s/strings.xml" % d, g)

# ---------------------------------------------------------------- In-app privacy policy text
PRIV = {
 "values": [
  ("Last updated: October 5, 2026", "Last updated: October 8, 2026"),
  ("is not shared with third parties or used for advertising.",
   "is not shared with third parties or used for advertising. The service never taps, types or changes anything on the screen and is not used to record calls. Events from all other apps are ignored immediately: nothing from them is read or stored. Before the service can be enabled, the app shows a disclosure and asks for your consent, and you can turn it off at any time."),
  ("notification access is used only to detect new order offers while the app runs in the background.",
   "notification access is optional and is used only to detect new order offers from the supported apps while the app runs in the background. Other notifications are not read or stored."),
  ("calculated rates and verdict of each evaluated order",
   "calculated rates, estimated fuel cost, net profit and verdict of each evaluated order"),
  ("\\n• Installed apps list:",
   "\\n• Free trial: to provide the one-time free trial of scanning, the app stores on your device a counter of how long scanning has run. It is not sent anywhere.\\n• Installed apps list:"),
  ("subscription status verification based on anonymous device and transaction identifiers.",
   "subscription status verification based on anonymous device and transaction identifiers. RevenueCat may also process technical connection data such as an IP address."),
 ],
 "values-ru": [
  ("Последнее обновление: 5 октября 2026 г.", "Последнее обновление: 8 октября 2026 г."),
  ("не передаются третьим лицам и не используются для рекламы.",
   "не передаются третьим лицам и не используются для рекламы. Служба никогда ничего не нажимает, не вводит и не меняет на экране и не используется для записи звонков. События всех остальных приложений сразу игнорируются: ничего из них не читается и не сохраняется. Перед включением службы приложение показывает пояснение и запрашивает ваше согласие; вы можете отключить её в любой момент."),
  ("Доступ к уведомлениям используется только для обнаружения новых предложений заказов, пока приложение работает в фоновом режиме.",
   "Доступ к уведомлениям необязателен и используется только для обнаружения новых предложений заказов от поддерживаемых приложений, пока приложение работает в фоновом режиме. Остальные уведомления не читаются и не сохраняются."),
  ("рассчитанные тарифы и вердикт по каждому оценённому заказу",
   "рассчитанные тарифы, ориентировочные расходы на топливо, чистую прибыль и вердикт по каждому оценённому заказу"),
  ("\\n• Список установленных приложений:",
   "\\n• Пробный период: для разового бесплатного пробного периода сканирования приложение хранит на вашем устройстве счётчик времени работы сканирования. Он никуда не отправляется.\\n• Список установленных приложений:"),
  ("Проверка подписки на основе анонимных идентификаторов устройства и транзакций.",
   "Проверка подписки на основе анонимных идентификаторов устройства и транзакций. RevenueCat также может обрабатывать технические данные соединения, например IP-адрес."),
 ],
 "values-pl": [
  ("Data ostatniej aktualizacji: 5 października 2026 r.", "Data ostatniej aktualizacji: 8 października 2026 r."),
  ("nie są udostępniane osobom trzecim ani wykorzystywane do reklam.",
   "nie są udostępniane osobom trzecim ani wykorzystywane do reklam. Usługa nigdy niczego nie klika, nie wpisuje ani nie zmienia na ekranie i nie służy do nagrywania rozmów. Zdarzenia z wszystkich pozostałych aplikacji są natychmiast ignorowane: nic z nich nie jest odczytywane ani zapisywane. Przed włączeniem usługi aplikacja wyświetla objaśnienie i prosi o Twoją zgodę; możesz ją wyłączyć w dowolnej chwili."),
  ("Dostęp do powiadomień służy wyłącznie do wykrywania nowych propozycji zleceń, gdy aplikacja działa w tle.",
   "Dostęp do powiadomień jest opcjonalny i służy wyłącznie do wykrywania nowych propozycji zleceń z obsługiwanych aplikacji, gdy aplikacja działa w tle. Pozostałe powiadomienia nie są odczytywane ani zapisywane."),
  ("obliczone stawki i ocenę każdego ocenionego zlecenia",
   "obliczone stawki, szacowany koszt paliwa, zysk netto i ocenę każdego ocenionego zlecenia"),
  ("\\n• Lista zainstalowanych aplikacji:",
   "\\n• Okres próbny: na potrzeby jednorazowego darmowego okresu próbnego skanowania aplikacja zapisuje na Twoim urządzeniu licznik czasu działania skanowania. Nie jest on nigdzie wysyłany.\\n• Lista zainstalowanych aplikacji:"),
  ("Weryfikacja subskrypcji na podstawie anonimowych identyfikatorów urządzenia i transakcji.",
   "Weryfikacja subskrypcji na podstawie anonimowych identyfikatorów urządzenia i transakcji. RevenueCat może też przetwarzać techniczne dane połączenia, takie jak adres IP."),
 ],
 "values-uk": [
  ("Останнє оновлення: 5 жовтня 2026 р.", "Останнє оновлення: 8 жовтня 2026 р."),
  ("не передаються третім особам і не використовуються для реклами.",
   "не передаються третім особам і не використовуються для реклами. Служба ніколи нічого не натискає, не вводить і не змінює на екрані та не використовується для запису дзвінків. Події всіх інших застосунків одразу ігноруються: нічого з них не зчитується й не зберігається. Перед увімкненням служби застосунок показує пояснення й запитує вашу згоду; ви можете вимкнути її будь-коли."),
  ("Доступ до сповіщень використовується лише для виявлення нових пропозицій замовлень, поки застосунок працює у фоновому режимі.",
   "Доступ до сповіщень необов’язковий і використовується лише для виявлення нових пропозицій замовлень від підтримуваних застосунків, поки застосунок працює у фоновому режимі. Інші сповіщення не зчитуються й не зберігаються."),
  ("розраховані тарифи та вердикт щодо кожного оціненого замовлення",
   "розраховані тарифи, орієнтовні витрати на пальне, чистий прибуток і вердикт щодо кожного оціненого замовлення"),
  ("\\n• Список встановлених застосунків:",
   "\\n• Пробний період: для разового безкоштовного пробного періоду сканування застосунок зберігає на вашому пристрої лічильник часу роботи сканування. Він нікуди не надсилається.\\n• Список встановлених застосунків:"),
  ("Перевірка підписки на основі анонімних ідентифікаторів пристрою та транзакцій.",
   "Перевірка підписки на основі анонімних ідентифікаторів пристрою та транзакцій. RevenueCat також може обробляти технічні дані з’єднання, наприклад IP-адресу."),
 ],
}
# The remaining 13 languages carry the policy in English (untranslated copy), so they get the
# same English update. Result: every language shows the same, current policy text.
for _d in ("az", "bg", "cs", "da", "de", "fr", "hu", "hy", "ka", "nb", "ro", "sv", "uz"):
    PRIV["values-" + _d] = PRIV["values"]
for d, pairs in PRIV.items():
    def h(s, pairs=pairs):
        m = re.search(r'(<string name="settings_privacy_content">)(.*?)(</string>)', s, re.S)
        if not m:
            raise SystemExit("settings_privacy_content missing in " + d)
        body = m.group(2)
        for old, new in pairs:
            if new in body:
                continue
            if body.count(old) != 1:
                raise SystemExit("privacy text: %d matches in %s for: %s" % (body.count(old), d, old[:50]))
            body = body.replace(old, new, 1)
        return s[:m.start(2)] + body + s[m.end(2):]
    rw("app/src/main/res/%s/strings.xml" % d, h)

# ---------------------------------------------------------------- R8 / obfuscation (Play warning)
def gradle(s):
    if "isMinifyEnabled = true" in s:
        return s
    if s.count("isMinifyEnabled = false") != 1:
        raise SystemExit("isMinifyEnabled = false found %d times (expected 1)" % s.count("isMinifyEnabled = false"))
    return s.replace("isMinifyEnabled = false",
                     "// R8 code shrinking + obfuscation (Play Console: DEX optimization / obfuscation >= 25%).\n"
                     "            // Manifest components (services, activity) keep their names automatically.\n"
                     "            isMinifyEnabled = true", 1)
rw("app/build.gradle.kts", gradle)

def rules(s):
    if "renamesourcefileattribute" in s:
        return s
    return s.rstrip("\n") + """

# Readable crash stack traces in Play Console (mapping.txt is uploaded with the AAB automatically).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generic safety attributes (generics, annotations, nested classes).
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
"""
rw("app/proguard-rules.pro", rules)

# ---------------------------------------------------------------- Overlay: net profit + fuel (only if the user entered fuel data)
def vehicle_profile(s):
    if "userConfigured" in s:
        return s
    s = sub(s, "    val fuelPricePerUnit: Double\n) {",
            "    val fuelPricePerUnit: Double,\n"
            "    /** False only for [DEFAULT] (the user never saved a vehicle profile). */\n"
            "    val userConfigured: Boolean = true\n) {", "val userConfigured: Boolean = true")
    s = sub(s, "    companion object {\n",
            "    /**\n"
            "     * True only when the driver has actually entered fuel consumption AND fuel price. The built-in\n"
            "     * default profile does not count, so UI that shows fuel/net-profit lines stays hidden until then.\n"
            "     */\n"
            "    fun hasFuelCostData(): Boolean =\n"
            "        userConfigured && category != VehicleCategory.BICYCLE && consumptionPer100Km > 0.0 && fuelPricePerUnit > 0.0\n\n"
            "    companion object {\n", "fun hasFuelCostData()")
    s = sub(s, "            fuelPricePerUnit = 6.5\n        )",
            "            fuelPricePerUnit = 6.5,\n            userConfigured = false\n        )", "userConfigured = false")
    return s
rw("app/src/main/java/com/wayars/app/domain/model/VehicleProfile.kt", vehicle_profile)

def order_eval(s):
    return sub(s, "    val verdict: Verdict\n) {",
               "    val verdict: Verdict,\n"
               "    /** True when the driver entered fuel consumption and price, so fuel/net-profit lines may be shown. */\n"
               "    val fuelDataEntered: Boolean = false\n) {", "val fuelDataEntered: Boolean = false")
rw("app/src/main/java/com/wayars/app/domain/model/OrderEvaluation.kt", order_eval)

def use_case(s):
    return sub(s, "            ratePerMinute = ratePerMinute,\n            verdict = verdict\n        )",
               "            ratePerMinute = ratePerMinute,\n            verdict = verdict,\n"
               "            fuelDataEntered = vehicleProfile.hasFuelCostData()\n        )", "fuelDataEntered = vehicleProfile.hasFuelCostData()")
rw("app/src/main/java/com/wayars/app/domain/usecase/EvaluateOrderUseCase.kt", use_case)

def overlay_net(s):
    new_cond = "if (evaluation.fuelDataEntered && evaluation.fuelCost > 0 && evaluation.distanceKm >= OrderEvaluation.SHORT_TRIP_THRESHOLD_KM) {"
    old_cond = "if (evaluation.fuelCost > 0 && evaluation.distanceKm >= OrderEvaluation.SHORT_TRIP_THRESHOLD_KM) {"
    if new_cond in s:
        return s
    if old_cond in s:  # an earlier version of this script already added the block
        return s.replace(old_cond, new_cond, 1)
    anchor = ("            Spacer(Modifier.height(11.dp))\n"
              "            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n"
              "                Button(\n"
              "                    onClick = onAccept,")
    if s.count(anchor) != 1:
        raise SystemExit("overlay anchor found %d times (expected 1)" % s.count(anchor))
    block = ("            // Net profit after fuel. Shown only when the driver entered fuel consumption AND price in the\n"
             "            // vehicle settings, and the trip is long enough: under 1 km the badge above already shows\n"
             "            // the net profit.\n"
             "            " + new_cond + "\n"
             "                Spacer(Modifier.height(5.dp))\n"
             "                Text(\n"
             "                    stringResource(\n"
             "                        R.string.verdict_net_profit,\n"
             "                        CurrencyFormatter.format(evaluation.netProfit, evaluation.currency),\n"
             "                        CurrencyFormatter.format(evaluation.fuelCost, evaluation.currency)\n"
             "                    ),\n"
             "                    fontSize = 10.sp,\n"
             "                    color = WaTextSecondary\n"
             "                )\n"
             "            }\n\n")
    return s.replace(anchor, block + anchor, 1)
rw(BASE + "widget/OverlayContent.kt", overlay_net)

def verdict_card(s):
    if "evaluation.fuelDataEntered && evaluation.fuelCost > 0" in s:
        return s
    return sub(s, "        if (evaluation.fuelCost > 0) {", "        if (evaluation.fuelDataEntered && evaluation.fuelCost > 0) {",
               "evaluation.fuelDataEntered && evaluation.fuelCost > 0")
rw(BASE + "ui/component/VerdictCard.kt", verdict_card)

# ---------------------------------------------------------------- "Set up your vehicle" prompt (on Active)
def ds_prompt(s):
    s = add_import(s, "import androidx.datastore.preferences.core.intPreferencesKey")
    s = sub(s, '        val VEHICLE_FUEL_PRICE = doublePreferencesKey("vehicle_fuel_price_per_unit")\n',
            '        val VEHICLE_FUEL_PRICE = doublePreferencesKey("vehicle_fuel_price_per_unit")\n'
            '        val VEHICLE_PROMPT_COUNT = intPreferencesKey("vehicle_prompt_count")\n', "VEHICLE_PROMPT_COUNT = intPreferencesKey")
    s = sub(s, "    val vehicleProfile: Flow<VehicleProfile> = context.dataStore.data.map { prefs ->",
            "    /** How many times the \"set up your vehicle\" prompt has been shown (it is shown at most twice). */\n"
            "    val vehiclePromptCount: Flow<Int> = context.dataStore.data.map { it[Keys.VEHICLE_PROMPT_COUNT] ?: 0 }\n\n"
            "    suspend fun incrementVehiclePromptCount() {\n"
            "        context.dataStore.edit { it[Keys.VEHICLE_PROMPT_COUNT] = (it[Keys.VEHICLE_PROMPT_COUNT] ?: 0) + 1 }\n"
            "    }\n\n"
            "    val vehicleProfile: Flow<VehicleProfile> = context.dataStore.data.map { prefs ->", "val vehiclePromptCount: Flow<Int>")
    return s
rw("app/src/main/java/com/wayars/app/data/prefs/SettingsDataStore.kt", ds_prompt)

def repo_iface(s):
    s = sub(s, "    val vehicleProfile: Flow<VehicleProfile>\n",
            "    val vehicleProfile: Flow<VehicleProfile>\n    val vehiclePromptCount: Flow<Int>\n", "val vehiclePromptCount: Flow<Int>")
    s = sub(s, "    suspend fun setVehicleProfile(profile: VehicleProfile)\n",
            "    suspend fun setVehicleProfile(profile: VehicleProfile)\n    suspend fun incrementVehiclePromptCount()\n", "suspend fun incrementVehiclePromptCount()")
    return s
rw("app/src/main/java/com/wayars/app/domain/repository/SettingsRepository.kt", repo_iface)

def repo_impl(s):
    s = sub(s, "    override val vehicleProfile: Flow<VehicleProfile> = store.vehicleProfile\n",
            "    override val vehicleProfile: Flow<VehicleProfile> = store.vehicleProfile\n"
            "    override val vehiclePromptCount: Flow<Int> = store.vehiclePromptCount\n", "override val vehiclePromptCount")
    s = sub(s, "    override suspend fun setVehicleProfile(profile: VehicleProfile) = store.setVehicleProfile(profile)\n",
            "    override suspend fun setVehicleProfile(profile: VehicleProfile) = store.setVehicleProfile(profile)\n"
            "    override suspend fun incrementVehiclePromptCount() = store.incrementVehiclePromptCount()\n", "override suspend fun incrementVehiclePromptCount()")
    return s
rw("app/src/main/java/com/wayars/app/data/repository/SettingsRepositoryImpl.kt", repo_impl)

def vm_prompt(s):
    return sub(s, "        settings.vehicleProfile.stateIn(viewModelScope, SharingStarted.Eagerly, VehicleProfile.DEFAULT)\n",
               "        settings.vehicleProfile.stateIn(viewModelScope, SharingStarted.Eagerly, VehicleProfile.DEFAULT)\n\n"
               "    /** Times the vehicle prompt was shown. Starts at 2 (= do not prompt) until the stored value is loaded. */\n"
               "    val vehiclePromptCount: StateFlow<Int> =\n"
               "        settings.vehiclePromptCount.stateIn(viewModelScope, SharingStarted.Eagerly, 2)\n\n"
               "    fun noteVehiclePromptShown() = viewModelScope.launch { settings.incrementVehiclePromptCount() }\n",
               "val vehiclePromptCount: StateFlow<Int>")
rw(BASE + "MainViewModel.kt", vm_prompt)

def nav_prompt(s):
    s = sub(s, "    val vehicleProfile by viewModel.vehicleProfile.collectAsState()\n",
            "    val vehicleProfile by viewModel.vehicleProfile.collectAsState()\n"
            "    val vehiclePromptCount by viewModel.vehiclePromptCount.collectAsState()\n", "val vehiclePromptCount by viewModel")
    s = sub(s, "                    vehicleProfile = vehicleProfile,\n",
            "                    vehicleProfile = vehicleProfile,\n"
            "                    vehiclePromptCount = vehiclePromptCount,\n"
            "                    onVehiclePromptShown = { viewModel.noteVehiclePromptShown() },\n", "vehiclePromptCount = vehiclePromptCount,")
    return s
rw(BASE + "ui/navigation/WayArsNavHost.kt", nav_prompt)

def main_vehicle(s):
    s = sub(s, "    vehicleProfile: VehicleProfile,\n    onLanguageSelected: (String) -> Unit,",
            "    vehicleProfile: VehicleProfile,\n    vehiclePromptCount: Int,\n    onVehiclePromptShown: () -> Unit,\n    onLanguageSelected: (String) -> Unit,",
            "vehiclePromptCount: Int,")
    s = sub(s, "    var permissionsRequestId by remember { mutableStateOf(0) }\n",
            "    var permissionsRequestId by remember { mutableStateOf(0) }\n"
            "    var vehicleSetupPrompt by remember { mutableStateOf(false) }\n"
            "    var vehicleSetupRequestId by remember { mutableStateOf(0) }\n", "var vehicleSetupPrompt")
    s = sub(s, "    // When the tour ends, land on the Home tab again.\n",
            "    // \"Set up your vehicle\" from the Active prompt: the tab is switched by the caller; bring the\n"
            "    // vehicle card (item 4: title, subscription, general, thresholds, vehicle) into view.\n"
            "    LaunchedEffect(vehicleSetupRequestId) {\n"
            "        if (vehicleSetupRequestId > 0) {\n"
            "            delay(80L)\n"
            "            runCatching { settingsListState.animateScrollToItem(4) }\n"
            "        }\n"
            "    }\n\n"
            "    // When the tour ends, land on the Home tab again.\n", "LaunchedEffect(vehicleSetupRequestId)")
    s = sub(s, "                        showPermissionsPrompt = true\n                        permissionsRequestId++\n                    }\n                )\n",
            "                        showPermissionsPrompt = true\n                        permissionsRequestId++\n                    },\n"
            "                    vehicleConfigured = vehicleProfile.userConfigured,\n"
            "                    vehiclePromptCount = vehiclePromptCount,\n"
            "                    promptsEnabled = !tour.running,\n"
            "                    onVehiclePromptShown = onVehiclePromptShown,\n"
            "                    onVehicleSetupRequested = {\n"
            "                        tab = MainTab.SETTINGS\n"
            "                        vehicleSetupPrompt = true\n"
            "                        vehicleSetupRequestId++\n"
            "                    }\n"
            "                )\n", "onVehicleSetupRequested = {")
    s = sub(s, "                    onPermissionsPromptHandled = { showPermissionsPrompt = false }\n",
            "                    onPermissionsPromptHandled = { showPermissionsPrompt = false },\n"
            "                    vehiclePrompt = vehicleSetupPrompt,\n"
            "                    onVehiclePromptHandled = { vehicleSetupPrompt = false }\n", "vehiclePrompt = vehicleSetupPrompt")
    return s
rw(BASE + "ui/navigation/MainScreen.kt", main_vehicle)

def settings_vehicle(s):
    s = sub(s, "    permissionsPrompt: Boolean = false,\n    onPermissionsPromptHandled: () -> Unit = {}\n) {",
            "    permissionsPrompt: Boolean = false,\n    onPermissionsPromptHandled: () -> Unit = {},\n"
            "    vehiclePrompt: Boolean = false,\n    onVehiclePromptHandled: () -> Unit = {}\n) {", "vehiclePrompt: Boolean = false")
    s = sub(s, "                    currency = currency,\n                    onSave = onSaveVehicleProfile\n",
            "                    currency = currency,\n                    onSave = onSaveVehicleProfile,\n"
            "                    openRequest = vehiclePrompt,\n                    onRequestHandled = onVehiclePromptHandled\n", "openRequest = vehiclePrompt")
    s = sub(s, "    onSave: (VehicleProfile) -> Unit\n) {\n    val context = LocalContext.current\n    var expanded by remember { mutableStateOf(false) }\n",
            "    onSave: (VehicleProfile) -> Unit,\n    openRequest: Boolean = false,\n    onRequestHandled: () -> Unit = {}\n) {\n    val context = LocalContext.current\n    var expanded by remember { mutableStateOf(false) }\n"
            "    // Opened from the Dashboard \"set up your vehicle\" prompt: expand this card.\n"
            "    LaunchedEffect(openRequest) {\n"
            "        if (openRequest) {\n"
            "            expanded = true\n"
            "            onRequestHandled()\n"
            "        }\n"
            "    }\n", "// Opened from the Dashboard \"set up your vehicle\" prompt: expand this card.")
    return s
rw(BASE + "ui/screen/settings/SettingsScreen.kt", settings_vehicle)

def dash_vehicle(s):
    for imp in ("import androidx.compose.runtime.LaunchedEffect", "import androidx.compose.runtime.mutableStateOf",
                "import androidx.compose.runtime.setValue", "import androidx.compose.material3.Button",
                "import androidx.compose.material3.TextButton"):
        s = add_import(s, imp)
    s = sub(s, "    onPermissionsRequired: () -> Unit = {},\n    modifier: Modifier = Modifier\n) {",
            "    onPermissionsRequired: () -> Unit = {},\n"
            "    vehicleConfigured: Boolean = true,\n"
            "    vehiclePromptCount: Int = 2,\n"
            "    promptsEnabled: Boolean = true,\n"
            "    onVehiclePromptShown: () -> Unit = {},\n"
            "    onVehicleSetupRequested: () -> Unit = {},\n"
            "    modifier: Modifier = Modifier\n) {", "vehicleConfigured: Boolean = true")
    # state + launcher callback
    old_launcher = "    val notificationPermissionLauncher = rememberLauncherForActivityResult("
    if s.count(old_launcher) != 1:
        raise SystemExit("notification launcher not found")
    if "var notificationRequestInFlight" not in s:
        s = s.replace(old_launcher,
            "    // \"Set up your vehicle\" prompt state. The prompt must never overlap the Android notification-permission\n"
            "    // dialog, so while that request is open the prompt is only marked pending and shown once it is answered.\n"
            "    var showVehiclePrompt by remember { mutableStateOf(false) }\n"
            "    var vehiclePromptPending by remember { mutableStateOf(false) }\n"
            "    var notificationRequestInFlight by remember { mutableStateOf(false) }\n\n"
            + old_launcher, 1)
    i = s.find("    ) { /* proceed regardless of the result")
    if i >= 0 and "notificationRequestInFlight = false" not in s:
        j = s.index("*/ }\n", i) + len("*/ }\n")
        s = s[:i] + ("    ) {\n"
                     "        // Proceed regardless of the result (see activateScanning below).\n"
                     "        notificationRequestInFlight = false\n"
                     "        if (vehiclePromptPending) {\n"
                     "            vehiclePromptPending = false\n"
                     "            if (promptsEnabled) showVehiclePrompt = true\n"
                     "        }\n"
                     "    }\n") + s[j:]
    elif "notificationRequestInFlight = false" not in s:
        raise SystemExit("launcher callback not found")
    s = sub(s, "                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)\n",
            "                                notificationRequestInFlight = true\n"
            "                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)\n",
            "notificationRequestInFlight = true")
    # start scanning helper + prompt gate
    s = sub(s, "    fun activateScanning() {\n",
            "    fun startScanning() {\n"
            "        ScanningState.setActive(true, context)\n"
            "        context.startForegroundService(Intent(context, OverlayService::class.java))\n"
            "    }\n\n"
            "    fun activateScanning() {\n", "fun startScanning()")
    s = sub(s, "        if (overlayOk && accessibilityOk) {\n"
               "            ScanningState.setActive(true, context)\n"
               "            context.startForegroundService(Intent(context, OverlayService::class.java))\n"
               "        } else {\n",
            "        if (overlayOk && accessibilityOk) {\n"
            "            // Vehicle type and fuel use drive every calculation: if the user never saved a vehicle profile,\n"
            "            // offer to set it up first (at most twice in total). Not during the first-run tour.\n"
            "            if (promptsEnabled && !vehicleConfigured && vehiclePromptCount < 2) {\n"
            "                if (notificationRequestInFlight) vehiclePromptPending = true else showVehiclePrompt = true\n"
            "                return\n"
            "            }\n"
            "            startScanning()\n"
            "        } else {\n", "if (promptsEnabled && !vehicleConfigured && vehiclePromptCount < 2)")
    # dialog + tour guard
    s = sub(s, "    Column(\n        modifier = modifier\n            .fillMaxSize()\n            .verticalScroll(rememberScrollState())",
            "    // Never show the prompt on top of the first-run tour.\n"
            "    LaunchedEffect(promptsEnabled) {\n"
            "        if (!promptsEnabled) {\n"
            "            showVehiclePrompt = false\n"
            "            vehiclePromptPending = false\n"
            "        }\n"
            "    }\n"
            "    if (showVehiclePrompt) {\n"
            "        LaunchedEffect(Unit) { onVehiclePromptShown() }\n"
            "        androidx.compose.material3.AlertDialog(\n"
            "            // Back / tap outside = Skip: the user asked for scanning, so it still starts.\n"
            "            onDismissRequest = {\n"
            "                showVehiclePrompt = false\n"
            "                startScanning()\n"
            "            },\n"
            "            title = { Text(stringResource(R.string.vehicle_prompt_title)) },\n"
            "            text = { Text(stringResource(R.string.vehicle_prompt_body)) },\n"
            "            confirmButton = {\n"
            "                Button(onClick = {\n"
            "                    showVehiclePrompt = false\n"
            "                    onVehicleSetupRequested()\n"
            "                }) { Text(stringResource(R.string.vehicle_prompt_configure)) }\n"
            "            },\n"
            "            dismissButton = {\n"
            "                TextButton(onClick = {\n"
            "                    showVehiclePrompt = false\n"
            "                    startScanning()\n"
            "                }) { Text(stringResource(R.string.vehicle_prompt_skip)) }\n"
            "            }\n"
            "        )\n"
            "    }\n\n"
            "    Column(\n        modifier = modifier\n            .fillMaxSize()\n            .verticalScroll(rememberScrollState())",
            "R.string.vehicle_prompt_title")
    return s
rw(BASE + "ui/screen/dashboard/DashboardScreen.kt", dash_vehicle)

# strings for the prompt, all 17 languages
VP = {
 "values":    ("Set up your vehicle", "Your vehicle type and fuel use change how profitable an order really is. Set them once for accurate calculations.", "Set up", "Skip"),
 "values-ru": ("Настройте транспорт", "Вид транспорта и расход топлива влияют на то, насколько заказ действительно выгоден. Укажите их один раз, чтобы расчёты были точными.", "Настроить", "Пропустить"),
 "values-pl": ("Skonfiguruj pojazd", "Rodzaj pojazdu i zużycie paliwa decydują o tym, czy zlecenie naprawdę się opłaca. Podaj je raz, aby obliczenia były dokładne.", "Skonfiguruj", "Pomiń"),
 "values-uk": ("Налаштуйте транспорт", "Вид транспорту та витрата пального впливають на те, наскільки замовлення справді вигідне. Вкажіть їх один раз, щоб розрахунки були точними.", "Налаштувати", "Пропустити"),
 "values-de": ("Fahrzeug einrichten", "Fahrzeugtyp und Kraftstoffverbrauch bestimmen, wie lohnend ein Auftrag wirklich ist. Gib sie einmal an, damit die Berechnungen stimmen.", "Einrichten", "Überspringen"),
 "values-fr": ("Configurez votre véhicule", "Le type de véhicule et la consommation de carburant déterminent la rentabilité réelle d’une course. Indiquez-les une seule fois pour des calculs précis.", "Configurer", "Passer"),
 "values-cs": ("Nastavte vozidlo", "Typ vozidla a spotřeba paliva ovlivňují, jak výdělečná zakázka skutečně je. Zadejte je jednou, aby výpočty byly přesné.", "Nastavit", "Přeskočit"),
 "values-da": ("Opsæt dit køretøj", "Køretøjstype og brændstofforbrug afgør, hvor rentabel en ordre egentlig er. Angiv dem én gang, så beregningerne bliver præcise.", "Opsæt", "Spring over"),
 "values-nb": ("Sett opp kjøretøyet", "Kjøretøytype og drivstofforbruk avgjør hvor lønnsom en bestilling egentlig er. Angi dem én gang, så blir beregningene nøyaktige.", "Sett opp", "Hopp over"),
 "values-sv": ("Ställ in ditt fordon", "Fordonstyp och bränsleförbrukning avgör hur lönsam en order egentligen är. Ange dem en gång så blir beräkningarna korrekta.", "Ställ in", "Hoppa över"),
 "values-ro": ("Configurează vehiculul", "Tipul vehiculului și consumul de combustibil influențează cât de profitabilă este de fapt o comandă. Introdu-le o singură dată pentru calcule corecte.", "Configurează", "Omite"),
 "values-hu": ("Állítsd be a járművet", "A jármű típusa és az üzemanyag-fogyasztás határozza meg, mennyire kifizetődő valójában egy megrendelés. Add meg őket egyszer, hogy a számítások pontosak legyenek.", "Beállítás", "Kihagyás"),
 "values-bg": ("Настройте превозното средство", "Видът превозно средство и разходът на гориво определят колко изгодна е всъщност една поръчка. Задайте ги веднъж, за да са изчисленията точни.", "Настрой", "Пропусни"),
 "values-az": ("Nəqliyyat vasitəsini ayarlayın", "Nəqliyyat vasitəsinin növü və yanacaq sərfi sifarişin həqiqətən nə qədər sərfəli olduğunu müəyyən edir. Hesablamaların dəqiq olması üçün onları bir dəfə daxil edin.", "Ayarla", "Keç"),
 "values-uz": ("Transport vositasini sozlang", "Transport turi va yoqilg‘i sarfi buyurtma aslida qanchalik foydali ekanini belgilaydi. Hisob-kitoblar aniq bo‘lishi uchun ularni bir marta kiriting.", "Sozlash", "O‘tkazib yuborish"),
 "values-ka": ("დააყენეთ ტრანსპორტი", "ტრანსპორტის ტიპი და საწვავის ხარჯი განსაზღვრავს, რამდენად მომგებიანია შეკვეთა რეალურად. მიუთითეთ ისინი ერთხელ, რომ გათვლები იყოს ზუსტი.", "დაყენება", "გამოტოვება"),
 "values-hy": ("Կարգավորեք փոխադրամիջոցը", "Փոխադրամիջոցի տեսակը և վառելիքի ծախսը որոշում են, թե իրականում որքան եկամտաբեր է պատվերը։ Նշեք դրանք մեկ անգամ՝ ճշգրիտ հաշվարկների համար։", "Կարգավորել", "Բաց թողնել"),
}
for d, (t, b, c, k) in VP.items():
    def f(s, t=t, b=b, c=c, k=k):
        if "vehicle_prompt_title" in s:
            return s
        add = ('    <string name="vehicle_prompt_title">%s</string>\n'
               '    <string name="vehicle_prompt_body">%s</string>\n'
               '    <string name="vehicle_prompt_configure">%s</string>\n'
               '    <string name="vehicle_prompt_skip">%s</string>\n') % (t, b, c, k)
        if "</resources>" not in s:
            raise SystemExit("no </resources> in " + d)
        return s.replace("</resources>", add + "</resources>", 1)
    rw("app/src/main/res/%s/strings.xml" % d, f)

# ---------------------------------------------------------------- Statistics: periods + chart
STATS_SRC = r'''package com.wayars.app.presentation.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.StatsPeriod
import com.wayars.app.domain.repository.OrderRecord
import com.wayars.app.presentation.ui.component.StatCard
import com.wayars.app.presentation.ui.component.bottomNavBarClearance
import com.wayars.app.presentation.ui.component.verdictColor
import com.wayars.app.presentation.ui.component.verdictLabel
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaNeonGreenDark
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Earnings statistics. [orders] are every accepted order ever recorded (newest first); the screen
 * filters them by [period] (today / 7 days / month / year), shows totals and a bar chart, and lists
 * the most recent orders of that period. The chosen period is persisted by the caller.
 */
@Composable
fun StatsScreen(
    orders: List<OrderRecord>,
    currency: Currency,
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = remember(orders, period) { buildPeriodStats(orders, period) }
    val timeFormat = remember(period) {
        SimpleDateFormat(if (period == StatsPeriod.TODAY) "HH:mm" else "dd.MM HH:mm", Locale.getDefault())
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        // See bottomNavBarClearance's doc: device-accurate clearance for
        // the floating nav pill, instead of a fixed guess.
        contentPadding = PaddingValues(bottom = bottomNavBarClearance())
    ) {
        item { PeriodSelector(period, onPeriodChange) }

        if (stats.orders.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.stats_empty),
                    color = WaTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
                )
            }
        } else {
            item { SummaryBlock(stats, currency) }
            item { EarningsChart(stats, currency) }
            items(stats.orders.take(50)) { order ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(WaSurface)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            CurrencyFormatter.format(order.evaluation.earnings, order.evaluation.currency),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${order.evaluation.distanceKm} km • ${timeFormat.format(Date(order.timestampMillis))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WaTextSecondary
                        )
                    }
                    Text(
                        verdictLabel(order.evaluation.verdict),
                        color = verdictColor(order.evaluation.verdict),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun periodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.TODAY -> R.string.stats_period_today
        StatsPeriod.WEEK -> R.string.stats_period_week
        StatsPeriod.MONTH -> R.string.stats_period_month
        StatsPeriod.YEAR -> R.string.stats_period_year
    }
)

@Composable
private fun PeriodSelector(period: StatsPeriod, onChange: (StatsPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StatsPeriod.values().forEach { p ->
            val selected = p == period
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) WaNeonGreen else WaSurface)
                    .clickable { onChange(p) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    periodLabel(p),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (selected) Color.Black else WaTextSecondary
                )
            }
        }
    }
}

@Composable
private fun SummaryBlock(stats: PeriodStats, currency: Currency) {
    val total = stats.orders.sumOf { it.evaluation.earnings }
    val km = stats.orders.sumOf { it.evaluation.distanceKm }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                label = stringResource(R.string.stats_earned),
                value = CurrencyFormatter.format(total, currency),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = stringResource(R.string.dashboard_orders),
                value = stats.orders.size.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                label = stringResource(R.string.dashboard_distance),
                value = String.format(Locale.US, "%.1f km", km),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = stringResource(R.string.dashboard_avg_rate),
                value = CurrencyFormatter.formatRatePerKm(if (km > 0) total / km else 0.0, currency),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Bar chart of earnings per hour (today) / day (7 days, month) / month (year). Tap a bar to read its value. */
@Composable
private fun EarningsChart(stats: PeriodStats, currency: Currency) {
    var selected by remember(stats.period) { mutableStateOf(-1) }
    val max = stats.buckets.maxOrNull() ?: 0.0
    val bestIndex = stats.buckets.indexOf(max).coerceAtLeast(0)
    val shown = if (selected in stats.buckets.indices) selected else bestIndex
    val gap = if (stats.buckets.size > 14) 2.dp else 6.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(14.dp)
    ) {
        Text(
            "${stats.captions[shown]}: ${CurrencyFormatter.format(stats.buckets[shown], currency)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom
        ) {
            stats.buckets.forEachIndexed { i, value ->
                val fraction = if (max > 0) (value / max).toFloat() else 0f
                val barColor = when {
                    value <= 0.0 -> WaSurfaceVariant
                    i == shown -> WaNeonGreen
                    else -> WaNeonGreenDark.copy(alpha = 0.7f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { selected = i },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (value > 0.0) Modifier.fillMaxHeight(fraction.coerceIn(0.04f, 1f))
                                else Modifier.height(2.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            stats.labels.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 10.sp,
                        color = WaTextSecondary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(unbounded = true)
                    )
                }
            }
        }
    }
}

private class PeriodStats(
    val period: StatsPeriod,
    val orders: List<OrderRecord>,
    val buckets: List<Double>,
    val labels: List<String>,
    val captions: List<String>
)

private fun buildPeriodStats(all: List<OrderRecord>, period: StatsPeriod): PeriodStats {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        when (period) {
            StatsPeriod.TODAY -> Unit
            StatsPeriod.WEEK -> add(Calendar.DAY_OF_YEAR, -6)
            StatsPeriod.MONTH -> add(Calendar.DAY_OF_YEAR, -29)
            StatsPeriod.YEAR -> {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -11)
            }
        }
    }
    val count = when (period) {
        StatsPeriod.TODAY -> 24
        StatsPeriod.WEEK -> 7
        StatsPeriod.MONTH -> 30
        StatsPeriod.YEAR -> 12
    }
    val inPeriod = all.filter { it.timestampMillis >= start.timeInMillis }

    val sums = DoubleArray(count)
    val cal = Calendar.getInstance()
    for (order in inPeriod) {
        cal.timeInMillis = order.timestampMillis
        val index = when (period) {
            StatsPeriod.TODAY -> cal.get(Calendar.HOUR_OF_DAY)
            StatsPeriod.WEEK, StatsPeriod.MONTH -> {
                val dayStart = Calendar.getInstance().apply {
                    timeInMillis = order.timestampMillis
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Math.round((dayStart - start.timeInMillis) / 86_400_000.0).toInt()
            }
            StatsPeriod.YEAR ->
                (cal.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12 + cal.get(Calendar.MONTH) - start.get(Calendar.MONTH)
        }
        sums[index.coerceIn(0, count - 1)] += order.evaluation.earnings
    }

    val locale = Locale.getDefault()
    val dowShort = SimpleDateFormat("EEE", locale)
    val dowCaption = SimpleDateFormat("EEE, d MMM", locale)
    val dayNumber = SimpleDateFormat("d", locale)
    val dayCaption = SimpleDateFormat("d MMM", locale)
    val monthShort = SimpleDateFormat("LLL", locale)
    val monthCaption = SimpleDateFormat("LLLL yyyy", locale)

    val labels = ArrayList<String>(count)
    val captions = ArrayList<String>(count)
    val cursor = start.clone() as Calendar
    for (i in 0 until count) {
        val date = cursor.time
        when (period) {
            StatsPeriod.TODAY -> {
                labels.add(if (i % 6 == 0) i.toString() else "")
                captions.add(String.format(Locale.US, "%02d:00", i))
                cursor.add(Calendar.HOUR_OF_DAY, 1)
            }
            StatsPeriod.WEEK -> {
                labels.add(dowShort.format(date).take(3).replaceFirstChar { it.uppercase() })
                captions.add(dowCaption.format(date))
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }
            StatsPeriod.MONTH -> {
                labels.add(if (i % 5 == 0 || i == count - 1) dayNumber.format(date) else "")
                captions.add(dayCaption.format(date))
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }
            StatsPeriod.YEAR -> {
                labels.add(monthShort.format(date).take(3).replaceFirstChar { it.uppercase() })
                captions.add(monthCaption.format(date))
                cursor.add(Calendar.MONTH, 1)
            }
        }
    }
    return PeriodStats(period, inPeriod, sums.toList(), labels, captions)
}
'''

def new_file(path, content):
    import os
    if os.path.exists(path) and io.open(path, encoding="utf-8").read() == content:
        print("already OK", path); return
    if os.path.exists(path):
        print("updated", path)
    else:
        print("created", path)
    io.open(path, "w", encoding="utf-8").write(content)

new_file("app/src/main/java/com/wayars/app/domain/model/StatsPeriod.kt",
         "package com.wayars.app.domain.model\n\n/** Time window shown on the Statistics screen; the user's choice is persisted. */\nenum class StatsPeriod { TODAY, WEEK, MONTH, YEAR }\n")

def stats_screen_file(s):
    if "period: StatsPeriod" in s:
        return s
    if "fun StatsScreen(orders: List<OrderRecord>, modifier: Modifier = Modifier)" not in s:
        raise SystemExit("StatsScreen.kt has an unexpected shape")
    return STATS_SRC
rw(BASE + "ui/screen/dashboard/StatsScreen.kt", stats_screen_file)

def ds_stats(s):
    s = add_import(s, "import com.wayars.app.domain.model.StatsPeriod")
    s = sub(s, '        val PRESET = stringPreferencesKey("preset")\n',
            '        val PRESET = stringPreferencesKey("preset")\n        val STATS_PERIOD = stringPreferencesKey("stats_period")\n', "STATS_PERIOD = stringPreferencesKey")
    s = sub(s, "    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }\n",
            "    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }\n\n"
            "    /** Period chosen on the Statistics screen (default: today). */\n"
            "    val statsPeriod: Flow<StatsPeriod> = context.dataStore.data.map {\n"
            "        it[Keys.STATS_PERIOD]?.let { name -> runCatching { StatsPeriod.valueOf(name) }.getOrNull() } ?: StatsPeriod.TODAY\n"
            "    }\n\n"
            "    suspend fun setStatsPeriod(period: StatsPeriod) {\n"
            "        context.dataStore.edit { it[Keys.STATS_PERIOD] = period.name }\n"
            "    }\n", "val statsPeriod: Flow<StatsPeriod>")
    return s
rw("app/src/main/java/com/wayars/app/data/prefs/SettingsDataStore.kt", ds_stats)

def repo_stats_iface(s):
    s = add_import(s, "import com.wayars.app.domain.model.StatsPeriod")
    s = sub(s, "    val vehiclePromptCount: Flow<Int>\n",
            "    val vehiclePromptCount: Flow<Int>\n    val statsPeriod: Flow<StatsPeriod>\n", "val statsPeriod: Flow<StatsPeriod>")
    s = sub(s, "    suspend fun incrementVehiclePromptCount()\n",
            "    suspend fun incrementVehiclePromptCount()\n    suspend fun setStatsPeriod(period: StatsPeriod)\n", "suspend fun setStatsPeriod(period: StatsPeriod)")
    return s
rw("app/src/main/java/com/wayars/app/domain/repository/SettingsRepository.kt", repo_stats_iface)

def repo_stats_impl(s):
    s = add_import(s, "import com.wayars.app.domain.model.StatsPeriod")
    s = sub(s, "    override val vehiclePromptCount: Flow<Int> = store.vehiclePromptCount\n",
            "    override val vehiclePromptCount: Flow<Int> = store.vehiclePromptCount\n"
            "    override val statsPeriod: Flow<StatsPeriod> = store.statsPeriod\n", "override val statsPeriod")
    s = sub(s, "    override suspend fun incrementVehiclePromptCount() = store.incrementVehiclePromptCount()\n",
            "    override suspend fun incrementVehiclePromptCount() = store.incrementVehiclePromptCount()\n"
            "    override suspend fun setStatsPeriod(period: StatsPeriod) = store.setStatsPeriod(period)\n", "override suspend fun setStatsPeriod")
    return s
rw("app/src/main/java/com/wayars/app/data/repository/SettingsRepositoryImpl.kt", repo_stats_impl)

def vm_stats(s):
    s = add_import(s, "import com.wayars.app.domain.model.StatsPeriod")
    s = sub(s, "    fun noteVehiclePromptShown() = viewModelScope.launch { settings.incrementVehiclePromptCount() }\n",
            "    fun noteVehiclePromptShown() = viewModelScope.launch { settings.incrementVehiclePromptCount() }\n\n"
            "    /** Every accepted order ever recorded (newest first); the Statistics screen filters it by period. */\n"
            "    val allOrders: StateFlow<List<OrderRecord>> =\n"
            "        container.orderRepository.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())\n\n"
            "    val statsPeriod: StateFlow<StatsPeriod> =\n"
            "        settings.statsPeriod.stateIn(viewModelScope, SharingStarted.Eagerly, StatsPeriod.TODAY)\n\n"
            "    fun setStatsPeriod(period: StatsPeriod) = viewModelScope.launch { settings.setStatsPeriod(period) }\n",
            "val allOrders: StateFlow<List<OrderRecord>>")
    return s
rw(BASE + "MainViewModel.kt", vm_stats)

def nav_stats(s):
    s = sub(s, "    val todayOrders by viewModel.todayOrders.collectAsState()\n",
            "    val todayOrders by viewModel.todayOrders.collectAsState()\n"
            "    val allOrders by viewModel.allOrders.collectAsState()\n"
            "    val statsPeriod by viewModel.statsPeriod.collectAsState()\n", "val allOrders by viewModel.allOrders")
    s = sub(s, "                    todayOrders = todayOrders,\n",
            "                    todayOrders = todayOrders,\n"
            "                    allOrders = allOrders,\n"
            "                    statsPeriod = statsPeriod,\n"
            "                    onStatsPeriodChange = { viewModel.setStatsPeriod(it) },\n", "onStatsPeriodChange = { viewModel.setStatsPeriod(it) }")
    return s
rw(BASE + "ui/navigation/WayArsNavHost.kt", nav_stats)

def main_stats(s):
    s = add_import(s, "import com.wayars.app.domain.model.StatsPeriod")
    s = sub(s, "    todayOrders: List<OrderRecord>,\n",
            "    todayOrders: List<OrderRecord>,\n    allOrders: List<OrderRecord>,\n    statsPeriod: StatsPeriod,\n    onStatsPeriodChange: (StatsPeriod) -> Unit,\n",
            "onStatsPeriodChange: (StatsPeriod) -> Unit,")
    s = sub(s, "                MainTab.STATS -> StatsScreen(orders = todayOrders)\n",
            "                MainTab.STATS -> StatsScreen(\n"
            "                    orders = allOrders,\n"
            "                    currency = currency,\n"
            "                    period = statsPeriod,\n"
            "                    onPeriodChange = onStatsPeriodChange\n"
            "                )\n", "onPeriodChange = onStatsPeriodChange")
    return s
rw(BASE + "ui/navigation/MainScreen.kt", main_stats)

SP = {
 "values":    ("Today", "7 days", "Month", "Year", "Earned"),
 "values-ru": ("Сегодня", "7 дней", "Месяц", "Год", "Заработано"),
 "values-pl": ("Dziś", "7 dni", "Miesiąc", "Rok", "Zarobiono"),
 "values-uk": ("Сьогодні", "7 днів", "Місяць", "Рік", "Зароблено"),
 "values-de": ("Heute", "7 Tage", "Monat", "Jahr", "Verdient"),
 "values-fr": ("Aujourd’hui", "7 jours", "Mois", "Année", "Gagné"),
 "values-cs": ("Dnes", "7 dní", "Měsíc", "Rok", "Vyděláno"),
 "values-da": ("I dag", "7 dage", "Måned", "År", "Tjent"),
 "values-nb": ("I dag", "7 dager", "Måned", "År", "Tjent"),
 "values-sv": ("Idag", "7 dagar", "Månad", "År", "Intjänat"),
 "values-ro": ("Azi", "7 zile", "Lună", "An", "Câștigat"),
 "values-hu": ("Ma", "7 nap", "Hónap", "Év", "Kereset"),
 "values-bg": ("Днес", "7 дни", "Месец", "Година", "Спечелено"),
 "values-az": ("Bu gün", "7 gün", "Ay", "İl", "Qazanc"),
 "values-uz": ("Bugun", "7 kun", "Oy", "Yil", "Daromad"),
 "values-ka": ("დღეს", "7 დღე", "თვე", "წელი", "შემოსავალი"),
 "values-hy": ("Այսօր", "7 օր", "Ամիս", "Տարի", "Եկամուտ"),
}
for d, (a, b, c, e, f_) in SP.items():
    def g(s, a=a, b=b, c=c, e=e, f_=f_):
        if "stats_period_today" in s:
            return s
        if "</resources>" not in s:
            raise SystemExit("no </resources> in " + d)
        add = ('    <string name="stats_period_today">%s</string>\n'
               '    <string name="stats_period_week">%s</string>\n'
               '    <string name="stats_period_month">%s</string>\n'
               '    <string name="stats_period_year">%s</string>\n'
               '    <string name="stats_earned">%s</string>\n') % (a, b, c, e, f_)
        return s.replace("</resources>", add + "</resources>", 1)
    rw("app/src/main/res/%s/strings.xml" % d, g)

# ---------------------------------------------------------------- Overlay: verdict-coloured glow around the contour
def overlay_glow(s):
    if "fun Modifier.verdictGlow" in s:
        return s
    for imp in ("import androidx.compose.animation.animateColorAsState",
                "import androidx.compose.foundation.border",
                "import androidx.compose.ui.draw.drawBehind",
                "import androidx.compose.ui.geometry.CornerRadius",
                "import androidx.compose.ui.geometry.Offset",
                "import androidx.compose.ui.geometry.Size",
                "import androidx.compose.ui.unit.Dp"):
        s = add_import(s, imp)
    helper = (
        "/** Transparent margin around the card that hosts the glow (the overlay window wraps its content). */\n"
        "private val OVERLAY_GLOW_PADDING = 12.dp\n\n"
        "/**\n"
        " * Soft neon glow around the card contour: a stack of growing, translucent rounded rectangles drawn\n"
        " * BEHIND the card (the card background then covers the inside). Works on every API level, unlike\n"
        " * blur/shadow layers. [strength] 0..1 fades it in and out.\n"
        " */\n"
        "private fun Modifier.verdictGlow(color: Color, cornerRadius: Dp, glow: Dp, strength: Float): Modifier =\n"
        "    if (strength <= 0.01f) this else drawBehind {\n"
        "        val radius = cornerRadius.toPx()\n"
        "        val reach = glow.toPx()\n"
        "        val layers = 8\n"
        "        for (i in layers downTo 1) {\n"
        "            val expand = reach * i / layers\n"
        "            drawRoundRect(\n"
        "                color = color.copy(alpha = 0.075f * strength),\n"
        "                topLeft = Offset(-expand, -expand),\n"
        "                size = Size(size.width + 2 * expand, size.height + 2 * expand),\n"
        "                cornerRadius = CornerRadius(radius + expand)\n"
        "            )\n"
        "        }\n"
        "    }\n\n")
    anchor = "@Composable\nfun OverlayContent("
    if s.count(anchor) != 1:
        raise SystemExit("OverlayContent anchor found %d times" % s.count(anchor))
    s = s.replace(anchor, helper + anchor, 1)
    old = ("    Column(\n"
           "        modifier = Modifier\n"
           "            .width(212.dp)\n"
           "            .clip(RoundedCornerShape(16.dp))\n"
           "            .background(WaSurface)\n"
           "            .padding(12.dp)\n"
           "    ) {\n")
    if s.count(old) != 1:
        raise SystemExit("overlay card modifier found %d times" % s.count(old))
    new_card = (
        "    // The card outline glows in the verdict colour: green = good, amber = average, red = bad.\n"
        "    val verdictTint = evaluation?.let { verdictColor(it.verdict) }\n"
        "    val glowColor by animateColorAsState(\n"
        "        targetValue = verdictTint ?: WaSurfaceVariant,\n"
        "        animationSpec = tween(durationMillis = 250),\n"
        "        label = \"overlay_glow_color\"\n"
        "    )\n"
        "    val glowStrength by animateFloatAsState(\n"
        "        targetValue = if (verdictTint != null) 1f else 0f,\n"
        "        animationSpec = tween(durationMillis = 250),\n"
        "        label = \"overlay_glow_strength\"\n"
        "    )\n"
        "    Column(\n"
        "        modifier = Modifier\n"
        "            .padding(OVERLAY_GLOW_PADDING)\n"
        "            .width(212.dp)\n"
        "            .verdictGlow(glowColor, 16.dp, OVERLAY_GLOW_PADDING, glowStrength)\n"
        "            .clip(RoundedCornerShape(16.dp))\n"
        "            .background(WaSurface)\n"
        "            .border(1.5.dp, glowColor.copy(alpha = 0.35f + 0.65f * glowStrength), RoundedCornerShape(16.dp))\n"
        "            .padding(12.dp)\n"
        "    ) {\n")
    return s.replace(old, new_card, 1)
rw(BASE + "widget/OverlayContent.kt", overlay_glow)

# ---------------------------------------------------------------- Overlay glow: smooth pulsing
def overlay_pulse(s):
    if "overlay_glow_pulse" in s:
        return s
    for imp in ("import androidx.compose.animation.core.FastOutSlowInEasing",
                "import androidx.compose.animation.core.animateFloat",
                "import androidx.compose.animation.core.RepeatMode",
                "import androidx.compose.animation.core.infiniteRepeatable",
                "import androidx.compose.animation.core.rememberInfiniteTransition"):
        s = add_import(s, imp)
    s = sub(s, "private fun Modifier.verdictGlow(color: Color, cornerRadius: Dp, glow: Dp, strength: Float): Modifier =",
            "private fun Modifier.verdictGlow(color: Color, cornerRadius: Dp, glow: Dp, strength: Float, spread: Float): Modifier =",
            "strength: Float, spread: Float")
    s = sub(s, "        val reach = glow.toPx()\n", "        val reach = glow.toPx() * spread\n", "glow.toPx() * spread")
    s = sub(s, "color.copy(alpha = 0.075f * strength)", "color.copy(alpha = 0.085f * strength)", "0.085f * strength")
    s = sub(s, "    onDragBy: (dx: Float, dy: Float) -> Unit\n) {\n    // The card outline glows",
            "    onDragBy: (dx: Float, dy: Float) -> Unit,\n    reducedMotion: Boolean = false\n) {\n    // The card outline glows",
            "reducedMotion: Boolean = false")
    s = sub(s, '        label = "overlay_glow_strength"\n    )\n',
            '        label = "overlay_glow_strength"\n    )\n'
            "    // Slow \"breathing\" of the glow (brightness and reach). Steady when system animations are off.\n"
            "    val pulseTransition = rememberInfiniteTransition(label = \"overlay_glow_pulse\")\n"
            "    val pulseAnimated by pulseTransition.animateFloat(\n"
            "        initialValue = 0.35f,\n"
            "        targetValue = 1f,\n"
            "        animationSpec = infiniteRepeatable(\n"
            "            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),\n"
            "            repeatMode = RepeatMode.Reverse\n"
            "        ),\n"
            "        label = \"overlay_glow_pulse_value\"\n"
            "    )\n"
            "    val pulse = if (reducedMotion) 1f else pulseAnimated\n", "overlay_glow_pulse_value")
    s = sub(s, ".verdictGlow(glowColor, 16.dp, OVERLAY_GLOW_PADDING, glowStrength)",
            ".verdictGlow(glowColor, 16.dp, OVERLAY_GLOW_PADDING, glowStrength * pulse, 0.7f + 0.3f * pulse)",
            "glowStrength * pulse, 0.7f + 0.3f * pulse")
    s = sub(s, "glowColor.copy(alpha = 0.35f + 0.65f * glowStrength)",
            "glowColor.copy(alpha = (0.35f + 0.65f * glowStrength) * (0.7f + 0.3f * pulse))",
            "(0.35f + 0.65f * glowStrength) * (0.7f + 0.3f * pulse)")
    return s
rw(BASE + "widget/OverlayContent.kt", overlay_pulse)

def service_motion(s):
    return sub(s, "                            onDragBy = ::moveWindowBy\n                        )",
               "                            onDragBy = ::moveWindowBy,\n                            reducedMotion = reducedMotion\n                        )",
               "onDragBy = ::moveWindowBy,\n                            reducedMotion = reducedMotion")
rw(BASE.replace("presentation/", "") + "service/overlay/OverlayService.kt", service_motion)

# ---------------------------------------------------------------- Verdict fix: unknown trip time
def verdict_fix(s):
    # upgrade path: an earlier version of this script kept the strict rule for trips under 1 km
    old_line = "            val judgeByKmOnly = minutesUnknown && distanceKm >= OrderEvaluation.SHORT_TRIP_THRESHOLD_KM\n"
    if old_line in s:
        s = s.replace(old_line, "            val judgeByKmOnly = minutesUnknown\n", 1)
        s = s.replace("            // per km. For a real trip (>= SHORT_TRIP_THRESHOLD_KM) with unknown time we therefore judge on net\n"
                      "            // EUR/km only; very short trips with unknown time keep the strict rule (a tiny distance inflates EUR/km).\n",
                      "            // per km. When the time is unknown we therefore judge on net EUR/km only (any distance).\n", 1)
    if "val perMinuteOk" in s:
        return s
    s = sub(s, "            val clearsPresetBar = ratePerKm >= preset.minRatePerKm(currency) && ratePerMinute >= preset.minRatePerMinute(currency)\n",
            "            // Some source apps / screens (e.g. Stuart, some Uber screens) show only the distance, so timeMinutes\n"
            "            // arrives as 0 and EUR/minute is unknown (0.0). Requiring it would mark EVERY such order BAD however\n"
            "            // well it pays per km. When the time is unknown we therefore judge on net EUR/km only (any distance).\n"
            "            val minutesUnknown = timeMinutes <= 0.0\n"
            "            val judgeByKmOnly = minutesUnknown\n"
            "            val perMinuteOk = judgeByKmOnly || ratePerMinute >= preset.minRatePerMinute(currency)\n"
            "            val clearsPresetBar = ratePerKm >= preset.minRatePerKm(currency) && perMinuteOk\n", "val perMinuteOk")
    s = sub(s, "            if (ratePerKm >= absoluteGoodRate && ratePerMinute >= preset.minRatePerMinute(currency)) {\n",
            "            if (ratePerKm >= absoluteGoodRate && perMinuteOk) {\n", "absoluteGoodRate && perMinuteOk")
    return s
rw("app/src/main/java/com/wayars/app/domain/usecase/EvaluateOrderUseCase.kt", verdict_fix)

def no_zero_minutes(s):
    old = '"${fmt(evaluation.distanceKm)} km • ${fmt(evaluation.timeMinutes)} min",'
    new = ('if (evaluation.timeMinutes > 0) "${fmt(evaluation.distanceKm)} km • ${fmt(evaluation.timeMinutes)} min"\n'
           '                else "${fmt(evaluation.distanceKm)} km",')
    if "if (evaluation.timeMinutes > 0)" in s:
        return s
    if s.count(old) != 1:
        raise SystemExit("distance/time text found %d times" % s.count(old))
    return s.replace(old, new, 1)
rw(BASE + "widget/OverlayContent.kt", no_zero_minutes)
rw(BASE + "ui/component/VerdictCard.kt", no_zero_minutes)

CONSENT = {
 "values": [
  "Accessibility Service permission",
  "WayArs uses Android’s AccessibilityService API. It is needed for the app’s core feature: automatically reading order offers on the screen of the delivery and ride apps you use, so WayArs can calculate your net profit and show a verdict (good / average / bad) in a floating overlay while scanning is on.\\n\\nHow it is used:\\n• It reads the text shown by the supported apps (and any apps you add yourself) when an order offer appears: price, distance and estimated time.\\n• Passwords, messages and anything you type are never read. Everything shown by other apps is ignored immediately.\\n• It never taps, types, scrolls or changes anything on screen, and it is not used to record calls.\\n\\nWhat happens with the data:\\n• It is processed only on this device to calculate profit. Nothing is sent to a server, shared, sold or used for advertising.\\n• For orders you accept, only summary figures (price, distance, time, profit) are saved on this device for your statistics.\\n\\nYou stay in control: the service works only after you turn it on in Android settings, and you can turn it off at any time. If you also enable the optional notification access, WayArs reads only the notifications of supported apps about new orders.",
  "I agree and continue"
 ],
 "values-ru": [
  "Разрешение службы специальных возможностей",
  "WayArs использует Android AccessibilityService API. Он нужен для основной функции приложения: автоматически считывать предложения заказов с экрана приложений доставки и такси, которыми вы пользуетесь, чтобы WayArs рассчитал вашу чистую прибыль и показал вердикт (хороший / приемлемый / плохой) в плавающем окне, пока включено сканирование.\\n\\nКак это используется:\\n• Считывается текст, который показывают поддерживаемые приложения (и приложения, добавленные вами), когда появляется предложение заказа: цена, расстояние и примерное время.\\n• Пароли, сообщения и то, что вы вводите, не считываются никогда. Всё, что показывают другие приложения, сразу игнорируется.\\n• WayArs никогда ничего не нажимает, не вводит, не прокручивает и не меняет на экране, и не используется для записи звонков.\\n\\nЧто происходит с данными:\\n• Они обрабатываются только на этом устройстве для расчёта прибыли. Ничего не отправляется на сервер, не передаётся другим, не продаётся и не используется для рекламы.\\n• По принятым заказам на устройстве сохраняются только итоговые цифры (цена, расстояние, время, прибыль) для вашей статистики.\\n\\nВы управляете этим сами: служба работает только после того, как вы включите её в настройках Android, и вы можете отключить её в любой момент. Если вы дополнительно включите необязательный доступ к уведомлениям, WayArs читает только уведомления поддерживаемых приложений о новых заказах.",
  "Согласен, продолжить"
 ],
 "values-pl": [
  "Zgoda na usługę dostępności",
  "WayArs korzysta z Android AccessibilityService API. Jest ono potrzebne do głównej funkcji aplikacji: automatycznego odczytywania propozycji zleceń z ekranu aplikacji do dostaw i przewozów, z których korzystasz, aby WayArs obliczył Twój zysk netto i pokazał werdykt (dobry / akceptowalny / zły) w pływającym oknie, gdy skanowanie jest włączone.\\n\\nJak jest używane:\\n• Odczytywany jest tekst wyświetlany przez obsługiwane aplikacje (oraz te, które sam dodasz), gdy pojawia się propozycja zlecenia: cena, odległość i szacowany czas.\\n• Hasła, wiadomości ani to, co wpisujesz, nigdy nie są odczytywane. Wszystko, co pokazują inne aplikacje, jest natychmiast ignorowane.\\n• WayArs nigdy niczego nie klika, nie wpisuje, nie przewija ani nie zmienia na ekranie i nie służy do nagrywania rozmów.\\n\\nCo dzieje się z danymi:\\n• Są przetwarzane wyłącznie na tym urządzeniu, aby obliczyć zysk. Nic nie jest wysyłane na serwer, udostępniane, sprzedawane ani używane do reklam.\\n• Dla przyjętych zleceń na urządzeniu zapisywane są tylko zbiorcze liczby (cena, odległość, czas, zysk) na potrzeby Twoich statystyk.\\n\\nTo Ty decydujesz: usługa działa dopiero po włączeniu jej w ustawieniach Androida i możesz ją wyłączyć w dowolnej chwili. Jeśli dodatkowo włączysz opcjonalny dostęp do powiadomień, WayArs odczytuje tylko powiadomienia obsługiwanych aplikacji o nowych zleceniach.",
  "Zgadzam się, kontynuuj"
 ],
 "values-uk": [
  "Дозвіл служби спеціальних можливостей",
  "WayArs використовує Android AccessibilityService API. Він потрібен для основної функції застосунку: автоматично зчитувати пропозиції замовлень з екрана застосунків доставки й таксі, якими ви користуєтесь, щоб WayArs розрахував ваш чистий прибуток і показав вердикт (хороший / прийнятний / поганий) у плаваючому вікні, поки ввімкнено сканування.\\n\\nЯк це використовується:\\n• Зчитується текст, який показують підтримувані застосунки (і ті, що ви додали самі), коли з’являється пропозиція замовлення: ціна, відстань і орієнтовний час.\\n• Паролі, повідомлення та те, що ви вводите, не зчитуються ніколи. Усе, що показують інші застосунки, одразу ігнорується.\\n• WayArs ніколи нічого не натискає, не вводить, не прокручує й не змінює на екрані та не використовується для запису дзвінків.\\n\\nЩо відбувається з даними:\\n• Вони обробляються лише на цьому пристрої для розрахунку прибутку. Нічого не надсилається на сервер, не передається іншим, не продається і не використовується для реклами.\\n• За прийнятими замовленнями на пристрої зберігаються лише підсумкові цифри (ціна, відстань, час, прибуток) для вашої статистики.\\n\\nВи керуєте цим самі: служба працює лише після того, як ви ввімкнете її в налаштуваннях Android, і ви можете вимкнути її будь-коли. Якщо ви додатково ввімкнете необов’язковий доступ до сповіщень, WayArs читає лише сповіщення підтримуваних застосунків про нові замовлення.",
  "Погоджуюсь, продовжити"
 ],
 "values-de": [
  "Berechtigung für den Bedienungshilfen-Dienst",
  "WayArs verwendet die Android-AccessibilityService-API. Sie wird für die Hauptfunktion der App benötigt: Bestellangebote auf dem Bildschirm der von dir genutzten Liefer- und Fahrdienst-Apps automatisch zu lesen, damit WayArs deinen Nettogewinn berechnet und in einem schwebenden Fenster ein Urteil (gut / akzeptabel / schlecht) anzeigt, solange das Scannen eingeschaltet ist.\\n\\nSo wird sie verwendet:\\n• Gelesen wird der Text, den die unterstützten Apps (und von dir hinzugefügte Apps) bei einem Bestellangebot anzeigen: Preis, Entfernung und geschätzte Zeit.\\n• Passwörter, Nachrichten und deine Eingaben werden nie gelesen. Alles, was andere Apps anzeigen, wird sofort ignoriert.\\n• WayArs tippt, schreibt, scrollt oder ändert nie etwas auf dem Bildschirm und wird nicht zum Aufzeichnen von Anrufen verwendet.\\n\\nWas mit den Daten passiert:\\n• Sie werden nur auf diesem Gerät verarbeitet, um den Gewinn zu berechnen. Nichts wird an einen Server gesendet, weitergegeben, verkauft oder für Werbung genutzt.\\n• Für angenommene Bestellungen werden nur Summenwerte (Preis, Entfernung, Zeit, Gewinn) für deine Statistik auf dem Gerät gespeichert.\\n\\nDu behältst die Kontrolle: Der Dienst funktioniert erst, nachdem du ihn in den Android-Einstellungen aktivierst, und du kannst ihn jederzeit ausschalten. Wenn du zusätzlich den optionalen Benachrichtigungszugriff aktivierst, liest WayArs nur Benachrichtigungen der unterstützten Apps über neue Bestellungen.",
  "Zustimmen und fortfahren"
 ],
 "values-fr": [
  "Autorisation du service d’accessibilité",
  "WayArs utilise l’API AccessibilityService d’Android. Elle est nécessaire à la fonction principale de l’app : lire automatiquement les propositions de course à l’écran des applications de livraison et de VTC que vous utilisez, afin que WayArs calcule votre bénéfice net et affiche un verdict (bon / acceptable / mauvais) dans une fenêtre flottante tant que le scan est activé.\\n\\nComment elle est utilisée :\\n• Elle lit le texte affiché par les applications prises en charge (et celles que vous ajoutez vous-même) lorsqu’une proposition apparaît : prix, distance et durée estimée.\\n• Les mots de passe, messages et tout ce que vous saisissez ne sont jamais lus. Tout ce qu’affichent les autres applications est ignoré immédiatement.\\n• WayArs ne touche, ne saisit, ne fait défiler ni ne modifie jamais rien à l’écran et n’est pas utilisé pour enregistrer des appels.\\n\\nCe qu’il advient des données :\\n• Elles sont traitées uniquement sur cet appareil pour calculer le bénéfice. Rien n’est envoyé à un serveur, partagé, vendu ni utilisé pour la publicité.\\n• Pour les courses acceptées, seuls des chiffres récapitulatifs (prix, distance, durée, bénéfice) sont enregistrés sur l’appareil pour vos statistiques.\\n\\nVous gardez le contrôle : le service ne fonctionne qu’après son activation dans les paramètres Android et vous pouvez le désactiver à tout moment. Si vous activez aussi l’accès facultatif aux notifications, WayArs ne lit que les notifications des applications prises en charge concernant les nouvelles courses.",
  "J’accepte et je continue"
 ],
 "values-cs": [
  "Oprávnění služby usnadnění",
  "WayArs používá rozhraní Android AccessibilityService API. Je potřeba pro hlavní funkci aplikace: automaticky číst nabídky zakázek na obrazovce aplikací pro rozvoz a přepravu, které používáte, aby WayArs vypočítal váš čistý zisk a zobrazil hodnocení (dobré / přijatelné / špatné) v plovoucím okně, dokud je skenování zapnuté.\\n\\nJak se používá:\\n• Čte text zobrazený podporovanými aplikacemi (a aplikacemi, které sami přidáte), když se objeví nabídka zakázky: cena, vzdálenost a odhadovaný čas.\\n• Hesla, zprávy ani to, co píšete, se nikdy nečtou. Vše, co zobrazují jiné aplikace, se okamžitě ignoruje.\\n• WayArs nikdy nic neklepne, nepíše, neposouvá ani nemění na obrazovce a nepoužívá se k nahrávání hovorů.\\n\\nCo se děje s daty:\\n• Zpracovávají se pouze v tomto zařízení za účelem výpočtu zisku. Nic se neodesílá na server, nesdílí, neprodává ani nepoužívá k reklamě.\\n• U přijatých zakázek se v zařízení ukládají jen souhrnné údaje (cena, vzdálenost, čas, zisk) pro vaše statistiky.\\n\\nMáte to pod kontrolou: služba funguje až po zapnutí v nastavení Androidu a můžete ji kdykoli vypnout. Pokud navíc zapnete volitelný přístup k oznámením, WayArs čte jen oznámení podporovaných aplikací o nových zakázkách.",
  "Souhlasím a pokračuji"
 ],
 "values-da": [
  "Tilladelse til tilgængelighedstjeneste",
  "WayArs bruger Androids AccessibilityService API. Det er nødvendigt for appens hovedfunktion: automatisk at læse ordretilbud på skærmen i de leverings- og taxa-apps, du bruger, så WayArs kan beregne din nettofortjeneste og vise en vurdering (god / acceptabel / dårlig) i et svævende vindue, mens scanning er slået til.\\n\\nSådan bruges det:\\n• Den læser den tekst, som de understøttede apps (og apps, du selv tilføjer) viser, når et ordretilbud dukker op: pris, afstand og forventet tid.\\n• Adgangskoder, beskeder og det, du skriver, læses aldrig. Alt, hvad andre apps viser, ignoreres med det samme.\\n• WayArs trykker, skriver, ruller eller ændrer aldrig noget på skærmen og bruges ikke til at optage opkald.\\n\\nHvad der sker med data:\\n• De behandles kun på denne enhed for at beregne fortjeneste. Intet sendes til en server, deles, sælges eller bruges til reklamer.\\n• For ordrer, du accepterer, gemmes kun opsummerede tal (pris, afstand, tid, fortjeneste) på enheden til din statistik.\\n\\nDu har kontrollen: tjenesten virker først, når du slår den til i Androids indstillinger, og du kan slå den fra når som helst. Hvis du også aktiverer den valgfri adgang til notifikationer, læser WayArs kun notifikationer fra understøttede apps om nye ordrer.",
  "Jeg accepterer og fortsætter"
 ],
 "values-nb": [
  "Tillatelse for tilgjengelighetstjeneste",
  "WayArs bruker Androids AccessibilityService API. Det trengs for appens hovedfunksjon: å lese bestillingstilbud automatisk på skjermen i leverings- og drosjeappene du bruker, slik at WayArs kan beregne nettofortjenesten din og vise en vurdering (god / akseptabel / dårlig) i et flytende vindu mens skanning er på.\\n\\nSlik brukes det:\\n• Den leser teksten som de støttede appene (og apper du selv legger til) viser når et bestillingstilbud dukker opp: pris, avstand og estimert tid.\\n• Passord, meldinger og det du skriver leses aldri. Alt andre apper viser, ignoreres umiddelbart.\\n• WayArs trykker, skriver, ruller eller endrer aldri noe på skjermen og brukes ikke til å ta opp samtaler.\\n\\nHva som skjer med dataene:\\n• De behandles bare på denne enheten for å beregne fortjeneste. Ingenting sendes til en server, deles, selges eller brukes til reklame.\\n• For bestillinger du godtar, lagres bare sammendragstall (pris, avstand, tid, fortjeneste) på enheten til statistikken din.\\n\\nDu har kontrollen: tjenesten fungerer først etter at du slår den på i Android-innstillingene, og du kan slå den av når som helst. Hvis du også slår på den valgfrie tilgangen til varsler, leser WayArs bare varsler fra støttede apper om nye bestillinger.",
  "Jeg godtar og fortsetter"
 ],
 "values-sv": [
  "Behörighet för hjälpmedelstjänst",
  "WayArs använder Androids AccessibilityService API. Det behövs för appens huvudfunktion: att automatiskt läsa orderförslag på skärmen i de leverans- och taxiappar du använder, så att WayArs kan beräkna din nettovinst och visa ett omdöme (bra / acceptabelt / dåligt) i ett flytande fönster medan skanning är på.\\n\\nSå används det:\\n• Det läser texten som de stödda apparna (och appar du själv lägger till) visar när ett orderförslag dyker upp: pris, avstånd och beräknad tid.\\n• Lösenord, meddelanden och det du skriver läses aldrig. Allt som andra appar visar ignoreras omedelbart.\\n• WayArs trycker, skriver, rullar eller ändrar aldrig något på skärmen och används inte för att spela in samtal.\\n\\nVad som händer med data:\\n• De bearbetas bara på den här enheten för att beräkna vinst. Inget skickas till en server, delas, säljs eller används för reklam.\\n• För ordrar du accepterar sparas bara sammanfattande siffror (pris, avstånd, tid, vinst) på enheten för din statistik.\\n\\nDu har kontrollen: tjänsten fungerar först när du slår på den i Androids inställningar, och du kan stänga av den när som helst. Om du även aktiverar den valfria åtkomsten till aviseringar läser WayArs bara aviseringar från stödda appar om nya ordrar.",
  "Jag godkänner och fortsätter"
 ],
 "values-ro": [
  "Permisiune pentru serviciul de accesibilitate",
  "WayArs folosește API-ul Android AccessibilityService. Este necesar pentru funcția principală a aplicației: citirea automată a ofertelor de comenzi de pe ecranul aplicațiilor de livrare și de transport pe care le folosești, ca WayArs să calculeze profitul tău net și să afișeze un verdict (bun / acceptabil / prost) într-o fereastră plutitoare cât timp scanarea este pornită.\\n\\nCum este folosit:\\n• Citește textul afișat de aplicațiile acceptate (și de cele pe care le adaugi tu) când apare o ofertă de comandă: preț, distanță și timp estimat.\\n• Parolele, mesajele și ceea ce scrii nu sunt citite niciodată. Tot ce afișează alte aplicații este ignorat imediat.\\n• WayArs nu atinge, nu scrie, nu derulează și nu modifică niciodată nimic pe ecran și nu este folosit pentru înregistrarea apelurilor.\\n\\nCe se întâmplă cu datele:\\n• Sunt procesate doar pe acest dispozitiv, pentru a calcula profitul. Nimic nu este trimis către un server, distribuit, vândut sau folosit pentru reclame.\\n• Pentru comenzile acceptate se salvează pe dispozitiv doar cifre sumare (preț, distanță, timp, profit), pentru statisticile tale.\\n\\nTu ai controlul: serviciul funcționează abia după ce îl activezi din setările Android și îl poți dezactiva oricând. Dacă activezi și accesul opțional la notificări, WayArs citește doar notificările aplicațiilor acceptate despre comenzi noi.",
  "Sunt de acord, continuă"
 ],
 "values-hu": [
  "Kisegítő szolgáltatás engedélye",
  "A WayArs az Android AccessibilityService API-t használja. Az alkalmazás fő funkciójához szükséges: automatikusan olvassa a megrendelési ajánlatokat az általad használt kiszállító és fuvarozó alkalmazások képernyőjéről, hogy a WayArs kiszámítsa a nettó nyereségedet, és lebegő ablakban értékelést (jó / elfogadható / rossz) mutasson, amíg a beolvasás be van kapcsolva.\\n\\nHogyan használja:\\n• A támogatott alkalmazások (és az általad hozzáadottak) által megjelenített szöveget olvassa, amikor ajánlat érkezik: ár, távolság és becsült idő.\\n• Jelszavakat, üzeneteket és a beírt tartalmat soha nem olvas. Minden, amit más alkalmazások mutatnak, azonnal figyelmen kívül marad.\\n• A WayArs soha nem koppint, nem ír be, nem görget és nem módosít semmit a képernyőn, és nem használható hívásrögzítésre.\\n\\nMi történik az adatokkal:\\n• Csak ezen az eszközön dolgozza fel őket a nyereség kiszámításához. Semmi nem kerül szerverre, megosztásra, eladásra vagy hirdetésekhez.\\n• Az elfogadott megrendelésekből csak összesítő számok (ár, távolság, idő, nyereség) tárolódnak az eszközön a statisztikádhoz.\\n\\nTe irányítasz: a szolgáltatás csak azután működik, hogy bekapcsolod az Android beállításaiban, és bármikor kikapcsolhatod. Ha az opcionális értesítés-hozzáférést is bekapcsolod, a WayArs csak a támogatott alkalmazások új megrendelésekről szóló értesítéseit olvassa.",
  "Elfogadom és folytatom"
 ],
 "values-bg": [
  "Разрешение за услугата за достъпност",
  "WayArs използва Android AccessibilityService API. Той е необходим за основната функция на приложението: автоматично да чете предложенията за поръчки на екрана на приложенията за доставка и таксита, които използвате, за да изчисли WayArs чистата ви печалба и да покаже оценка (добра / приемлива / лоша) в плаващ прозорец, докато сканирането е включено.\\n\\nКак се използва:\\n• Чете текста, показван от поддържаните приложения (и от добавените от вас), когато се появи предложение за поръчка: цена, разстояние и приблизително време.\\n• Пароли, съобщения и това, което въвеждате, никога не се четат. Всичко, което показват други приложения, се игнорира веднага.\\n• WayArs никога не натиска, не въвежда, не превърта и не променя нищо на екрана и не се използва за запис на разговори.\\n\\nКакво се случва с данните:\\n• Обработват се само на това устройство, за да се изчисли печалбата. Нищо не се изпраща към сървър, не се споделя, не се продава и не се използва за реклама.\\n• За приетите поръчки на устройството се запазват само обобщени числа (цена, разстояние, време, печалба) за вашата статистика.\\n\\nВие контролирате: услугата работи едва след като я включите в настройките на Android и можете да я изключите по всяко време. Ако включите и по желание достъпа до известия, WayArs чете само известията на поддържаните приложения за нови поръчки.",
  "Съгласен съм, продължи"
 ],
 "values-az": [
  "Xüsusi imkanlar xidmətinə icazə",
  "WayArs Android AccessibilityService API-dən istifadə edir. O, tətbiqin əsas funksiyası üçün lazımdır: istifadə etdiyiniz çatdırılma və taksi tətbiqlərinin ekranındakı sifariş təkliflərini avtomatik oxumaq, beləliklə WayArs xalis gəlirinizi hesablasın və skan aktiv olduqda üzən pəncərədə qiymətləndirmə (yaxşı / məqbul / pis) göstərsin.\\n\\nNecə istifadə olunur:\\n• Dəstəklənən tətbiqlərin (və sizin əlavə etdiyiniz tətbiqlərin) sifariş təklifi göründükdə göstərdiyi mətni oxuyur: qiymət, məsafə və təxmini vaxt.\\n• Parollar, mesajlar və yazdığınız heç nə oxunmur. Digər tətbiqlərin göstərdiyi hər şey dərhal nəzərə alınmır.\\n• WayArs ekranda heç vaxt heç nəyə toxunmur, yazmır, sürüşdürmür və dəyişmir, həmçinin zəngləri yazmaq üçün istifadə olunmur.\\n\\nMəlumatlara nə olur:\\n• Yalnız bu cihazda gəliri hesablamaq üçün emal olunur. Heç nə serverə göndərilmir, paylaşılmır, satılmır və reklam üçün istifadə edilmir.\\n• Qəbul etdiyiniz sifarişlər üçün cihazda yalnız ümumi rəqəmlər (qiymət, məsafə, vaxt, gəlir) statistikanız üçün saxlanılır.\\n\\nNəzarət sizdədir: xidmət yalnız Android ayarlarında onu aktivləşdirdikdən sonra işləyir və istənilən vaxt söndürə bilərsiniz. İstəyə bağlı bildiriş girişini də aktiv etsəniz, WayArs yalnız dəstəklənən tətbiqlərin yeni sifarişlər barədə bildirişlərini oxuyur.",
  "Razıyam, davam et"
 ],
 "values-uz": [
  "Maxsus imkoniyatlar xizmatiga ruxsat",
  "WayArs Android AccessibilityService API’dan foydalanadi. U ilovaning asosiy funksiyasi uchun kerak: siz foydalanadigan yetkazib berish va taksi ilovalari ekranidagi buyurtma takliflarini avtomatik o‘qish, shunda WayArs sof foydangizni hisoblab, skanerlash yoqilgan paytda suzuvchi oynada baho (yaxshi / maqbul / yomon) ko‘rsatadi.\\n\\nQanday ishlatiladi:\\n• U qo‘llab-quvvatlanadigan ilovalar (va siz o‘zingiz qo‘shgan ilovalar) buyurtma taklifi paydo bo‘lganda ko‘rsatadigan matnni o‘qiydi: narx, masofa va taxminiy vaqt.\\n• Parollar, xabarlar va siz yozgan narsa hech qachon o‘qilmaydi. Boshqa ilovalar ko‘rsatgan hamma narsa darhol e’tiborga olinmaydi.\\n• WayArs ekranda hech qachon hech narsani bosmaydi, yozmaydi, aylantirmaydi yoki o‘zgartirmaydi va qo‘ng‘iroqlarni yozib olish uchun ishlatilmaydi.\\n\\nMa’lumotlarga nima bo‘ladi:\\n• Ular faqat shu qurilmada foydani hisoblash uchun qayta ishlanadi. Hech narsa serverga yuborilmaydi, ulashilmaydi, sotilmaydi va reklama uchun ishlatilmaydi.\\n• Qabul qilingan buyurtmalar bo‘yicha qurilmada faqat yig‘ma raqamlar (narx, masofa, vaqt, foyda) statistikangiz uchun saqlanadi.\\n\\nNazorat sizda: xizmat faqat Android sozlamalarida yoqganingizdan keyin ishlaydi va uni istalgan vaqtda o‘chirishingiz mumkin. Agar ixtiyoriy bildirishnomalar ruxsatini ham yoqsangiz, WayArs faqat qo‘llab-quvvatlanadigan ilovalarning yangi buyurtmalar haqidagi bildirishnomalarini o‘qiydi.",
  "Roziman, davom etish"
 ],
 "values-ka": [
  "ხელმისაწვდომობის სერვისის ნებართვა",
  "WayArs იყენებს Android AccessibilityService API-ს. ის საჭიროა აპლიკაციის ძირითადი ფუნქციისთვის: ავტომატურად წაიკითხოს შეკვეთების შეთავაზებები თქვენ მიერ გამოყენებული მიწოდებისა და ტაქსის აპლიკაციების ეკრანზე, რათა WayArs-მა გამოთვალოს თქვენი წმინდა მოგება და აჩვენოს შეფასება (კარგი / მისაღები / ცუდი) მცურავ ფანჯარაში, სანამ სკანირება ჩართულია.\\n\\nროგორ გამოიყენება:\\n• ის კითხულობს ტექსტს, რომელსაც მხარდაჭერილი აპლიკაციები (და თქვენ მიერ დამატებული აპლიკაციები) აჩვენებენ შეკვეთის შეთავაზების გამოჩენისას: ფასი, მანძილი და სავარაუდო დრო.\\n• პაროლები, შეტყობინებები და თქვენ მიერ აკრეფილი არასოდეს იკითხება. ყველაფერი, რასაც სხვა აპლიკაციები აჩვენებენ, მაშინვე იგნორირდება.\\n• WayArs არასოდეს აჭერს, არ ბეჭდავს, არ ასქროლებს და არაფერს ცვლის ეკრანზე და არ გამოიყენება ზარების ჩასაწერად.\\n\\nრა ხდება მონაცემებთან:\\n• ისინი მუშავდება მხოლოდ ამ მოწყობილობაზე მოგების გამოსათვლელად. არაფერი იგზავნება სერვერზე, არ გადაეცემა სხვებს, არ იყიდება და არ გამოიყენება რეკლამისთვის.\\n• მიღებული შეკვეთებისთვის მოწყობილობაზე ინახება მხოლოდ შემაჯამებელი ციფრები (ფასი, მანძილი, დრო, მოგება) თქვენი სტატისტიკისთვის.\\n\\nკონტროლი თქვენ გაქვთ: სერვისი მუშაობს მხოლოდ მას შემდეგ, რაც Android-ის პარამეტრებში ჩართავთ, და ნებისმიერ დროს შეგიძლიათ გამორთოთ. თუ დამატებით ჩართავთ არასავალდებულო წვდომას შეტყობინებებზე, WayArs კითხულობს მხოლოდ მხარდაჭერილი აპლიკაციების შეტყობინებებს ახალი შეკვეთების შესახებ.",
  "ვეთანხმები და გავაგრძელებ"
 ],
 "values-hy": [
  "Հասանելիության ծառայության թույլտվություն",
  "WayArs-ն օգտագործում է Android AccessibilityService API-ը: Այն անհրաժեշտ է հավելվածի հիմնական գործառույթի համար՝ ավտոմատ կերպով կարդալ պատվերների առաջարկները ձեր օգտագործած առաքման և տաքսու հավելվածների էկրանից, որպեսզի WayArs-ը հաշվի ձեր զուտ շահույթը և ցուցադրի գնահատական (լավ / ընդունելի / վատ) լողացող պատուհանում, քանի դեռ սկանավորումը միացված է։\\n\\nԻնչպես է օգտագործվում.\\n• Այն կարդում է աջակցվող հավելվածների (և ձեր ավելացրած հավելվածների) ցուցադրած տեքստը, երբ պատվերի առաջարկ է հայտնվում՝ գինը, հեռավորությունը և մոտավոր ժամանակը։\\n• Գաղտնաբառերը, հաղորդագրությունները և ձեր մուտքագրածը երբեք չեն կարդացվում։ Այն ամենը, ինչ ցուցադրում են այլ հավելվածները, անմիջապես անտեսվում է։\\n• WayArs-ը երբեք ոչինչ չի սեղմում, չի մուտքագրում, չի ոլորում և չի փոխում էկրանին, և չի օգտագործվում զանգերի ձայնագրման համար։\\n\\nԻնչ է լինում տվյալների հետ.\\n• Դրանք մշակվում են միայն այս սարքում՝ շահույթը հաշվելու համար։ Ոչինչ չի ուղարկվում սերվեր, չի փոխանցվում, չի վաճառվում և չի օգտագործվում գովազդի համար։\\n• Ընդունված պատվերների համար սարքում պահվում են միայն ամփոփ թվեր (գին, հեռավորություն, ժամանակ, շահույթ) ձեր վիճակագրության համար։\\n\\nՎերահսկողությունը ձերն է. ծառայությունն աշխատում է միայն Android-ի կարգավորումներում դրա միացումից հետո, և կարող եք անջատել ցանկացած պահի։ Եթե լրացուցիչ միացնեք ծանուցումների ընտրովի հասանելիությունը, WayArs-ը կկարդա միայն աջակցվող հավելվածների ծանուցումները նոր պատվերների մասին։",
  "Համաձայն եմ, շարունակել"
 ]
}

def consent_strings(d):
    title, body, allow = CONSENT[d]
    def f(s):
        def setv(s, name, val):
            pat = re.compile(r'(<string name="%s">)(.*?)(</string>)' % name, re.S)
            if not pat.search(s):
                raise SystemExit("missing %s in %s" % (name, d))
            return pat.sub(lambda m: m.group(1) + val + m.group(3), s, count=1)
        s = setv(s, "accessibility_consent_title", title)
        s = setv(s, "accessibility_consent_body", body)
        s = setv(s, "accessibility_consent_allow", allow)
        return s
    return f
for _d in CONSENT:
    rw("app/src/main/res/%s/strings.xml" % _d, consent_strings(_d))

def consent_scroll(s):
    for imp in ("import androidx.compose.foundation.rememberScrollState", "import androidx.compose.foundation.verticalScroll"):
        s = add_import(s, imp)
    return sub(s, "        text = { Text(stringResource(R.string.accessibility_consent_body)) },",
               "        // Long disclosure: must stay fully readable on small screens, buttons always reachable.\n"
               "        text = { Text(stringResource(R.string.accessibility_consent_body), modifier = Modifier.verticalScroll(rememberScrollState())) },",
               "accessibility_consent_body), modifier = Modifier.verticalScroll")
rw(BASE + "ui/screen/settings/SettingsScreen.kt", consent_scroll)
PYEOF
echo "Done. Review with git diff, then: git add -A && git commit && git push"
