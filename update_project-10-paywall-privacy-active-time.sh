#!/usr/bin/env bash
# update_project-10-paywall-privacy-active-time.sh  (WayArs)
# 1) Paywall: offerings are re-requested on every return to the screen (ON_RESUME)
#    and once more ~4s after opening (RevenueCat returns its cached copy first and
#    refreshes in the background, so the 2nd call picks up the new data).
#    Parallel calls are de-duplicated in SubscriptionViewModel.
# 2) Privacy policy (ru / pl / uk): removed the trailing «мы» / („my”) / «ми» after
#    the developer's name.
# 3) Dashboard: two separate tiles - "Active Time" (how long scanning has been ON today,
#    from pressing "Active"; new ActiveSessionTracker, survives process death) and
#    "Order Time" (the old figure: total time of evaluated orders). Summary card is now a 2x2 grid.
# Idempotent: safe to run twice.  Run BEFORE git add/commit/push.
set -e
cd "$(dirname "$0")"
[ -f app/src/main/AndroidManifest.xml ] || { echo "Run this script from the WayArs repo root"; exit 1; }

python3 - <<'PYEOF'
import glob, io, os, re

def rw(path, fn):
    s = io.open(path, encoding="utf-8").read()
    out = fn(s)
    if out != s:
        io.open(path, "w", encoding="utf-8").write(out)
        print("updated", path)
    else:
        print("already OK", path)

# ---- 1a) SubscriptionViewModel: de-duplicate parallel loads
VM = "app/src/main/java/com/wayars/app/presentation/SubscriptionViewModel.kt"
def vm(s):
    if "offeringsJob" in s:
        return s
    s = s.replace("import kotlinx.coroutines.flow.MutableStateFlow",
                  "import kotlinx.coroutines.Job\nimport kotlinx.coroutines.flow.MutableStateFlow", 1)
    old = "    fun loadOfferings() {\n        viewModelScope.launch {\n"
    new = ("    private var offeringsJob: Job? = null\n\n"
           "    fun loadOfferings() {\n"
           "        // Paywall asks for offerings on open, on resume and once more\n"
           "        // shortly after open; never run two requests at the same time.\n"
           "        if (offeringsJob?.isActive == true) return\n"
           "        offeringsJob = viewModelScope.launch {\n")
    assert old in s, "loadOfferings() block not found"
    return s.replace(old, new, 1)
rw(VM, vm)

# ---- 1b) PaywallScreen: refresh on resume + delayed second request
PW = "app/src/main/java/com/wayars/app/presentation/ui/screen/paywall/PaywallScreen.kt"
def pw(s):
    if "LifecycleEventObserver" in s:
        return s
    imports = ("import androidx.compose.runtime.Composable\n"
               "import androidx.compose.runtime.DisposableEffect\n")
    s = s.replace("import androidx.compose.runtime.Composable\n", imports, 1)
    s = s.replace("import androidx.compose.ui.draw.clip\n",
                  "import androidx.compose.ui.draw.clip\n"
                  "import androidx.compose.ui.platform.LocalLifecycleOwner\n"
                  "import androidx.lifecycle.Lifecycle\n"
                  "import androidx.lifecycle.LifecycleEventObserver\n"
                  "import kotlinx.coroutines.delay\n", 1)
    old = "    LaunchedEffect(Unit) { onLoadOfferings() }\n"
    new = ("    // RevenueCat hands back its cached offerings first and refreshes them in\n"
           "    // the background, so a single request can show stale prices (e.g. right\n"
           "    // after a price change in Play Console). Ask again a few seconds later\n"
           "    // and every time the user returns to the paywall.\n"
           "    LaunchedEffect(Unit) {\n"
           "        onLoadOfferings()\n"
           "        delay(4_000L)\n"
           "        onLoadOfferings()\n"
           "    }\n"
           "    val lifecycleOwner = LocalLifecycleOwner.current\n"
           "    DisposableEffect(lifecycleOwner) {\n"
           "        val observer = LifecycleEventObserver { _, event ->\n"
           "            if (event == Lifecycle.Event.ON_RESUME) onLoadOfferings()\n"
           "        }\n"
           "        lifecycleOwner.lifecycle.addObserver(observer)\n"
           "        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }\n"
           "    }\n")
    assert old in s, "LaunchedEffect(Unit) { onLoadOfferings() } not found"
    return s.replace(old, new, 1)
