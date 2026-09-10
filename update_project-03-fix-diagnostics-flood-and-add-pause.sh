#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

SERVICE_FILE="$REPO_ROOT/app/src/main/java/com/wayars/app/service/accessibility/OrderAccessibilityService.kt"
DIAGNOSTICS_FILE="$REPO_ROOT/app/src/main/java/com/wayars/app/service/accessibility/ScanDiagnostics.kt"
SETTINGS_FILE="$REPO_ROOT/app/src/main/java/com/wayars/app/presentation/ui/screen/settings/SettingsScreen.kt"

for f in "$SERVICE_FILE" "$DIAGNOSTICS_FILE" "$SETTINGS_FILE"; do
  if [ ! -f "$f" ]; then
    echo "ERROR: expected file not found: $f" >&2
    exit 1
  fi
done

python3 - "$SERVICE_FILE" "$DIAGNOSTICS_FILE" "$SETTINGS_FILE" <<'PYEOF'
import sys

service_file, diagnostics_file, settings_file = sys.argv[1:4]

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

# --- 1. OrderAccessibilityService.kt: stop bypassing debounce for TYPE_WINDOWS_CHANGED ---
old_service = """        // event that gets swallowed by the debounce. TYPE_WINDOWS_CHANGED
        // counts as a new-window signal too \u2014 it's fired for exactly that
        // reason for Uber's overlay popup.
        val isNewWindowAppearing = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED"""

new_service = """        // event that gets swallowed by the debounce. TYPE_WINDOWS_CHANGED
        // deliberately does NOT get the same bypass: unlike WINDOW_STATE_
        // CHANGED (which only fires on a genuine window change), WINDOWS_
        // CHANGED fires for any change to the system's visible-window list
        // \u2014 including ones that have nothing to do with a new order (minor
        // layout/animation churn while the same popup is still on screen).
        // Bypassing the debounce for every one of those flooded ScanDiagnostics
        // with near-duplicate entries every few hundred ms, which pushed the
        // one entry actually worth reading off the capped list before it
        // could be opened. The normal per-package debounce below is enough:
        // the first WINDOWS_CHANGED for a package is virtually always >500ms
        // after that package's last event (it wasn't in the foreground a
        // moment ago), so Uber's popup still gets caught on first sight.
        val isNewWindowAppearing = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED"""

replace_once(service_file, old_service, new_service, "remove TYPE_WINDOWS_CHANGED debounce bypass")

# --- 2. ScanDiagnostics.kt: add a paused flag ---
old_diag = """object ScanDiagnostics {
    private const val MAX_ENTRIES = 20

    private val _recentPackages = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val recentPackages: StateFlow<List<DiagnosticEntry>> = _recentPackages

    /** Raw text lists are capped per-entry too \u2014 only need enough to spot the pattern. */
    private const val MAX_RAW_TEXTS_PER_ENTRY = 40

    fun record(
        packageName: String,
        matchedSupportedApp: Boolean,
        windowFound: Boolean = false,
        textsCollected: Int = 0,
        parsedSummary: String? = null,
        rawTexts: List<String> = emptyList()
    ) {
        val entry = DiagnosticEntry("""

new_diag = """object ScanDiagnostics {
    private const val MAX_ENTRIES = 20

    private val _recentPackages = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val recentPackages: StateFlow<List<DiagnosticEntry>> = _recentPackages

    /**
     * While true, [record] is a no-op. Lets the Diagnostics screen freeze
     * the list on demand \u2014 entries scroll off the capped list fast enough
     * (even after fixing the debounce bug that made it worse) that reading
     * one before it's evicted was still a race against real time.
     */
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused

    fun setPaused(value: Boolean) {
        _paused.value = value
    }

    /** Raw text lists are capped per-entry too \u2014 only need enough to spot the pattern. */
    private const val MAX_RAW_TEXTS_PER_ENTRY = 40

    fun record(
        packageName: String,
        matchedSupportedApp: Boolean,
        windowFound: Boolean = false,
        textsCollected: Int = 0,
        parsedSummary: String? = null,
        rawTexts: List<String> = emptyList()
    ) {
        if (_paused.value) return
        val entry = DiagnosticEntry("""

