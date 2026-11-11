#!/usr/bin/env bash
# update_project-12-tax-and-net-overlay.sh  (WayArs)
#
# 0) FIX THE BROKEN BUILD. The GitHub build fails because updates 07 and 11 were only half applied in the repo
#    (new Kotlin files exist, but string resources, the play app-update dependency and the Settings/NavHost
#    patches are missing -> "Unresolved reference tour_*, update_*, AppUpdateManagerFactory"). This script first
#    re-runs update_project-07 ... 11 (all are safe to run twice) so the repo ends up fully consistent.
#    That also brings in the Google Play fix (update 11, items 16/17): detailed AccessibilityService prominent
#    disclosure, shown on first launch and before enabling the service.
# 1) Settings: new "Tax" card (percent of each order). Saved in DataStore.
# 2) After saving, every NEW order is evaluated with that tax:
#       take-home = order amount - fuel (only if fuel data entered) - tax
# 3) Overlay: the BIG number is now the take-home ("Net"); the order amount (gross) moved below it, smaller;
#    a small line shows what was subtracted ("fuel -X . tax -Y"). Same logic in the in-app verdict card.
#    Verdict and thresholds still use fuel-only net profit per km/min, so your thresholds keep their meaning.
# 4) Texts in all 17 languages. Tour indices shifted by +1 for permissions/apps (new card in Settings).
# Idempotent. Run from the WayArs repo root, BEFORE git add/commit/push.
set -e
cd "$(dirname "$0")"
[ -f app/src/main/AndroidManifest.xml ] || { echo "Run this script from the WayArs repo root"; exit 1; }

# Updates 07..11 are applied only once (a marker file remembers it), so running this script again is harmless.
if [ ! -f .update12_base_applied ]; then
  for n in 07 08 09 10 11; do
    f=$(ls update_project-$n-*.sh 2>/dev/null | head -n1 || true)
    if [ -n "$f" ]; then
      echo "=== re-applying $f"
      bash "$f"
    else
      echo "!!! update_project-$n-*.sh not found next to this script - copy it into the repo root first"; exit 1
    fi
  done
  echo "updates 07-11 applied" > .update12_base_applied
else
  echo "=== updates 07-11 already applied here (marker .update12_base_applied) - skipping"
fi

echo "=== applying update 12"
python3 - <<'PYEOF'
# -*- coding: utf-8 -*-
import io, os, re, sys

P = "app/src/main/java/com/wayars/app/"
RES = "app/src/main/res/"

def read(p):
    return io.open(p, encoding="utf-8").read()

def write(p, s):
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)
    print("updated", p)

def edit(path, pairs):
    """pairs: list of (old, new, marker). Skips a pair if marker already present (idempotent)."""
    s = read(path)
    changed = False
    for old, new, marker in pairs:
        if marker in s:
            continue
        if s.count(old) != 1:
            raise SystemExit("PATTERN NOT FOUND (or not unique) in %s:\n%s" % (path, old[:120]))
        s = s.replace(old, new, 1)
        changed = True
    if changed:
        write(path, s)
    else:
        print("already OK", path)

# ------------------------------------------------------------------ domain model
edit(P + "domain/model/OrderEvaluation.kt", [
    ("    val fuelDataEntered: Boolean = false\n) {",
     "    val fuelDataEntered: Boolean = false,\n"
     "    /** Tax rate (percent of the order amount) the driver entered in Settings; 0 = not set. */\n"
     "    val taxPercent: Double = 0.0,\n"
     "    /** Tax in the driver's currency: earnings * taxPercent / 100. */\n"
     "    val taxAmount: Double = 0.0,\n"
     "    /** What the driver really keeps: earnings minus fuel (only if fuel data was entered) minus tax. */\n"
     "    val takeHome: Double = earnings\n) {",
     "val takeHome: Double"),
])