rw(PW, pw)

# ---- 2) Privacy policy: drop the "we" pronoun in brackets
REPL = {"ru": " («мы»)", "pl": " („my”)", "uk": " («ми»)"}
for lang, frag in REPL.items():
    rw("app/src/main/res/values-%s/strings.xml" % lang,
       lambda s, frag=frag: s.replace(frag, ""))

# ================= 3) Active time (app session) + separate Order time =================
TRACKER = "app/src/main/java/com/wayars/app/service/accessibility/ActiveSessionTracker.kt"
TRACKER_SRC = """package com.wayars.app.service.accessibility

import android.content.Context
import java.util.Calendar

/**
 * Counts how long the driver has had scanning switched ON (the Dashboard
 * "Active" toggle) today - independent of whether any order was seen or
 * accepted. This is the "Active Time" tile; the old order-based figure is
 * now the separate "Order Time" tile.
 *
 * Persisted in SharedPreferences so it survives process death: while a
 * session runs, a heartbeat is written (by the app's polling loop); if the
 * process dies mid-session, the next cold start credits the session up to
 * the last heartbeat. Only today's total is kept - it resets at midnight,
 * and a session running across midnight only counts its post-midnight part.
 */
object ActiveSessionTracker {

    private const val PREFS = "active_session_tracker"
    private const val KEY_DAY = "day"
    private const val KEY_TODAY_MS = "today_ms"
    private const val KEY_SESSION_START = "session_start"
    private const val KEY_LAST_BEAT = "last_beat"

    @Volatile
    private var appContext: Context? = null

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Call once from Application.onCreate. Recovers a session cut short by process death. */
    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        val p = prefs() ?: return
        val start = p.getLong(KEY_SESSION_START, 0L)
        if (start > 0L) {
            val beat = maxOf(p.getLong(KEY_LAST_BEAT, start), start)
            credit(start, beat)
            p.edit().putLong(KEY_SESSION_START, 0L).apply()
        }
    }

    @Synchronized
    fun onActiveChanged(active: Boolean, context: Context) {
        if (appContext == null) appContext = context.applicationContext
        val p = prefs() ?: return
        val now = System.currentTimeMillis()
        val start = p.getLong(KEY_SESSION_START, 0L)
        if (active) {
            if (start == 0L) {
                p.edit().putLong(KEY_SESSION_START, now).putLong(KEY_LAST_BEAT, now).apply()
            }
        } else if (start > 0L) {
            credit(start, now)
            p.edit().putLong(KEY_SESSION_START, 0L).apply()
        }
    }

    /** Called periodically while scanning runs, so a killed process loses at most one interval. */
    @Synchronized
    fun heartbeat() {
        val p = prefs() ?: return
        if (p.getLong(KEY_SESSION_START, 0L) > 0L) {
            p.edit().putLong(KEY_LAST_BEAT, System.currentTimeMillis()).apply()
        }
    }

    /** Today's active milliseconds, including the currently running session. */
    @Synchronized
    fun todayMillis(now: Long = System.currentTimeMillis()): Long {
        val p = prefs() ?: return 0L
        val dayStart = startOfDay(now)
        var total = if (p.getLong(KEY_DAY, 0L) == dayStart) p.getLong(KEY_TODAY_MS, 0L) else 0L
        val start = p.getLong(KEY_SESSION_START, 0L)
        if (start > 0L) total += maxOf(0L, now - maxOf(start, dayStart))
        return total
    }

    private fun credit(start: Long, end: Long) {
        val p = prefs() ?: return
        val dayStart = startOfDay(end)
        val base = if (p.getLong(KEY_DAY, 0L) == dayStart) p.getLong(KEY_TODAY_MS, 0L) else 0L
        val add = maxOf(0L, end - maxOf(start, dayStart))
        p.edit().putLong(KEY_DAY, dayStart).putLong(KEY_TODAY_MS, base + add).apply()
    }
}
"""
import os
if not os.path.exists(TRACKER):
    io.open(TRACKER, "w", encoding="utf-8").write(TRACKER_SRC)
    print("created", TRACKER)
