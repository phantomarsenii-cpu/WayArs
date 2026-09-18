#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

NAVHOST_FILE="$REPO_ROOT/app/src/main/java/com/wayars/app/presentation/ui/navigation/WayArsNavHost.kt"

if [ ! -f "$NAVHOST_FILE" ]; then
  echo "ERROR: expected file not found: $NAVHOST_FILE" >&2
  exit 1
fi

python3 - "$NAVHOST_FILE" <<'PYEOF'
import sys

navhost_file = sys.argv[1]

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

# --- 1. Add ScanningState import ---
old_import = """import com.wayars.app.presentation.ui.screen.splash.SplashScreen
import com.wayars.app.presentation.ui.theme.WaBackground
import com.wayars.app.util.findActivity"""

new_import = """import com.wayars.app.presentation.ui.screen.splash.SplashScreen
import com.wayars.app.presentation.ui.theme.WaBackground
import com.wayars.app.service.accessibility.ScanningState
import com.wayars.app.util.findActivity"""

replace_once(navhost_file, old_import, new_import, "add ScanningState import")

# --- 2. Turn scanning off the moment the paywall shows for a lapsed subscription ---
old_effect = """            composable(Routes.MAIN) {
                // Belt-and-suspenders: if the entitlement lapses while the
                // user is already inside the app (checked on every
                // foreground, per WayArsApplication's ProcessLifecycleOwner
                // observer), drop them back to the paywall immediately
                // instead of waiting for their next app restart.
                LaunchedEffect(gateState) {
                    if (gateState is SubscriptionState.NotSubscribed) {
                        navController.navigate(Routes.PAYWALL) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    }
                }"""

new_effect = """            composable(Routes.MAIN) {
                // Belt-and-suspenders: if the entitlement lapses while the
                // user is already inside the app (checked on every
                // foreground, per WayArsApplication's ProcessLifecycleOwner
                // observer), drop them back to the paywall immediately
                // instead of waiting for their next app restart.
                //
                // Getting bounced to the paywall isn't enough on its own,
                // though: scanning (ScanningState) is a separate switch that
                // keeps running until something explicitly turns it off, so
                // without this the accessibility service just kept scanning
                // behind the paywall for anyone who didn't also flip the
                // Dashboard "Active" toggle by hand. Turn it off here, same
                // as tapping that toggle off, the moment the paywall shows
                // for a lapsed subscription.
                val mainContext = LocalContext.current
                LaunchedEffect(gateState) {
                    if (gateState is SubscriptionState.NotSubscribed) {
                        ScanningState.setActive(false, mainContext)
                        navController.navigate(Routes.PAYWALL) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    }
                }"""

replace_once(navhost_file, old_effect, new_effect, "turn off scanning when dropped to paywall")
PYEOF

echo "Done. Rebuild the app to verify."