edit(P + "domain/usecase/EvaluateOrderUseCase.kt", [
    ("        customThresholds: CustomThresholds? = null\n    ): OrderEvaluation {",
     "        customThresholds: CustomThresholds? = null,\n        taxPercent: Double = 0.0\n    ): OrderEvaluation {",
     "taxPercent: Double = 0.0"),
    ("        val netProfit = earnings - fuelCost\n",
     "        val netProfit = earnings - fuelCost\n"
     "        // Tax is a percentage of the order amount. It only affects the \"take-home\" figure shown to the\n"
     "        // driver; the verdict and the per-km / per-minute rates stay based on fuel-only net profit, so the\n"
     "        // driver's own thresholds keep their meaning.\n"
     "        val taxAmount = if (taxPercent > 0.0) earnings * taxPercent / 100.0 else 0.0\n"
     "        val takeHome = earnings - (if (vehicleProfile.hasFuelCostData()) fuelCost else 0.0) - taxAmount\n",
     "val taxAmount"),
    ("            fuelDataEntered = vehicleProfile.hasFuelCostData()\n        )",
     "            fuelDataEntered = vehicleProfile.hasFuelCostData(),\n"
     "            taxPercent = taxPercent,\n"
     "            taxAmount = taxAmount,\n"
     "            takeHome = takeHome\n        )",
     "takeHome = takeHome"),
])

# ------------------------------------------------------------------ settings storage
edit(P + "data/prefs/SettingsDataStore.kt", [
    ("        val VEHICLE_PROMPT_COUNT = intPreferencesKey(\"vehicle_prompt_count\")\n",
     "        val VEHICLE_PROMPT_COUNT = intPreferencesKey(\"vehicle_prompt_count\")\n"
     "        val TAX_PERCENT = doublePreferencesKey(\"tax_percent\")\n",
     "TAX_PERCENT = doublePreferencesKey"),
    ("    val vehicleProfile: Flow<VehicleProfile> = context.dataStore.data.map { prefs ->",
     "    /** Tax rate in percent of each order amount (0 = not set). */\n"
     "    val taxPercent: Flow<Double> = context.dataStore.data.map { it[Keys.TAX_PERCENT] ?: 0.0 }\n\n"
     "    suspend fun setTaxPercent(percent: Double) {\n"
     "        context.dataStore.edit { prefs ->\n"
     "            if (percent > 0.0) prefs[Keys.TAX_PERCENT] = percent else prefs.remove(Keys.TAX_PERCENT)\n"
     "        }\n"
     "    }\n\n"
     "    val vehicleProfile: Flow<VehicleProfile> = context.dataStore.data.map { prefs ->",
     "val taxPercent: Flow<Double>"),
])

edit(P + "domain/repository/SettingsRepository.kt", [
    ("    val vehiclePromptCount: Flow<Int>\n",
     "    val vehiclePromptCount: Flow<Int>\n    val taxPercent: Flow<Double>\n",
     "val taxPercent: Flow<Double>"),
    ("    suspend fun setVehicleProfile(profile: VehicleProfile)\n",
     "    suspend fun setVehicleProfile(profile: VehicleProfile)\n    suspend fun setTaxPercent(percent: Double)\n",
     "suspend fun setTaxPercent"),
])

edit(P + "data/repository/SettingsRepositoryImpl.kt", [
    ("    override val vehiclePromptCount: Flow<Int> = store.vehiclePromptCount\n",
     "    override val vehiclePromptCount: Flow<Int> = store.vehiclePromptCount\n"
     "    override val taxPercent: Flow<Double> = store.taxPercent\n",
     "override val taxPercent"),
    ("    override suspend fun setVehicleProfile(profile: VehicleProfile) = store.setVehicleProfile(profile)\n",
     "    override suspend fun setVehicleProfile(profile: VehicleProfile) = store.setVehicleProfile(profile)\n"
     "    override suspend fun setTaxPercent(percent: Double) = store.setTaxPercent(percent)\n",
     "override suspend fun setTaxPercent"),
])

edit(P + "presentation/MainViewModel.kt", [
    ("    /** Times the vehicle prompt was shown.",
     "    /** Tax rate in percent of each order amount (0 = not set). */\n"
     "    val taxPercent: StateFlow<Double> =\n"
     "        settings.taxPercent.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)\n\n"
     "    /** Times the vehicle prompt was shown.",
     "val taxPercent: StateFlow<Double>"),
    ("    fun setVehicleProfile(profile: VehicleProfile) = viewModelScope.launch { settings.setVehicleProfile(profile) }\n",
     "    fun setVehicleProfile(profile: VehicleProfile) = viewModelScope.launch { settings.setVehicleProfile(profile) }\n"
     "    fun setTaxPercent(percent: Double) = viewModelScope.launch { settings.setTaxPercent(percent) }\n",
     "fun setTaxPercent"),
])