else:
    print("already OK", TRACKER)

# ScanningState: notify tracker BEFORE flipping the flag (so observers see the credited total)
SS = "app/src/main/java/com/wayars/app/service/accessibility/ScanningState.kt"
def ss(s):
    if "ActiveSessionTracker" in s:
        return s
    old = "        _isActive.value = active\n"
    assert old in s, "ScanningState.setActive not found"
    return s.replace(old, "        ActiveSessionTracker.onActiveChanged(active, context)\n        _isActive.value = active\n", 1)
rw(SS, ss)

# Application: init tracker + heartbeat in the polling loop
APP = "app/src/main/java/com/wayars/app/WayArsApplication.kt"
def app(s):
    if "ActiveSessionTracker" in s:
        return s
    s = s.replace("import com.wayars.app.service.accessibility.ScanningState\n",
                  "import com.wayars.app.service.accessibility.ActiveSessionTracker\n"
                  "import com.wayars.app.service.accessibility.ScanningState\n", 1)
    old = "        container = AppContainer(this)\n"
    assert old in s, "AppContainer(this) not found"
    s = s.replace(old, old + "        ActiveSessionTracker.init(this)\n", 1)
    old2 = "                delay(SUBSCRIPTION_POLL_INTERVAL_MS)\n"
    assert old2 in s, "poll delay not found"
    s = s.replace(old2, "                ActiveSessionTracker.heartbeat()\n" + old2, 1)
    return s
rw(APP, app)

# Dashboard: two separate tiles
DB = "app/src/main/java/com/wayars/app/presentation/ui/screen/dashboard/DashboardScreen.kt"
def db(s):
    if "ActiveSessionTracker" in s:
        return s
    s = s.replace("import androidx.compose.runtime.getValue\n",
                  "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.produceState\n", 1)
    s = s.replace("import com.wayars.app.service.accessibility.ScanningState\n",
                  "import com.wayars.app.service.accessibility.ActiveSessionTracker\n"
                  "import com.wayars.app.service.accessibility.ScanningState\n", 1)
    s = s.replace("import com.wayars.app.util.CurrencyFormatter\n",
                  "import com.wayars.app.util.CurrencyFormatter\nimport kotlinx.coroutines.delay\n", 1)
    old = "    val isActive by ScanningState.isActive.collectAsState()\n"
    assert old in s, "isActive line not found"
    new = (old +
           "    // Time the scanner has been switched ON today (not order-based).\n"
           "    // Holds WHOLE MINUTES, so the card only recomposes when the shown value\n"
           "    // really changes; while scanning runs it sleeps exactly until the next\n"
           "    // minute boundary, so the tile ticks up the moment the minute flips.\n"
           "    val scanningNow = isActive\n"
           "    val activeTodayMinutes by produceState(initialValue = ActiveSessionTracker.todayMillis() / 60_000L, scanningNow) {\n"
           "        while (true) {\n"
           "            val ms = ActiveSessionTracker.todayMillis()\n"
           "            value = ms / 60_000L\n"
           "            delay(if (scanningNow) 60_000L - (ms % 60_000L) + 50L else 15_000L)\n"
           "        }\n"
           "    }\n")
    s = s.replace(old, new, 1)
    old_row = s[s.index("            Row(modifier = Modifier.fillMaxWidth()) {\n                SummaryStat(\n                    value = fmtDuration(summary.totalTimeMinutes),"):]
    old_row = old_row[:old_row.index("            }\n        }\n") + len("            }\n")]
    new_rows = (
        "            Row(modifier = Modifier.fillMaxWidth()) {\n"
        "                SummaryStat(\n"
        "                    value = fmtDuration(activeTodayMinutes.toDouble()),\n"
        "                    label = stringResource(R.string.dashboard_active_time),\n"
        "                    modifier = Modifier.weight(1f)\n"
        "                )\n"
        "                SummaryStat(\n"
        "                    value = fmtDuration(summary.totalTimeMinutes),\n"
        "                    label = stringResource(R.string.dashboard_order_time),\n"
        "                    modifier = Modifier.weight(1f)\n"
        "                )\n"
        "            }\n"
        "            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))\n"
        "            Row(modifier = Modifier.fillMaxWidth()) {\n"
        "                SummaryStat(\n"
        "                    value = \"${fmt(summary.totalDistanceKm)} km\",\n"
        "                    label = stringResource(R.string.dashboard_distance),\n"
        "                    modifier = Modifier.weight(1f)\n"
        "                )\n"
        "                SummaryStat(\n"
        "                    value = CurrencyFormatter.formatRatePerKm(summary.avgRatePerKm, summary.currency),\n"
        "                    label = stringResource(R.string.dashboard_avg_rate),\n"
        "                    modifier = Modifier.weight(1f)\n"
        "                )\n"
        "            }\n")
    s = s.replace(old_row, new_rows, 1)
    return s
