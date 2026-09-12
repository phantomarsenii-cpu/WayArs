package com.wayars.app.presentation.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import com.wayars.app.R
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.FuelType
import com.wayars.app.domain.model.PackageHint
import com.wayars.app.domain.model.VehicleCategory
import com.wayars.app.domain.model.VehicleProfile
import com.wayars.app.presentation.ui.theme.WaAmber
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaRed
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.CurrencyFormatter
import com.wayars.app.util.LocaleManager

@Composable
fun SettingsScreen(
    languageCode: String,
    currency: Currency,
    customThresholds: CustomThresholds?,
    vehicleProfile: VehicleProfile,
    onLanguageSelected: (String) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSaveCustomThresholds: (bad: Double, average: Double, good: Double) -> Unit,
    onClearCustomThresholds: () -> Unit,
    onSaveVehicleProfile: (VehicleProfile) -> Unit,
    customPackages: Set<String>,
    onAddCustomPackage: (String) -> Unit,
    onRemoveCustomPackage: (String) -> Unit,
    packageHints: Map<String, PackageHint>,
    onSavePackageHint: (PackageHint) -> Unit,
    onClearPackageHint: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }

        item {
            SectionLabel(stringResource(R.string.settings_language))
            LanguagePicker(current = languageCode, onSelect = onLanguageSelected)
        }

        item {
            SectionLabel(stringResource(R.string.settings_currency))
            CurrencyPicker(current = currency, onSelect = onCurrencySelected)
        }

        item {
            CustomThresholdsSection(
                existing = customThresholds,
                currency = currency,
                onSave = onSaveCustomThresholds,
                onClear = onClearCustomThresholds
            )
        }

        item {
            VehicleSection(
                profile = vehicleProfile,
                currency = currency,
                onSave = onSaveVehicleProfile
            )
        }

        item {
            PermissionsSection(
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenNotificationSettings = onOpenNotificationSettings
            )
        }

        item {
            SupportedAppsSection(
                customPackages = customPackages,
                onAddCustomPackage = onAddCustomPackage,
                onRemoveCustomPackage = onRemoveCustomPackage,
                packageHints = packageHints,
                onSavePackageHint = onSavePackageHint,
                onClearPackageHint = onClearPackageHint
            )
        }
    }
}

/** Collapsed by default — groups all three permission buttons into one compact row. */
@Composable
private fun PermissionsSection(
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_permissions_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.settings_permissions_hint),
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
            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                InnerPermissionButton(
                    title = stringResource(R.string.settings_enable_accessibility),
                    hint = stringResource(R.string.settings_accessibility_hint),
                    onClick = onOpenAccessibilitySettings
                )
                InnerPermissionButton(
                    title = stringResource(R.string.settings_enable_overlay),
                    hint = stringResource(R.string.settings_overlay_hint),
                    onClick = onOpenOverlaySettings
                )
                InnerPermissionButton(
                    title = stringResource(R.string.settings_enable_notifications),
                    hint = stringResource(R.string.settings_notifications_hint),
                    onClick = onOpenNotificationSettings
                )
            }
        }
    }
}

@Composable
private fun InnerPermissionButton(title: String, hint: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WaSurfaceVariant)
            .padding(14.dp)
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        Text(hint, color = WaTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
        ) {
            Text(title)
        }
    }
}

/**
 * Every delivery/taxi app WayArs reads from is on ONE allow-list check
 * ([com.wayars.app.service.accessibility.OrderAccessibilityService.isSupportedPackage]):
 * a hardcoded default set plus whatever the user adds here. The scanner and
 * ScreenTextParser have no per-app code — they just need the package id on
 * that list — so this section is what makes "any courier/taxi app in the
 * world" actually reachable without a WayArs update: the user points the
 * app at a new package themselves, either by picking it from what's already
 * installed on their phone or by typing the id directly (useful for an app
 * they're about to install, or one they found the id for online).
 *
 * The built-in list itself is NOT editable here (no code path removes a
 * default) — only entries the user added can be removed.
 */