# ------------------------------------------------------------------ services (feed the tax into every evaluation)
edit(P + "service/accessibility/OrderAccessibilityService.kt", [
    ("    private var currentVehicleProfile: VehicleProfile = VehicleProfile.DEFAULT\n",
     "    private var currentVehicleProfile: VehicleProfile = VehicleProfile.DEFAULT\n    private var currentTaxPercent: Double = 0.0\n",
     "currentTaxPercent: Double"),
    ("        scope.launch {\n            container.settingsRepository.vehicleProfile.collect { currentVehicleProfile = it }\n        }\n",
     "        scope.launch {\n            container.settingsRepository.vehicleProfile.collect { currentVehicleProfile = it }\n        }\n"
     "        scope.launch {\n            container.settingsRepository.taxPercent.collect { currentTaxPercent = it }\n        }\n",
     "taxPercent.collect"),
    ("            vehicleProfile = currentVehicleProfile,\n            customThresholds = currentCustomThresholds\n        )",
     "            vehicleProfile = currentVehicleProfile,\n            customThresholds = currentCustomThresholds,\n            taxPercent = currentTaxPercent\n        )",
     "taxPercent = currentTaxPercent"),
])

edit(P + "service/notification/OrderNotificationListenerService.kt", [
    ("    private var currentVehicleProfile: VehicleProfile = VehicleProfile.DEFAULT\n",
     "    private var currentVehicleProfile: VehicleProfile = VehicleProfile.DEFAULT\n    private var currentTaxPercent: Double = 0.0\n",
     "currentTaxPercent: Double"),
    ("        scope.launch { container.settingsRepository.vehicleProfile.collect { currentVehicleProfile = it } }\n",
     "        scope.launch { container.settingsRepository.vehicleProfile.collect { currentVehicleProfile = it } }\n"
     "        scope.launch { container.settingsRepository.taxPercent.collect { currentTaxPercent = it } }\n",
     "taxPercent.collect"),
    ("            vehicleProfile = currentVehicleProfile,\n            customThresholds = currentCustomThresholds\n        )",
     "            vehicleProfile = currentVehicleProfile,\n            customThresholds = currentCustomThresholds,\n            taxPercent = currentTaxPercent\n        )",
     "taxPercent = currentTaxPercent"),
])

# ------------------------------------------------------------------ navigation plumbing
edit(P + "presentation/ui/navigation/WayArsNavHost.kt", [
    ("    val vehicleProfile by viewModel.vehicleProfile.collectAsState()\n",
     "    val vehicleProfile by viewModel.vehicleProfile.collectAsState()\n    val taxPercent by viewModel.taxPercent.collectAsState()\n",
     "val taxPercent by"),
    ("                    onSaveVehicleProfile = { viewModel.setVehicleProfile(it) },\n",
     "                    onSaveVehicleProfile = { viewModel.setVehicleProfile(it) },\n"
     "                    taxPercent = taxPercent,\n"
     "                    onSaveTaxPercent = { viewModel.setTaxPercent(it) },\n",
     "onSaveTaxPercent = {"),
])

edit(P + "presentation/ui/navigation/MainScreen.kt", [
    ("    onSaveVehicleProfile: (VehicleProfile) -> Unit,\n",
     "    onSaveVehicleProfile: (VehicleProfile) -> Unit,\n"
     "    taxPercent: Double = 0.0,\n"
     "    onSaveTaxPercent: (Double) -> Unit = {},\n",
     "onSaveTaxPercent: (Double)"),
    ("                    onSaveVehicleProfile = onSaveVehicleProfile,\n",
     "                    onSaveVehicleProfile = onSaveVehicleProfile,\n"
     "                    taxPercent = taxPercent,\n"
     "                    onSaveTaxPercent = onSaveTaxPercent,\n",
     "onSaveTaxPercent = onSaveTaxPercent"),
    # the new Tax card sits right after the vehicle card, so "permissions" moves from item 5 to item 6
    ("settingsListState.animateScrollToItem(5)", "settingsListState.animateScrollToItem(6)", "animateScrollToItem(6)"),
])

