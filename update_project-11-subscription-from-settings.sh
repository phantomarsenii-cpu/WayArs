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
#  9) In-app Privacy Policy (en/ru/pl) brought in line with the website policy and the app:
#     no clicks/typing, other apps ignored, consent shown first, optional notification access,
#     fuel cost / net profit in history, local free-trial counter, RevenueCat technical data.
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
    if line in s:
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
}
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
PYEOF
echo "Done. Review with git diff, then: git add -A && git commit && git push"
