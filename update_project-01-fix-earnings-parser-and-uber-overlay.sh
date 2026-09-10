#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

PARSER_FILE="$REPO_ROOT/app/src/main/java/com/wayars/app/util/ScreenTextParser.kt"
SERVICE_FILE="$REPO_ROOT/app/src/main/java/com/wayars/app/service/accessibility/OrderAccessibilityService.kt"
CONFIG_FILE="$REPO_ROOT/app/src/main/res/xml/accessibility_service_config.xml"

for f in "$PARSER_FILE" "$SERVICE_FILE" "$CONFIG_FILE"; do
  if [ ! -f "$f" ]; then
    echo "ERROR: expected file not found: $f" >&2
    exit 1
  fi
done

python3 - "$PARSER_FILE" "$SERVICE_FILE" "$CONFIG_FILE" <<'PYEOF'
import sys

parser_file, service_file, config_file = sys.argv[1:4]

def replace_once(path, old, new, label):
    with open(path, "r", encoding="utf-8") as fh:
        content = fh.read()
    count = content.count(old)
    if count != 1:
        print(f"ERROR: expected exactly 1 match for '{label}' in {path}, found {count}", file=sys.stderr)
        sys.exit(1)
    content = content.replace(old, new)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)
    print(f"OK: patched '{label}' in {path}")

# --- 1. ScreenTextParser.kt: star-badge + cross-node "/km" guards ---
old_parser = """    fun parse(texts: List<String>): RawOrderCandidate {
        var earnings: Double? = null
        var currency: Currency? = null
        var distanceKm: Double? = null
        var timeMinutes: Double? = null

        for (raw in texts) {
            val text = raw.trim()
            if (text.isEmpty()) continue

            if (earnings == null) {
                for ((regex, cur) in moneyPatterns) {
                    val match = regex.find(text) ?: continue
                    if (perUnitSuffixRegex.containsMatchIn(text.substring(match.range.last + 1))) continue
                    val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        earnings = amount
                        currency = cur
                        break
                    }
                }
            }"""

new_parser = """    fun parse(texts: List<String>): RawOrderCandidate {
        var earnings: Double? = null
        var currency: Currency? = null
        var distanceKm: Double? = null
        var timeMinutes: Double? = null

        for (index in texts.indices) {
            val raw = texts[index]
            val text = raw.trim()
            if (text.isEmpty()) continue

            if (earnings == null) {
                for ((regex, cur) in moneyPatterns) {
                    val match = regex.find(text) ?: continue

                    // Guard: a rating badge (e.g. "\u2605 1.67") is never the
                    // order total, even on the rare layout where the badge
                    // text itself happens to sit next to a currency marker.
                    // Checked in the SAME string only \u2014 a star glyph in an
                    // unrelated earlier sibling node can't reach this match.
                    val precedingText = text.substring(0, match.range.first)
                    if (precedingText.trimEnd().endsWith("\u2605")) continue

                    // Guard: a per-km/per-order rate suffix ("/km", "per km").
                    // Checked in two places: right after the amount in THIS
                    // string (original case), and also at the START of the
                    // NEXT collected text (Stuart observed splitting a rate
                    // like "1.67 z\u0142" and its "/km" suffix across two sibling
                    // nodes \u2014 collectText's sibling order guarantees the
                    // suffix, if present, is the very next entry).
                    val sameNodeSuffix = text.substring(match.range.last + 1)
                    val nextNodeText = texts.getOrNull(index + 1)?.trim().orEmpty()
                    if (perUnitSuffixRegex.containsMatchIn(sameNodeSuffix) ||
                        perUnitSuffixRegex.containsMatchIn(nextNodeText)
                    ) continue

                    val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        earnings = amount
                        currency = cur
                        break
                    }
                }
            }"""

replace_once(parser_file, old_parser, new_parser, "parse() money-match guards")