edit(P + "presentation/ui/tour/TourController.kt", [
    ("TourTarget.SETTINGS_PERMISSIONS, MainTab.SETTINGS, 5,", "TourTarget.SETTINGS_PERMISSIONS, MainTab.SETTINGS, 6,", "SETTINGS_PERMISSIONS, MainTab.SETTINGS, 6,"),
    ("TourTarget.SETTINGS_APPS, MainTab.SETTINGS, 6,", "TourTarget.SETTINGS_APPS, MainTab.SETTINGS, 7,", "SETTINGS_APPS, MainTab.SETTINGS, 7,"),
])

# ------------------------------------------------------------------ Settings screen: Tax card
SETTINGS = P + "presentation/ui/screen/settings/SettingsScreen.kt"
edit(SETTINGS, [
    ("    onSaveVehicleProfile: (VehicleProfile) -> Unit,\n    customPackages: Set<String>,",
     "    onSaveVehicleProfile: (VehicleProfile) -> Unit,\n"
     "    taxPercent: Double = 0.0,\n"
     "    onSaveTaxPercent: (Double) -> Unit = {},\n"
     "    customPackages: Set<String>,",
     "onSaveTaxPercent: (Double)"),
    ("                    openRequest = vehiclePrompt,\n                    onRequestHandled = onVehiclePromptHandled\n                )\n            }\n        }\n",
     "                    openRequest = vehiclePrompt,\n                    onRequestHandled = onVehiclePromptHandled\n                )\n            }\n        }\n\n"
     "        item {\n"
     "            TaxSection(taxPercent = taxPercent, onSave = onSaveTaxPercent)\n"
     "        }\n",
     "TaxSection(taxPercent = taxPercent"),
    ("import androidx.compose.material.icons.filled.Check\n",
     "import androidx.compose.material.icons.filled.AccountBalance\nimport androidx.compose.material.icons.filled.Check\n",
     "filled.AccountBalance"),
    ("@Composable\nprivate fun ChoiceChip(",
     '''private fun formatTaxPercent(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** Driver's tax rate (percent of each order amount). New orders show the take-home amount after fuel and tax. */
@Composable
private fun TaxSection(
    taxPercent: Double,
    onSave: (Double) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var text by remember(taxPercent) {
        mutableStateOf(if (taxPercent > 0.0) formatTaxPercent(taxPercent) else "")
    }
    val summary = if (taxPercent > 0.0) {
        stringResource(R.string.settings_tax_summary, formatTaxPercent(taxPercent))
    } else {
        stringResource(R.string.settings_tax_summary_none)
    }

    Column(
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
            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = WaTextSecondary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    stringResource(R.string.settings_tax_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    summary,
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
                Text(
                    stringResource(R.string.settings_tax_desc),
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.padding(top = 12.dp))
                ThresholdField(
                    label = stringResource(R.string.settings_tax_label),
                    value = text,
                    accent = WaNeonGreen,
                    onValueChange = { text = it }
                )
                Spacer(Modifier.padding(top = 14.dp))
                TextButton(
                    onClick = {
                        val percent = (text.replace(',', '.').toDoubleOrNull() ?: 0.0).coerceIn(0.0, 99.0)
                        onSave(percent)
                        expanded = false
                        Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.settings_vehicle_save), color = WaNeonGreen)
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(''',
     "private fun TaxSection("),
])

# ------------------------------------------------------------------ shared helper: "fuel -X · tax -Y"
HELPER = P + "presentation/ui/component/DeductionsText.kt"
if not os.path.exists(HELPER):
    write(HELPER, '''package com.wayars.app.presentation.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wayars.app.R
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.util.CurrencyFormatter

/**
 * What was subtracted from the order amount to get the take-home figure, e.g. "fuel -3.20 · tax -4.00".
 * Null when nothing was subtracted (no fuel data entered and no tax set).
 */
@Composable
fun deductionsLine(evaluation: OrderEvaluation): String? {
    val parts = ArrayList<String>(2)
    if (evaluation.fuelDataEntered && evaluation.fuelCost > 0.0) {
        parts.add(stringResource(R.string.overlay_deduction_fuel, CurrencyFormatter.format(evaluation.fuelCost, evaluation.currency)))
    }
    if (evaluation.taxAmount > 0.0) {
        parts.add(stringResource(R.string.overlay_deduction_tax, CurrencyFormatter.format(evaluation.taxAmount, evaluation.currency)))
    }
    return if (parts.isEmpty()) null else parts.joinToString(" \\u00B7 ")
}
''')
else:
    print("already OK", HELPER)