@Composable
private fun SupportedAppsSection(
    customPackages: Set<String>,
    onAddCustomPackage: (String) -> Unit,
    onRemoveCustomPackage: (String) -> Unit,
    packageHints: Map<String, PackageHint>,
    onSavePackageHint: (PackageHint) -> Unit,
    onClearPackageHint: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var calibratingPackage by remember { mutableStateOf<String?>(null) }

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
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_supported_apps_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.settings_supported_apps_hint),
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
            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.settings_supported_apps_builtin_label),
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                BUILTIN_SUPPORTED_PACKAGES_DISPLAY.forEach { line ->
                    Text("• $line", color = WaTextSecondary, style = MaterialTheme.typography.bodySmall)
                }

                if (customPackages.isNotEmpty()) {
                    Text(
                        stringResource(R.string.settings_supported_apps_custom_label),
                        color = WaTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    customPackages.sorted().forEach { pkg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pkg, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                val hasHint = packageHints[pkg]?.isEmpty == false
                                Text(
                                    if (hasHint) {
                                        stringResource(R.string.settings_calibrated)
                                    } else {
                                        stringResource(R.string.settings_not_calibrated)
                                    },
                                    color = if (hasHint) WaNeonGreen else WaTextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.clickable { calibratingPackage = pkg }
                                )
                            }
                            Text(
                                "✕",
                                color = WaRed,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onRemoveCustomPackage(pkg) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showAppPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = WaSurfaceVariant, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_add_from_installed), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { showManualEntry = !showManualEntry },
                        colors = ButtonDefaults.buttonColors(containerColor = WaSurfaceVariant, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_add_manually), style = MaterialTheme.typography.bodySmall)
                    }
                }

                AnimatedVisibility(visible = showManualEntry) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualText,
                            onValueChange = { manualText = it },
                            label = { Text(stringResource(R.string.settings_package_id_label)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaNeonGreen,
                                unfocusedBorderColor = WaTextSecondary,
                                focusedLabelColor = WaNeonGreen,
                                unfocusedLabelColor = WaTextSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            val trimmed = manualText.trim()
                            if (trimmed.isNotEmpty()) {
                                onAddCustomPackage(trimmed)
                                manualText = ""
                                showManualEntry = false
                            }
                        }) {
                            Text(stringResource(R.string.settings_add), color = WaNeonGreen)
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        InstalledAppsPickerDialog(
            alreadyAdded = customPackages,
            onDismiss = { showAppPicker = false },
            onPick = { pkg ->
                onAddCustomPackage(pkg)
                showAppPicker = false
            }
        )
    }

    calibratingPackage?.let { pkg ->
        CalibrationDialog(
            packageName = pkg,
            existingHint = packageHints[pkg],
            onDismiss = { calibratingPackage = null },
            onSave = { hint ->
                onSavePackageHint(hint)
                calibratingPackage = null
            },
            onClearHint = {
                onClearPackageHint(pkg)
                calibratingPackage = null
            }
        )
    }
}

// Display-only — package ids match OrderAccessibilityService.SUPPORTED_PACKAGES.
// Not read from there directly to avoid pulling an accessibility-service
// class into a Compose screen just for a label list; keep the two in sync
// by hand if that set changes.
private val BUILTIN_SUPPORTED_PACKAGES_DISPLAY = listOf(
    "Bolt Courier — com.bolt.deliverycourier",
    "Bolt Driver — ee.mtakso.driver",
    "Uber Driver — com.ubercab.driver",
    "Wolt Courier — com.wolt.courierapp",
    "FreeNow Driver — taxi.android.driver",
    "Stuart Courier — com.stuart.courier"
)

/**
 * Lets the user pick any app already installed on their phone instead of
 * having to know/type its package id by hand. Filtered to apps that have a
 * launcher entry (an icon the user actually taps to open) so the list isn't
 * flooded with system components and background services that could never
 * be the delivery/taxi app in the first place.
 */
@Composable
private fun InstalledAppsPickerDialog(
    alreadyAdded: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    val apps = remember {
        val pm = context.packageManager
        val installed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        installed
            .asSequence()
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
            .toList()
    }

    val filtered = remember(query, apps) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.second.contains(query, ignoreCase = true) || it.first.contains(query, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WaSurface)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.settings_pick_installed_app_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.settings_search_apps_label)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WaNeonGreen,
                    unfocusedBorderColor = WaTextSecondary,
                    focusedLabelColor = WaNeonGreen,
                    unfocusedLabelColor = WaTextSecondary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(filtered, key = { it.first }) { (pkg, label) ->
                    val isAlreadyAdded = pkg in alreadyAdded
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isAlreadyAdded) { onPick(pkg) }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            label,
                            color = if (isAlreadyAdded) WaTextSecondary else Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (isAlreadyAdded) "$pkg · " + stringResource(R.string.settings_already_added) else pkg,
                            color = WaTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.settings_no_apps_found),
                            color = WaTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    }
}

