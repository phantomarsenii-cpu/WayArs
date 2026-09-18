#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

APP_FILE="$REPO_ROOT/app/src/main/java/com/wayars/app/WayArsApplication.kt"

if [ ! -f "$APP_FILE" ]; then
  echo "ERROR: expected file not found: $APP_FILE" >&2
  exit 1
fi

python3 - "$APP_FILE" <<'PYEOF'
import sys

app_file = sys.argv[1]

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

# --- WayArsApplication.kt: persist the auto-detected language on first launch ---
old_oncreate = """    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        configureRevenueCat()
        observeAppForeground()
    }"""

new_oncreate = """    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        configureRevenueCat()
        observeAppForeground()
        persistResolvedLanguageIfMissing()
    }

    /**
     * attachBaseContext() below resolves and *applies* a language on every
     * cold start (device locale, if supported, else English), but until now
     * that resolved value was never written back to LanguagePrefs/DataStore
     * unless the user explicitly opened Settings and picked a language.
     * Result: the UI could be running in Russian (correctly auto-detected)
     * while MainViewModel.languageCode stayed null and the Settings screen
     * fell back to its "en" default — a real mismatch between what's
     * displayed and what's shown as selected. Persist the resolved value
     * once, right after first launch, so both stay in sync from then on.
     */
    private fun persistResolvedLanguageIfMissing() {
        if (LanguagePrefs.read(this) != null) return
        val resolved = LocaleManager.resolveInitialLanguage()
        applicationScope.launch {
            container.settingsRepository.setLanguage(resolved)
        }
    }"""

replace_once(app_file, old_oncreate, new_oncreate, "persist resolved language on first launch")
PYEOF

echo "Done. Rebuild the app to verify."