# ------------------------------------------------------------------ Overlay: net amount is the big number, gross goes below
edit(P + "presentation/widget/OverlayContent.kt", [
    ("            Spacer(Modifier.height(5.dp))\n"
     "            Text(\n"
     "                CurrencyFormatter.format(evaluation.earnings, evaluation.currency),\n"
     "                fontSize = 21.sp,\n"
     "                fontWeight = FontWeight.Bold,\n"
     "                color = MaterialTheme.colorScheme.onSurface\n"
     "            )\n",
     "            Spacer(Modifier.height(5.dp))\n"
     "            // The big number is what the driver really keeps (after fuel and tax); the order amount\n"
     "            // as shown by the source app (gross) is repeated smaller underneath.\n"
     "            Text(\n"
     "                stringResource(R.string.overlay_net_caption),\n"
     "                fontSize = 10.sp,\n"
     "                color = WaTextSecondary\n"
     "            )\n"
     "            Text(\n"
     "                CurrencyFormatter.format(evaluation.takeHome, evaluation.currency),\n"
     "                fontSize = 24.sp,\n"
     "                fontWeight = FontWeight.Bold,\n"
     "                color = MaterialTheme.colorScheme.onSurface\n"
     "            )\n"
     "            Text(\n"
     "                stringResource(R.string.overlay_gross, CurrencyFormatter.format(evaluation.earnings, evaluation.currency)),\n"
     "                fontSize = 12.sp,\n"
     "                color = WaTextSecondary\n"
     "            )\n",
     "R.string.overlay_net_caption"),
    ("stringResource(R.string.verdict_short_trip_net, CurrencyFormatter.format(evaluation.netProfit, evaluation.currency))",
     "stringResource(R.string.verdict_short_trip_net, CurrencyFormatter.format(evaluation.takeHome, evaluation.currency))",
     "verdict_short_trip_net, CurrencyFormatter.format(evaluation.takeHome"),
    ("            // Net profit after fuel. Shown only when the driver entered fuel consumption AND price in the\n"
     "            // vehicle settings, and the trip is long enough: under 1 km the badge above already shows\n"
     "            // the net profit.\n"
     "            if (evaluation.fuelDataEntered && evaluation.fuelCost > 0 && evaluation.distanceKm >= OrderEvaluation.SHORT_TRIP_THRESHOLD_KM) {\n"
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
     "            }\n",
     "            // What was subtracted from the order amount: fuel (only if the driver entered fuel data) and tax.\n"
     "            val deductions = deductionsLine(evaluation)\n"
     "            if (deductions != null) {\n"
     "                Spacer(Modifier.height(5.dp))\n"
     "                Text(\n"
     "                    deductions,\n"
     "                    fontSize = 10.sp,\n"
     "                    color = WaTextSecondary\n"
     "                )\n"
     "            }\n",
     "deductionsLine(evaluation)"),
    ("import com.wayars.app.presentation.ui.component.verdictColor\n",
     "import com.wayars.app.presentation.ui.component.deductionsLine\nimport com.wayars.app.presentation.ui.component.verdictColor\n",
     "component.deductionsLine"),
])

# ------------------------------------------------------------------ In-app verdict card: same take-home logic
edit(P + "presentation/ui/component/VerdictCard.kt", [
    ("stringResource(R.string.verdict_short_trip_net, CurrencyFormatter.format(evaluation.netProfit, evaluation.currency))",
     "stringResource(R.string.verdict_short_trip_net, CurrencyFormatter.format(evaluation.takeHome, evaluation.currency))",
     "verdict_short_trip_net, CurrencyFormatter.format(evaluation.takeHome"),
    ("        if (evaluation.fuelDataEntered && evaluation.fuelCost > 0) {\n"
     "            Text(\n"
     "                stringResource(\n"
     "                    R.string.verdict_net_profit,\n"
     "                    CurrencyFormatter.format(evaluation.netProfit, evaluation.currency),\n"
     "                    CurrencyFormatter.format(evaluation.fuelCost, evaluation.currency)\n"
     "                ),\n",
     "        val deductions = deductionsLine(evaluation)\n"
     "        if (deductions != null) {\n"
     "            Text(\n"
     "                stringResource(\n"
     "                    R.string.verdict_take_home,\n"
     "                    CurrencyFormatter.format(evaluation.takeHome, evaluation.currency),\n"
     "                    deductions\n"
     "                ),\n",
     "R.string.verdict_take_home"),
])