replace_once(diagnostics_file, old_diag, new_diag, "add paused flag")

# --- 3. SettingsScreen.kt: pause/resume toggle in the diagnostics header ---
old_settings = """@Composable
private fun DiagnosticsSection() {
    val entries by com.wayars.app.service.accessibility.ScanDiagnostics.recentPackages.collectAsState()
    var expanded by remember { mutableStateOf(true) }
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Only the header toggles the whole section now \u2014 this used
                // to be on the outer Column, which meant it covered the
                // entries list too. Since no entry row had its own clickable
                // area big enough to consume a tap first, almost any tap
                // inside an entry (anywhere but the tiny "\u0442\u0435\u043a\u0441\u0442\u044b: \u043f\u043e\u043a\u0430\u0437\u0430\u0442\u044c"
                // label) fell through to this handler and collapsed the
                // whole diagnostics block instead of expanding that entry's
                // raw texts.
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("\u0414\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u0430 (\u0432\u0440\u0435\u043c\u0435\u043d\u043d\u043e)", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Text(
                    "\u041e\u0442\u043a\u0440\u043e\u0439 Bolt/Wolt \u0438 \u043f\u043e\u0441\u043c\u043e\u0442\u0440\u0438, \u0447\u0442\u043e \u043f\u043e\u044f\u0432\u0438\u0442\u0441\u044f \u043d\u0438\u0436\u0435",
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = WaTextSecondary
            )
        }"""

new_settings = """@Composable
private fun DiagnosticsSection() {
    val entries by com.wayars.app.service.accessibility.ScanDiagnostics.recentPackages.collectAsState()
    val paused by com.wayars.app.service.accessibility.ScanDiagnostics.paused.collectAsState()
    var expanded by remember { mutableStateOf(true) }
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Only the header toggles the whole section now \u2014 this used
                // to be on the outer Column, which meant it covered the
                // entries list too. Since no entry row had its own clickable
                // area big enough to consume a tap first, almost any tap
                // inside an entry (anywhere but the tiny "\u0442\u0435\u043a\u0441\u0442\u044b: \u043f\u043e\u043a\u0430\u0437\u0430\u0442\u044c"
                // label) fell through to this handler and collapsed the
                // whole diagnostics block instead of expanding that entry's
                // raw texts.
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("\u0414\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u0430 (\u0432\u0440\u0435\u043c\u0435\u043d\u043d\u043e)", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Text(
                    "\u041e\u0442\u043a\u0440\u043e\u0439 Bolt/Wolt \u0438 \u043f\u043e\u0441\u043c\u043e\u0442\u0440\u0438, \u0447\u0442\u043e \u043f\u043e\u044f\u0432\u0438\u0442\u0441\u044f \u043d\u0438\u0436\u0435",
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            // Separate clickable target from the header row's own toggle \u2014
            // stops event propagation here the same way the per-entry rows
            // do below, so tapping Pause doesn't also collapse the section.
            Text(
                if (paused) "\u25b6 \u043f\u0440\u043e\u0434\u043e\u043b\u0436\u0438\u0442\u044c" else "\u23f8 \u043f\u0430\u0443\u0437\u0430",
                color = if (paused) WaNeonGreen else WaTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clickable { com.wayars.app.service.accessibility.ScanDiagnostics.setPaused(!paused) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = WaTextSecondary
            )
        }"""

replace_once(settings_file, old_settings, new_settings, "add pause toggle to diagnostics header")

print("All patches applied.")
PYEOF

echo
echo "=== git status --short ==="
git status --short

echo
echo "=== git diff --stat ==="
git diff --stat