/**
 * Lets the user teach WayArs how a specific custom app's screen is laid
 * out, without needing a code change — reuses the SAME live scan the
 * Diagnostics section already shows (see ScanDiagnostics), so no new
 * capture mechanism is needed: whatever this app's screen most recently
 * produced (while the user had a real order open, per the on-screen
 * instructions) is right here, tap the line that's the price/distance/
 * time, and WayArs derives an extra pattern for JUST that package from the
 * literal unit word/symbol next to the number in that line (see
 * ScreenTextParser.buildHintMoneyPatterns and friends) — not the number
 * itself, since that changes every order.
 *
 * A static screenshot upload was considered and rejected: WayArs reads the
 * live AccessibilityNodeInfo tree, not pixels, so a photo can't be mapped
 * to anything a future live scan of that app will produce. The live scan
 * already sitting in ScanDiagnostics is the actual equivalent of "show it
 * one example" that works with how this app actually reads screens.
 */
@Composable
private fun CalibrationDialog(
    packageName: String,
    existingHint: PackageHint?,
    onDismiss: () -> Unit,
    onSave: (PackageHint) -> Unit,
    onClearHint: () -> Unit
) {
    val entries by com.wayars.app.service.accessibility.ScanDiagnostics.recentPackages.collectAsState()
    // ScanDiagnostics prepends new entries (index 0 = most recent), so the
    // first match for this package is the freshest thing it has seen.
    val latestEntry = remember(entries, packageName) {
        entries.firstOrNull { it.packageName == packageName && it.rawTexts.isNotEmpty() }
    }

    var moneyRowText by remember(packageName) { mutableStateOf<String?>(null) }
    var distanceRowText by remember(packageName) { mutableStateOf<String?>(null) }
    var timeRowText by remember(packageName) { mutableStateOf<String?>(null) }
    var moneyToken by remember(packageName) { mutableStateOf(existingHint?.moneyToken.orEmpty()) }
    var moneyCurrency by remember(packageName) { mutableStateOf(existingHint?.moneyCurrency ?: Currency.PLN) }
    var distanceToken by remember(packageName) { mutableStateOf(existingHint?.distanceUnitToken.orEmpty()) }
    var timeToken by remember(packageName) { mutableStateOf(existingHint?.timeUnitToken.orEmpty()) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }

    // A tapped line like "12.50 CHF" or "CHF 12.50" -> "CHF": strip every
    // digit/decimal separator, keep whatever's left. Deliberately simple —
    // shown in an editable field right after, so the person can trim any
    // stray leftover text (an emoji, a second word) by hand rather than the
    // app guessing wrong silently.
    fun extractToken(text: String): String = text.replace(Regex("""[\d.,]"""), "").trim()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WaSurface)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.settings_calibration_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                packageName,
                color = WaTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (latestEntry == null) {
                Text(
                    stringResource(R.string.settings_calibration_no_data),
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Text(
                    stringResource(R.string.settings_calibration_instructions),
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(latestEntry.rawTexts) { line ->
                        val isMoney = line == moneyRowText
                        val isDistance = line == distanceRowText
                        val isTime = line == timeRowText
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                line,
                                color = if (isMoney || isDistance || isTime) WaNeonGreen else Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CalibrationRoleChip(
                                    label = stringResource(R.string.settings_calibration_role_money),
                                    selected = isMoney,
                                    onClick = { moneyRowText = line; moneyToken = extractToken(line) }
                                )
                                CalibrationRoleChip(
                                    label = stringResource(R.string.settings_calibration_role_distance),
                                    selected = isDistance,
                                    onClick = { distanceRowText = line; distanceToken = extractToken(line) }
                                )
                                CalibrationRoleChip(
                                    label = stringResource(R.string.settings_calibration_role_time),
                                    selected = isTime,
                                    onClick = { timeRowText = line; timeToken = extractToken(line) }
                                )
                            }
                        }
                    }
                }
            }

            if (moneyRowText != null) {
                Text(
                    stringResource(R.string.settings_calibration_money_token_label),
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = moneyToken,
                        onValueChange = { moneyToken = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaNeonGreen,
                            unfocusedBorderColor = WaTextSecondary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        TextButton(onClick = { currencyMenuExpanded = true }) {
                            Text(moneyCurrency.code, color = WaNeonGreen)
                        }
                        DropdownMenu(expanded = currencyMenuExpanded, onDismissRequest = { currencyMenuExpanded = false }) {
                            Currency.entries.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.code} (${c.symbol})") },
                                    onClick = { moneyCurrency = c; currencyMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            if (distanceRowText != null) {
                Text(
                    stringResource(R.string.settings_calibration_distance_token_label),
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = distanceToken,
                    onValueChange = { distanceToken = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaNeonGreen,
                        unfocusedBorderColor = WaTextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (timeRowText != null) {
                Text(
                    stringResource(R.string.settings_calibration_time_token_label),
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = timeToken,
                    onValueChange = { timeToken = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaNeonGreen,
                        unfocusedBorderColor = WaTextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (existingHint?.isEmpty == false) {
                    TextButton(onClick = onClearHint) {
                        Text(stringResource(R.string.settings_calibration_clear), color = WaRed)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                    Button(
                        onClick = {
                            onSave(
                                PackageHint(
                                    packageName = packageName,
                                    moneyToken = moneyToken.trim().ifBlank { null },
                                    moneyCurrency = if (moneyToken.isNotBlank()) moneyCurrency else null,
                                    distanceUnitToken = distanceToken.trim().ifBlank { null },
                                    timeUnitToken = timeToken.trim().ifBlank { null }
                                )
                            )
                        },
                        enabled = moneyToken.isNotBlank() || distanceToken.isNotBlank() || timeToken.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
                    ) {
                        Text(stringResource(R.string.settings_custom_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationRoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) WaNeonGreen else WaSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/**
 * Collapsed by default so it doesn't dominate the Settings screen — expands
 * on tap. The currency symbol now lives on each field's own label (not a
 * hardcoded symbol in the section title, which used to say "€/km" even when
 * PLN or another currency was selected).
 */
@Composable
private fun CustomThresholdsSection(
    existing: CustomThresholds?,
    currency: Currency,
    onSave: (bad: Double, average: Double, good: Double) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var badText by remember(existing) { mutableStateOf(existing?.badRatePerKm?.toString() ?: "") }
    var averageText by remember(existing) { mutableStateOf(existing?.averageRatePerKm?.toString() ?: "") }
    var goodText by remember(existing) { mutableStateOf(existing?.goodRatePerKm?.toString() ?: "") }

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
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_custom_thresholds_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (existing != null) {
                        "${CurrencyFormatter.formatRatePerKm(existing.badRatePerKm, currency)} · " +
                            "${CurrencyFormatter.formatRatePerKm(existing.averageRatePerKm, currency)} · " +
                            CurrencyFormatter.formatRatePerKm(existing.goodRatePerKm, currency)
                    } else {
                        stringResource(R.string.settings_custom_thresholds_hint, currency.symbol)
                    },
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
                ThresholdField(
                    label = "${stringResource(R.string.settings_custom_bad)} (${currency.symbol}/km)",
                    value = badText,
                    accent = WaRed,
                    onValueChange = { badText = it }
                )
                Spacer(Modifier.padding(top = 10.dp))
                ThresholdField(
                    label = "${stringResource(R.string.settings_custom_average)} (${currency.symbol}/km)",
                    value = averageText,
                    accent = WaAmber,
                    onValueChange = { averageText = it }
                )
                Spacer(Modifier.padding(top = 10.dp))
                ThresholdField(
                    label = "${stringResource(R.string.settings_custom_good)} (${currency.symbol}/km)",
                    value = goodText,
                    accent = WaNeonGreen,
                    onValueChange = { goodText = it }
                )

                Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val bad = badText.toDoubleOrNull()
                            val avg = averageText.toDoubleOrNull()
                            val good = goodText.toDoubleOrNull()
                            if (bad != null && avg != null && good != null) {
                                onSave(bad, avg, good)
                                expanded = false
                                Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
                    ) {
                        Text(stringResource(R.string.settings_custom_save))
                    }
                    if (existing != null) {
                        TextButton(onClick = {
                            badText = ""; averageText = ""; goodText = ""
                            onClear()
                        }) {
                            Text(stringResource(R.string.settings_custom_clear), color = WaTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact single row when collapsed ("Car · Petrol · 7.0/100km"); expands
 * into the hierarchical picker: category first, then (only for Car) fuel
 * type, then the two number fields. Two-wheelers never show fuel fields at
 * all — there is nothing to enter, cost is always 0 for them.
 */
@Composable
private fun VehicleSection(
    profile: VehicleProfile,
    currency: Currency,
    onSave: (VehicleProfile) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var category by remember(profile) { mutableStateOf(profile.category) }
    var fuelType by remember(profile) { mutableStateOf(profile.fuelType ?: FuelType.PETROL) }
    var consumptionText by remember(profile) {
        mutableStateOf(if (profile.consumptionPer100Km > 0) profile.consumptionPer100Km.toString() else "")
    }
    var priceText by remember(profile) {
        mutableStateOf(if (profile.fuelPricePerUnit > 0) profile.fuelPricePerUnit.toString() else "")
    }

    val categoryLabel = when (profile.category) {
        VehicleCategory.CAR -> stringResource(R.string.settings_vehicle_car)
        VehicleCategory.SCOOTER -> stringResource(R.string.settings_vehicle_scooter)
        VehicleCategory.BICYCLE -> stringResource(R.string.settings_vehicle_bicycle)
    }
    val fuelLabel = when (profile.fuelType) {
        FuelType.PETROL -> stringResource(R.string.settings_fuel_petrol)
        FuelType.DIESEL -> stringResource(R.string.settings_fuel_diesel)
        FuelType.LPG -> stringResource(R.string.settings_fuel_lpg)
        FuelType.ELECTRIC -> stringResource(R.string.settings_fuel_electric)
        null -> ""
    }
    val summary = if (profile.category == VehicleCategory.CAR) {
        stringResource(R.string.settings_vehicle_summary_car, fuelLabel, profile.consumptionPer100Km.toString())
    } else {
        stringResource(R.string.settings_vehicle_summary_no_fuel, categoryLabel)
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
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_vehicle_title),
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
                // Step 1: category
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ChoiceChip(
                        label = stringResource(R.string.settings_vehicle_car),
                        selected = category == VehicleCategory.CAR,
                        onClick = { category = VehicleCategory.CAR },
                        modifier = Modifier.weight(1f)
                    )
                    ChoiceChip(
                        label = stringResource(R.string.settings_vehicle_scooter),
                        selected = category == VehicleCategory.SCOOTER,
                        onClick = { category = VehicleCategory.SCOOTER },
                        modifier = Modifier.weight(1f)
                    )
                    ChoiceChip(
                        label = stringResource(R.string.settings_vehicle_bicycle),
                        selected = category == VehicleCategory.BICYCLE,
                        onClick = { category = VehicleCategory.BICYCLE },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Step 2: fuel type + consumption/price — only for Car.
                if (category == VehicleCategory.CAR) {
                    Spacer(Modifier.padding(top = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ChoiceChip(
                            label = stringResource(R.string.settings_fuel_petrol),
                            selected = fuelType == FuelType.PETROL,
                            onClick = { fuelType = FuelType.PETROL },
                            modifier = Modifier.weight(1f)
                        )
                        ChoiceChip(
                            label = stringResource(R.string.settings_fuel_diesel),
                            selected = fuelType == FuelType.DIESEL,
                            onClick = { fuelType = FuelType.DIESEL },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ChoiceChip(
                            label = stringResource(R.string.settings_fuel_lpg),
                            selected = fuelType == FuelType.LPG,
                            onClick = { fuelType = FuelType.LPG },
                            modifier = Modifier.weight(1f)
                        )
                        ChoiceChip(
                            label = stringResource(R.string.settings_fuel_electric),
                            selected = fuelType == FuelType.ELECTRIC,
                            onClick = { fuelType = FuelType.ELECTRIC },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.padding(top = 12.dp))
                    ThresholdField(
                        label = stringResource(R.string.settings_vehicle_consumption),
                        value = consumptionText,
                        accent = WaNeonGreen,
                        onValueChange = { consumptionText = it }
                    )
                    Spacer(Modifier.padding(top = 10.dp))
                    ThresholdField(
                        label = if (fuelType == FuelType.ELECTRIC)
                            stringResource(R.string.settings_vehicle_price_electric)
                        else
                            "${stringResource(R.string.settings_vehicle_price_fuel)} (${currency.symbol})",
                        value = priceText,
                        accent = WaNeonGreen,
                        onValueChange = { priceText = it }
                    )
                }

                Spacer(Modifier.padding(top = 14.dp))
                Button(
                    onClick = {
                        val newProfile = if (category == VehicleCategory.CAR) {
                            VehicleProfile(
                                category = VehicleCategory.CAR,
                                fuelType = fuelType,
                                consumptionPer100Km = consumptionText.toDoubleOrNull() ?: 0.0,
                                fuelPricePerUnit = priceText.toDoubleOrNull() ?: 0.0
                            )
                        } else {
                            VehicleProfile(category = category, fuelType = null, consumptionPer100Km = 0.0, fuelPricePerUnit = 0.0)
                        }
                        onSave(newProfile)
                        expanded = false
                        Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
                ) {
                    Text(stringResource(R.string.settings_vehicle_save))
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) WaNeonGreen.copy(alpha = 0.18f) else WaSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (selected) WaNeonGreen else WaTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * Kept only as a fallback in case the file-based log (see ScanLogFile /
 * Dashboard "share log") ever needs to be bypassed — no longer surfaced
 * anywhere in Settings. Currently unused and safe to delete entirely once
 * confident the file log fully replaces it.
 */
@Suppress("unused")
@Composable
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
                // Only the header toggles the whole section now — this used
                // to be on the outer Column, which meant it covered the
                // entries list too. Since no entry row had its own clickable
                // area big enough to consume a tap first, almost any tap
                // inside an entry (anywhere but the tiny "тексты: показать"
                // label) fell through to this handler and collapsed the
                // whole diagnostics block instead of expanding that entry's
                // raw texts.
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Диагностика (временно)", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Открой Bolt/Wolt и посмотри, что появится ниже",
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            // Separate clickable target from the header row's own toggle —
            // stops event propagation here the same way the per-entry rows
            // do below, so tapping Pause doesn't also collapse the section.
            Text(
                if (paused) "▶ продолжить" else "⏸ пауза",
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
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                if (entries.isEmpty()) {
                    Text(
                        "Пока пусто. Включи Active, открой Bolt или Wolt на экране заказа и подожди пару секунд.",
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
                                // not just the small "показать" label — and
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
                                    "${timeFormat.format(java.util.Date(entry.timestampMillis))} · окно: ${if (entry.windowFound) "найдено" else "нет"} · текстов: ${entry.textsCollected}",
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
                                        if (rawExpanded) "тексты: скрыть" else "тексты: показать (${entry.rawTexts.size})",
                                        color = WaTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    AnimatedVisibility(visible = rawExpanded) {
                                        Text(
                                            entry.rawTexts.joinToString("\n") { "• $it" },
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
}

@Composable
private fun ThresholdField(
    label: String,
    value: String,
    accent: Color,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.all { it.isDigit() || it == '.' || it == ',' }) onValueChange(new) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = WaTextSecondary,
            focusedLabelColor = accent,
            unfocusedLabelColor = WaTextSecondary,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionLabel(text: String) {
    if (text.isNotEmpty()) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = WaTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun LanguagePicker(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .clickable { expanded = true }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(LocaleManager.displayName(current), color = MaterialTheme.colorScheme.onSurface)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LocaleManager.supported.forEach { code ->
                DropdownMenuItem(
                    text = { Text(LocaleManager.displayName(code)) },
                    onClick = { onSelect(code); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun CurrencyPicker(current: Currency, onSelect: (Currency) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .clickable { expanded = true }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${current.code} (${current.symbol})", color = MaterialTheme.colorScheme.onSurface)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Currency.entries.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.code} (${c.symbol})") },
                    onClick = { onSelect(c); expanded = false }
                )
            }
        }
    }
}