# ------------------------------------------------------------------ strings (all 17 languages)
T = {
 "en": dict(
  settings_tax_title="Tax",
  settings_tax_summary_none="Not set",
  settings_tax_summary="%1$s%% of the order amount",
  settings_tax_desc="Enter the percent of each order that you pay as tax. After saving, new orders show the net amount after fuel and tax.",
  settings_tax_label="Tax rate, %",
  overlay_net_caption="Net",
  overlay_gross="Order amount %1$s",
  overlay_deduction_fuel="fuel −%1$s",
  overlay_deduction_tax="tax −%1$s",
  verdict_take_home="Net %1$s (%2$s)"),
 "ru": dict(
  settings_tax_title="Налог",
  settings_tax_summary_none="Не задан",
  settings_tax_summary="%1$s%% от суммы заказа",
  settings_tax_desc="Укажите, какой процент с каждого заказа вы платите в виде налога. После сохранения новые заказы покажут чистую сумму за вычетом топлива и налога.",
  settings_tax_label="Ставка налога, %",
  overlay_net_caption="Чисто",
  overlay_gross="Сумма заказа %1$s",
  overlay_deduction_fuel="топливо −%1$s",
  overlay_deduction_tax="налог −%1$s",
  verdict_take_home="Чисто %1$s (%2$s)"),
 "uk": dict(
  settings_tax_title="Податок",
  settings_tax_summary_none="Не задано",
  settings_tax_summary="%1$s%% від суми замовлення",
  settings_tax_desc="Вкажіть, який відсоток з кожного замовлення ви сплачуєте як податок. Після збереження нові замовлення показуватимуть чисту суму за вирахуванням пального та податку.",
  settings_tax_label="Ставка податку, %",
  overlay_net_caption="Чисто",
  overlay_gross="Сума замовлення %1$s",
  overlay_deduction_fuel="пальне −%1$s",
  overlay_deduction_tax="податок −%1$s",
  verdict_take_home="Чисто %1$s (%2$s)"),
 "pl": dict(
  settings_tax_title="Podatek",
  settings_tax_summary_none="Nie ustawiono",
  settings_tax_summary="%1$s%% kwoty zamówienia",
  settings_tax_desc="Wpisz, jaki procent każdego zamówienia płacisz jako podatek. Po zapisaniu nowe zamówienia pokażą kwotę netto po odliczeniu paliwa i podatku.",
  settings_tax_label="Stawka podatku, %",
  overlay_net_caption="Netto",
  overlay_gross="Kwota zamówienia %1$s",
  overlay_deduction_fuel="paliwo −%1$s",
  overlay_deduction_tax="podatek −%1$s",
  verdict_take_home="Netto %1$s (%2$s)"),
 "de": dict(
  settings_tax_title="Steuer",
  settings_tax_summary_none="Nicht festgelegt",
  settings_tax_summary="%1$s%% des Auftragsbetrags",
  settings_tax_desc="Gib an, wie viel Prozent jedes Auftrags du als Steuer zahlst. Nach dem Speichern zeigen neue Aufträge den Nettobetrag nach Abzug von Kraftstoff und Steuer.",
  settings_tax_label="Steuersatz, %",
  overlay_net_caption="Netto",
  overlay_gross="Auftragsbetrag %1$s",
  overlay_deduction_fuel="Kraftstoff −%1$s",
  overlay_deduction_tax="Steuer −%1$s",
  verdict_take_home="Netto %1$s (%2$s)"),
 "fr": dict(
  settings_tax_title="Impôt",
  settings_tax_summary_none="Non défini",
  settings_tax_summary="%1$s%% du montant de la course",
  settings_tax_desc="Indiquez le pourcentage de chaque course que vous payez en impôt. Après l’enregistrement, les nouvelles courses affichent le montant net après carburant et impôt.",
  settings_tax_label="Taux d’impôt, %",
  overlay_net_caption="Net",
  overlay_gross="Montant de la course %1$s",
  overlay_deduction_fuel="carburant −%1$s",
  overlay_deduction_tax="impôt −%1$s",
  verdict_take_home="Net %1$s (%2$s)"),
 "cs": dict(
  settings_tax_title="Daň",
  settings_tax_summary_none="Nenastaveno",
  settings_tax_summary="%1$s%% z částky objednávky",
  settings_tax_desc="Zadejte, kolik procent z každé objednávky platíte na daních. Po uložení budou nové objednávky zobrazovat čistou částku po odečtení paliva a daně.",
  settings_tax_label="Sazba daně, %",
  overlay_net_caption="Čistě",
  overlay_gross="Částka objednávky %1$s",
  overlay_deduction_fuel="palivo −%1$s",
  overlay_deduction_tax="daň −%1$s",
  verdict_take_home="Čistě %1$s (%2$s)"),
 "da": dict(
  settings_tax_title="Skat",
  settings_tax_summary_none="Ikke angivet",
  settings_tax_summary="%1$s%% af ordrebeløbet",
  settings_tax_desc="Angiv, hvor mange procent af hver ordre du betaler i skat. Efter gemning viser nye ordrer nettobeløbet efter brændstof og skat.",
  settings_tax_label="Skattesats, %",
  overlay_net_caption="Netto",
  overlay_gross="Ordrebeløb %1$s",
  overlay_deduction_fuel="brændstof −%1$s",
  overlay_deduction_tax="skat −%1$s",
  verdict_take_home="Netto %1$s (%2$s)"),
 "nb": dict(
  settings_tax_title="Skatt",
  settings_tax_summary_none="Ikke angitt",
  settings_tax_summary="%1$s%% av ordrebeløpet",
  settings_tax_desc="Oppgi hvor mange prosent av hver ordre du betaler i skatt. Etter lagring viser nye ordrer nettobeløpet etter drivstoff og skatt.",
  settings_tax_label="Skattesats, %",
  overlay_net_caption="Netto",
  overlay_gross="Ordrebeløp %1$s",
  overlay_deduction_fuel="drivstoff −%1$s",
  overlay_deduction_tax="skatt −%1$s",
  verdict_take_home="Netto %1$s (%2$s)"),
 "sv": dict(
  settings_tax_title="Skatt",
  settings_tax_summary_none="Inte angiven",
  settings_tax_summary="%1$s%% av ordersumman",
  settings_tax_desc="Ange hur många procent av varje order du betalar i skatt. Efter att du sparat visar nya ordrar nettobeloppet efter bränsle och skatt.",
  settings_tax_label="Skattesats, %",
  overlay_net_caption="Netto",
  overlay_gross="Ordersumma %1$s",
  overlay_deduction_fuel="bränsle −%1$s",
  overlay_deduction_tax="skatt −%1$s",
  verdict_take_home="Netto %1$s (%2$s)"),
 "ro": dict(
  settings_tax_title="Impozit",
  settings_tax_summary_none="Nesetat",
  settings_tax_summary="%1$s%% din suma comenzii",
  settings_tax_desc="Introdu ce procent din fiecare comandă plătești ca impozit. După salvare, comenzile noi vor arăta suma netă după scăderea combustibilului și a impozitului.",
  settings_tax_label="Cota de impozit, %",
  overlay_net_caption="Net",
  overlay_gross="Suma comenzii %1$s",
  overlay_deduction_fuel="combustibil −%1$s",
  overlay_deduction_tax="impozit −%1$s",
  verdict_take_home="Net %1$s (%2$s)"),
 "hu": dict(
  settings_tax_title="Adó",
  settings_tax_summary_none="Nincs megadva",
  settings_tax_summary="A rendelés összegének %1$s%%-a",
  settings_tax_desc="Add meg, hogy minden rendelés hány százalékát fizeted adóként. Mentés után az új rendeléseknél az üzemanyag és az adó levonása utáni nettó összeg jelenik meg.",
  settings_tax_label="Adókulcs, %",
  overlay_net_caption="Nettó",
  overlay_gross="Rendelés összege %1$s",
  overlay_deduction_fuel="üzemanyag −%1$s",
  overlay_deduction_tax="adó −%1$s",
  verdict_take_home="Nettó %1$s (%2$s)"),
 "bg": dict(
  settings_tax_title="Данък",
  settings_tax_summary_none="Не е зададен",
  settings_tax_summary="%1$s%% от сумата на поръчката",
  settings_tax_desc="Въведете какъв процент от всяка поръчка плащате като данък. След запазване новите поръчки ще показват чистата сума след изваждане на горивото и данъка.",
  settings_tax_label="Данъчна ставка, %",
  overlay_net_caption="Чисто",
  overlay_gross="Сума на поръчката %1$s",
  overlay_deduction_fuel="гориво −%1$s",
  overlay_deduction_tax="данък −%1$s",
  verdict_take_home="Чисто %1$s (%2$s)"),
 "az": dict(
  settings_tax_title="Vergi",
  settings_tax_summary_none="Təyin edilməyib",
  settings_tax_summary="Sifariş məbləğinin %1$s%%-i",
  settings_tax_desc="Hər sifarişdən vergi kimi neçə faiz ödədiyinizi daxil edin. Yadda saxladıqdan sonra yeni sifarişlər yanacaq və vergi çıxıldıqdan sonrakı xalis məbləği göstərəcək.",
  settings_tax_label="Vergi dərəcəsi, %",
  overlay_net_caption="Xalis",
  overlay_gross="Sifariş məbləği %1$s",
  overlay_deduction_fuel="yanacaq −%1$s",
  overlay_deduction_tax="vergi −%1$s",
  verdict_take_home="Xalis %1$s (%2$s)"),
 "uz": dict(
  settings_tax_title="Soliq",
  settings_tax_summary_none="Belgilanmagan",
  settings_tax_summary="Buyurtma summasining %1$s%%",
  settings_tax_desc="Har bir buyurtmadan soliq sifatida necha foiz to‘lashingizni kiriting. Saqlangandan so‘ng yangi buyurtmalarda yoqilg‘i va soliq ayirilgandan keyingi sof summa ko‘rsatiladi.",
  settings_tax_label="Soliq stavkasi, %",
  overlay_net_caption="Sof",
  overlay_gross="Buyurtma summasi %1$s",
  overlay_deduction_fuel="yoqilg‘i −%1$s",
  overlay_deduction_tax="soliq −%1$s",
  verdict_take_home="Sof %1$s (%2$s)"),
 "ka": dict(
  settings_tax_title="გადასახადი",
  settings_tax_summary_none="არ არის მითითებული",
  settings_tax_summary="შეკვეთის თანხის %1$s%%",
  settings_tax_desc="მიუთითეთ, თითოეული შეკვეთიდან რამდენ პროცენტს იხდით გადასახადად. შენახვის შემდეგ ახალი შეკვეთები აჩვენებს წმინდა თანხას საწვავისა და გადასახადის გამოკლებით.",
  settings_tax_label="გადასახადის განაკვეთი, %",
  overlay_net_caption="წმინდა",
  overlay_gross="შეკვეთის თანხა %1$s",
  overlay_deduction_fuel="საწვავი −%1$s",
  overlay_deduction_tax="გადასახადი −%1$s",
  verdict_take_home="წმინდა %1$s (%2$s)"),
 "hy": dict(
  settings_tax_title="Հարկ",
  settings_tax_summary_none="Սահմանված չէ",
  settings_tax_summary="Պատվերի գումարի %1$s%%-ը",
  settings_tax_desc="Նշեք, յուրաքանչյուր պատվերից քանի տոկոս եք վճարում որպես հարկ։ Պահպանելուց հետո նոր պատվերները կցուցադրեն մաքուր գումարը՝ վառելիքի և հարկի հանումից հետո։",
  settings_tax_label="Հարկի դրույք, %",
  overlay_net_caption="Մաքուր",
  overlay_gross="Պատվերի գումար %1$s",
  overlay_deduction_fuel="վառելիք −%1$s",
  overlay_deduction_tax="հարկ −%1$s",
  verdict_take_home="Մաքուր %1$s (%2$s)"),
}

def xml_escape(v):
    v = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    v = v.replace("'", "\\'").replace('"', '\\"')
    return v

for lang, d in T.items():
    path = RES + ("values/strings.xml" if lang == "en" else "values-%s/strings.xml" % lang)
    if not os.path.exists(path):
        print("skip (no file):", path)
        continue
    s = read(path)
    add = ""
    for k, v in d.items():
        if ('name="%s"' % k) in s:
            continue
        add += '    <string name="%s">%s</string>\n' % (k, xml_escape(v))
    if add:
        i = s.rindex("</resources>")
        s = s[:i] + add + s[i:]
        write(path, s)
    else:
        print("already OK", path)
print("DONE")
PYEOF

echo
echo "Done. Review with git diff --stat, then:"
echo "  git add -A && git commit -m 'Fix build, Play disclosure, tax setting, net amount in overlay' && git push"
