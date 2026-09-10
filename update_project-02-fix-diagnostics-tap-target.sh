#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

SETTINGS_FILE="$REPO_ROOT/app/src/main/java/com/wayars/app/presentation/ui/screen/settings/SettingsScreen.kt"

if [ ! -f "$SETTINGS_FILE" ]; then
  echo "ERROR: expected file not found: $SETTINGS_FILE" >&2
  exit 1
fi

python3 - "$SETTINGS_FILE" <<'PYEOF'
import sys

settings_file = sys.argv[1]

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

old_block = """    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                if (entries.isEmpty()) {
                    Text(
                        "\u041f\u043e\u043a\u0430 \u043f\u0443\u0441\u0442\u043e. \u0412\u043a\u043b\u044e\u0447\u0438 Active, \u043e\u0442\u043a\u0440\u043e\u0439 Bolt \u0438\u043b\u0438 Wolt \u043d\u0430 \u044d\u043a\u0440\u0430\u043d\u0435 \u0437\u0430\u043a\u0430\u0437\u0430 \u0438 \u043f\u043e\u0434\u043e\u0436\u0434\u0438 \u043f\u0430\u0440\u0443 \u0441\u0435\u043a\u0443\u043d\u0434.",
                        color = WaTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    entries.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.packageName,
                                    color = if (entry.matchedSupportedApp) WaNeonGreen else Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${timeFormat.format(java.util.Date(entry.timestampMillis))} \u00b7 \u043e\u043a\u043d\u043e: ${if (entry.windowFound) "\u043d\u0430\u0439\u0434\u0435\u043d\u043e" else "\u043d\u0435\u0442"} \u00b7 \u0442\u0435\u043a\u0441\u0442\u043e\u0432: ${entry.textsCollected}",
                                    color = WaTextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (entry.parsedSummary != null) {
                                    Text(
                                        entry.parsedSummary,
                                        color = if (entry.parsedSummary.startsWith("OK")) WaNeonGreen else WaTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (entry.rawTexts.isNotEmpty()) {
                                    var rawExpanded by remember { mutableStateOf(false) }
                                    Text(
                                        if (rawExpanded) "\u0442\u0435\u043a\u0441\u0442\u044b: \u0441\u043a\u0440\u044b\u0442\u044c" else "\u0442\u0435\u043a\u0441\u0442\u044b: \u043f\u043e\u043a\u0430\u0437\u0430\u0442\u044c (${entry.rawTexts.size})",
                                        color = WaTextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.clickable { rawExpanded = !rawExpanded }
                                    )
                                    AnimatedVisibility(visible = rawExpanded) {
                                        Text(
                                            entry.rawTexts.joinToString("\\n") { "\u2022 $it" },
                                            color = WaTextSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}"""

new_block = """    Column(
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
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                if (entries.isEmpty()) {
                    Text(
                        "\u041f\u043e\u043a\u0430 \u043f\u0443\u0441\u0442\u043e. \u0412\u043a\u043b\u044e\u0447\u0438 Active, \u043e\u0442\u043a\u0440\u043e\u0439 Bolt \u0438\u043b\u0438 Wolt \u043d\u0430 \u044d\u043a\u0440\u0430\u043d\u0435 \u0437\u0430\u043a\u0430\u0437\u0430 \u0438 \u043f\u043e\u0434\u043e\u0436\u0434\u0438 \u043f\u0430\u0440\u0443 \u0441\u0435\u043a\u0443\u043d\u0434.",
                        color = WaTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    entries.forEach { entry ->
                        var rawExpanded by remember(entry.timestampMillis) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                // Whole row toggles this entry's raw texts now,
                                // not just the small "\u043f\u043e\u043a\u0430\u0437\u0430\u0442\u044c" label \u2014 and
                                // it's a normal per-entry clickable, so it
                                // consumes the tap here and never reaches the
                                // header's section-collapse handler above.
                                .clickable(enabled = entry.rawTexts.isNotEmpty()) { rawExpanded = !rawExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.packageName,
                                    color = if (entry.matchedSupportedApp) WaNeonGreen else Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${timeFormat.format(java.util.Date(entry.timestampMillis))} \u00b7 \u043e\u043a\u043d\u043e: ${if (entry.windowFound) "\u043d\u0430\u0439\u0434\u0435\u043d\u043e" else "\u043d\u0435\u0442"} \u00b7 \u0442\u0435\u043a\u0441\u0442\u043e\u0432: ${entry.textsCollected}",
                                    color = WaTextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (entry.parsedSummary != null) {
                                    Text(
                                        entry.parsedSummary,
                                        color = if (entry.parsedSummary.startsWith("OK")) WaNeonGreen else WaTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (entry.rawTexts.isNotEmpty()) {
                                    Text(
                                        if (rawExpanded) "\u0442\u0435\u043a\u0441\u0442\u044b: \u0441\u043a\u0440\u044b\u0442\u044c" else "\u0442\u0435\u043a\u0441\u0442\u044b: \u043f\u043e\u043a\u0430\u0437\u0430\u0442\u044c (${entry.rawTexts.size})",
                                        color = WaTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    AnimatedVisibility(visible = rawExpanded) {
                                        Text(
                                            entry.rawTexts.joinToString("\\n") { "\u2022 $it" },
                                            color = WaTextSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}"""

replace_once(settings_file, old_block, new_block, "DiagnosticsSection tap-target restructure")
print("Patch applied.")
PYEOF

echo
echo "=== git status --short ==="
git status --short

echo
echo "=== git diff --stat ==="
git diff --stat