# --- 2. OrderAccessibilityService.kt: instant Uber overlay detection ---
old_service = """        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        // Package filter MUST run before the debounce check, not after.
        // The old order checked/updated a single global lastProcessedAt
        // against events from EVERY app (launcher, system UI, keyboard,
        // whatever else is generating accessibility events at that moment)
        // before ever looking at which package the event came from. On a
        // busy device that global timer is almost never idle, so the one
        // event that actually matters \u2014 Uber's order popup WINDOW_STATE_
        // CHANGED the instant it appears \u2014 regularly landed inside someone
        // else's 500ms window and got silently dropped. By the time a
        // Uber event finally survived the debounce, the popup itself had
        // often already been dismissed/expired, so the overlay showed
        // late or not at all. Filtering by package first means only
        // Uber's (or another supported app's) own event cadence can debounce
        // Uber, so the very first appearance is processed immediately.
        val eventPackage = event.packageName?.toString() ?: return
        val isSupported = isSupportedPackage(eventPackage)
        if (!isSupported) {
            // Still worth a diagnostic line (see Settings -> \u0414\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u0430) so
            // the real Bolt/Wolt/Uber package name can be confirmed
            // on-device \u2014 but nothing more expensive than that for apps we
            // don't care about.
            ScanDiagnostics.record(eventPackage, matchedSupportedApp = false)
            return
        }

        // Per-package debounce, not a single global one \u2014 see comment
        // above. A burst of content-changed events from the SAME popup
        // (e.g. a ticking ETA) is still collapsed, but that no longer
        // costs other apps' events any of the window's budget.
        val now = System.currentTimeMillis()
        val lastForPackage = lastProcessedAtByPackage[eventPackage] ?: 0L
        // Never debounce a brand-new window appearing \u2014 only repeat
        // content-changed spam on an already-seen window. This is what
        // guarantees the FIRST sighting of an order popup (Uber's included)
        // is always parsed instantly instead of possibly being the one
        // event that gets swallowed by the debounce.
        val isNewWindowAppearing = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!isNewWindowAppearing && now - lastForPackage < 500) return
        lastProcessedAtByPackage[eventPackage] = now

        val root = findSupportedWindowRoot(eventPackage)
        if (root == null) {
            ScanDiagnostics.record(eventPackage, matchedSupportedApp = true, windowFound = false)
            return
        }"""

new_service = """        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        // TYPE_WINDOWS_CHANGED fires the instant the system's set of visible
        // windows changes anywhere on the device, and is the only event a
        // non-focusable TYPE_APPLICATION_OVERLAY popup reliably triggers \u2014
        // which is how Uber draws its incoming-order toast. Unlike the other
        // two event types it does NOT reliably carry event.packageName, so
        // the package can't be read off the event itself here. Resolve it by
        // scanning the actually-visible windows instead (findSupportedWindowRoot
        // already does that scan) and hang onto the root it finds so the
        // normal path below doesn't have to scan `windows` a second time.
        val windowsChangedRoot: AccessibilityNodeInfo? =
            if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                findSupportedWindowRoot(null)
            } else {
                null
            }

        // Package filter MUST run before the debounce check, not after.
        // The old order checked/updated a single global lastProcessedAt
        // against events from EVERY app (launcher, system UI, keyboard,
        // whatever else is generating accessibility events at that moment)
        // before ever looking at which package the event came from. On a
        // busy device that global timer is almost never idle, so the one
        // event that actually matters \u2014 Uber's order popup WINDOW_STATE_
        // CHANGED the instant it appears \u2014 regularly landed inside someone
        // else's 500ms window and got silently dropped. By the time a
        // Uber event finally survived the debounce, the popup itself had
        // often already been dismissed/expired, so the overlay showed
        // late or not at all. Filtering by package first means only
        // Uber's (or another supported app's) own event cadence can debounce
        // Uber, so the very first appearance is processed immediately.
        val eventPackage = if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            windowsChangedRoot?.packageName?.toString() ?: return
        } else {
            event.packageName?.toString() ?: return
        }
        val isSupported = isSupportedPackage(eventPackage)
        if (!isSupported) {
            // Still worth a diagnostic line (see Settings -> \u0414\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u0430) so
            // the real Bolt/Wolt/Uber package name can be confirmed
            // on-device \u2014 but nothing more expensive than that for apps we
            // don't care about.
            ScanDiagnostics.record(eventPackage, matchedSupportedApp = false)
            return
        }

        // Per-package debounce, not a single global one \u2014 see comment
        // above. A burst of content-changed events from the SAME popup
        // (e.g. a ticking ETA) is still collapsed, but that no longer
        // costs other apps' events any of the window's budget.
        val now = System.currentTimeMillis()
        val lastForPackage = lastProcessedAtByPackage[eventPackage] ?: 0L
        // Never debounce a brand-new window appearing \u2014 only repeat
        // content-changed spam on an already-seen window. This is what
        // guarantees the FIRST sighting of an order popup (Uber's included)
        // is always parsed instantly instead of possibly being the one
        // event that gets swallowed by the debounce. TYPE_WINDOWS_CHANGED
        // counts as a new-window signal too \u2014 it's fired for exactly that
        // reason for Uber's overlay popup.
        val isNewWindowAppearing = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isNewWindowAppearing && now - lastForPackage < 500) return
        lastProcessedAtByPackage[eventPackage] = now

        val root = windowsChangedRoot ?: findSupportedWindowRoot(eventPackage)
        if (root == null) {
            ScanDiagnostics.record(eventPackage, matchedSupportedApp = true, windowFound = false)
            return
        }"""

replace_once(service_file, old_service, new_service, "onAccessibilityEvent windows-changed handling")

# --- 3. accessibility_service_config.xml: declare typeWindowsChanged ---
old_config = 'android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"'
new_config = 'android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged"'
replace_once(config_file, old_config, new_config, "accessibilityEventTypes")

print("All three patches applied.")
PYEOF

echo
echo "=== git status --short ==="
git status --short

echo
echo "=== git diff --stat ==="
git diff --stat