rw(DB, db)

# Dashboard (lifecycle-aware tile): no timer at all while scanning is OFF, and no
# timer while the app is not on screen. Also upgrades earlier versions of this patch.
def db2(s):
    if "repeatOnLifecycle" in s:
        return s
    m = re.search(r"    val scanningNow = isActive\n.*?\n    }\n|    val activeTodayMs by produceState\(.*?\n    }\n", s, re.S)
    assert m, "active time block not found"
    new = (
        "    val scanningNow = isActive\n"
        "    val lifecycleOwner = LocalLifecycleOwner.current\n"
        "    // Whole minutes. Only runs while the screen is visible (STARTED); when scanning\n"
        "    // is OFF it reads the value once and stops, when scanning is ON it sleeps\n"
        "    // exactly until the next minute boundary. Nothing runs in the background.\n"
        "    val activeTodayMinutes by produceState(initialValue = ActiveSessionTracker.todayMillis() / 60_000L, scanningNow) {\n"
        "        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {\n"
        "            while (true) {\n"
        "                val ms = ActiveSessionTracker.todayMillis()\n"
        "                value = ms / 60_000L\n"
        "                if (!scanningNow) break\n"
        "                delay(60_000L - (ms % 60_000L) + 50L)\n"
        "            }\n"
        "        }\n"
        "    }\n")
    s = s[:m.start()] + new + s[m.end():]
    s = s.replace("fmtDuration(activeTodayMs / 60_000.0)", "fmtDuration(activeTodayMinutes.toDouble())")
    s = s.replace("import androidx.compose.runtime.produceState\n",
                  "import androidx.compose.runtime.produceState\n"
                  "import androidx.compose.ui.platform.LocalLifecycleOwner\n"
                  "import androidx.lifecycle.Lifecycle\n"
                  "import androidx.lifecycle.repeatOnLifecycle\n", 1)
    return s
rw(DB, db2)

# Strings: new label "Order Time" in every language
ORDER_TIME = {
    "en": "Order Time", "ru": "Время по заказам", "pl": "Czas zleceń", "uk": "Час замовлень",
    "de": "Auftragszeit", "fr": "Durée des commandes", "sv": "Ordertid", "cs": "Čas zakázek",
    "uz": "Buyurtmalar vaqti", "ka": "შეკვეთების დრო", "da": "Ordretid", "hy": "Պատվերների ժամանակ",
    "nb": "Ordretid", "bg": "Време за поръчки", "az": "Sifariş vaxtı", "ro": "Timp comenzi",
    "hu": "Rendelési idő",
}
import re
for f in sorted(glob.glob("app/src/main/res/values*/strings.xml")):
    d = os.path.basename(os.path.dirname(f))
    lang = d.split("-")[1] if "-" in d else "en"
    text = ORDER_TIME.get(lang, ORDER_TIME["en"])
    def add(s, text=text):
        if 'name="dashboard_order_time"' in s:
            return s
        return re.sub(r'([ \t]*<string name="dashboard_active_time">[^\n]*</string>\n)',
                      lambda m: m.group(1) + '    <string name="dashboard_order_time">' + text + '</string>\n', s, count=1)
    rw(f, add)

PYEOF

echo "Done. Now: git add -A && git commit -m 'Paywall refresh, privacy pronoun, active vs order time' && git push"
