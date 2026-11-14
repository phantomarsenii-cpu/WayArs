package com.wayars.app.presentation.ui.screen.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wayars.app.R
import com.wayars.app.presentation.ui.tour.TourTarget
import com.wayars.app.presentation.ui.tour.tourTarget
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.FuelType
import com.wayars.app.domain.model.PackageHint
import com.wayars.app.domain.model.VehicleCategory
import com.wayars.app.domain.model.VehicleProfile
import com.wayars.app.presentation.ui.theme.WaAmber
import com.wayars.app.presentation.ui.theme.WaBlue
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaRed
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextPrimary
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.AccessibilityUtils
import com.wayars.app.util.CalibrationHelper
import com.wayars.app.util.OrderTextParser
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
    taxPercent: Double = 0.0,
    onSaveTaxPercent: (Double) -> Unit = {},
    customPackages: Set<String>,
    onAddCustomPackage: (String) -> Unit,
    onRemoveCustomPackage: (String) -> Unit,
    packageHints: Map<String, PackageHint>,
    onSavePackageHint: (PackageHint) -> Unit,
    onClearPackageHint: (String) -> Unit,
    termsAcceptedAt: Long?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onReplayTour: () -> Unit = {},
    isSubscribed: Boolean = false,
    onOpenPaywall: () -> Unit = {},
    permissionsPrompt: Boolean = false,
    onPermissionsPromptHandled: () -> Unit = {},
    vehiclePrompt: Boolean = false,
    onVehiclePromptHandled: () -> Unit = {}
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        // See bottomNavBarClearance's doc: device-accurate clearance for
        // the floating nav pill, instead of a fixed guess.
        contentPadding = PaddingValues(bottom = com.wayars.app.presentation.ui.component.bottomNavBarClearance())
    ) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }

        item {
            Box(modifier = Modifier.tourTarget(TourTarget.SETTINGS_SUBSCRIPTION)) {
                SubscriptionSection(isSubscribed = isSubscribed, onOpenPaywall = onOpenPaywall)
            }
        }

        item {
            Box(modifier = Modifier.tourTarget(TourTarget.SETTINGS_CURRENCY)) {
                GeneralSettingsCard(
                    languageCode = languageCode,
                    currency = currency,
                    onLanguageSelected = onLanguageSelected,
                    onCurrencySelected = onCurrencySelected
                )
            }
        }

        item {
            Box(modifier = Modifier.tourTarget(TourTarget.SETTINGS_THRESHOLDS)) {
                CustomThresholdsSection(
                    existing = customThresholds,
                    currency = currency,
                    onSave = onSaveCustomThresholds,
                    onClear = onClearCustomThresholds
                )
            }
        }

        item {
            Box(modifier = Modifier.tourTarget(TourTarget.SETTINGS_VEHICLE)) {
                VehicleSection(
                    profile = vehicleProfile,
                    currency = currency,
                    onSave = onSaveVehicleProfile,
                    openRequest = vehiclePrompt,
                    onRequestHandled = onVehiclePromptHandled
                )
            }
        }

        item {
            Box(modifier = Modifier.tourTarget(TourTarget.SETTINGS_TAX)) {
                TaxSection(taxPercent = taxPercent, onSave = onSaveTaxPercent)
            }
        }

        item {
            Box(modifier = Modifier.tourTarget(TourTarget.SETTINGS_PARTNER)) {
                PartnerSection(currency = currency)
            }
        }

        item {
            Box(modifier = Modifier.tourTarget(TourTarget.SETTINGS_PERMISSIONS)) {
                PermissionsSection(
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    openRequest = permissionsPrompt,
                    onRequestHandled = onPermissionsPromptHandled
                )
            }
        }

        item {
            Box(modifier = Modifier.tourTarget(TourTarget.SETTINGS_APPS)) {
                SupportedAppsSection(
                    currency = currency,
                    customPackages = customPackages,
                    onAddCustomPackage = onAddCustomPackage,
                    onRemoveCustomPackage = onRemoveCustomPackage,
                    packageHints = packageHints,
                    onSavePackageHint = onSavePackageHint,
                    onClearPackageHint = onClearPackageHint
                )
            }
        }

        item {
            Box(modifier = Modifier.tourTarget(TourTarget.SETTINGS_EXTRAS)) {
                ExtrasSection()
            }
        }

        item {
            Box(modifier = Modifier.tourTarget(TourTarget.SETTINGS_INFO)) {
                InfoSection(termsAcceptedAt = termsAcceptedAt, onReplayTour = onReplayTour)
            }
        }
    }
}

/**
 * Reflects the REAL OS-level permission state (not an app-side toggle —
 * these three permissions can only be granted/revoked in system Settings,
 * WayArs can only send the user there). Rechecked on ON_RESUME so coming
 * back from the system Settings screen the row/switch just opened updates
 * without needing to reopen this screen.
 */
private data class PermissionStates(val accessibility: Boolean, val overlay: Boolean, val notifications: Boolean)

@Composable
private fun rememberPermissionStates(): PermissionStates {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun snapshot() = PermissionStates(
        accessibility = AccessibilityUtils.isServiceEnabled(context),
        overlay = Settings.canDrawOverlays(context),
        notifications = AccessibilityUtils.isNotificationListenerEnabled(context)
    )

    var state by remember { mutableStateOf(snapshot()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = snapshot()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

/** Collapsed by default — groups all three permission rows into one compact card. */
@Composable
private fun PermissionsSection(
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    openRequest: Boolean = false,
    onRequestHandled: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var showAccessibilityConsent by remember { mutableStateOf(false) }
    val states = rememberPermissionStates()

    // Opened from the Dashboard ("Active" without permissions): expand the card and, if screen
    // reading is still off, show the prominent-disclosure dialog straight away.
    LaunchedEffect(openRequest) {
        if (openRequest) {
            expanded = true
            if (!states.accessibility) showAccessibilityConsent = true
            onRequestHandled()
        }
    }

    if (showAccessibilityConsent) {
        AccessibilityConsentDialog(
            onAllow = {
                showAccessibilityConsent = false
                onOpenAccessibilitySettings()
            },
            onDecline = { showAccessibilityConsent = false }
        )
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
            Icon(Icons.Filled.VpnKey, contentDescription = null, tint = WaTextSecondary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PermissionRow(
                    title = stringResource(R.string.settings_enable_accessibility),
                    hint = stringResource(R.string.settings_accessibility_hint),
                    granted = states.accessibility,
                    onClick = {
                        if (states.accessibility) onOpenAccessibilitySettings()
                        else showAccessibilityConsent = true
                    }
                )
                PermissionRow(
                    title = stringResource(R.string.settings_enable_overlay),
                    hint = stringResource(R.string.settings_overlay_hint),
                    granted = states.overlay,
                    onClick = onOpenOverlaySettings
                )
                PermissionRow(
                    title = stringResource(R.string.settings_enable_notifications),
                    hint = stringResource(R.string.settings_notifications_hint),
                    granted = states.notifications,
                    onClick = onOpenNotificationSettings
                )
            }
        }
    }
}

/**
 * Prominent-disclosure screen shown before the FIRST request to enable the
 * AccessibilityService, kept as its own affirmative-action dialog separate
 * from the Privacy Policy screen (Google Play requires this — the
 * disclosure cannot only live inside the privacy policy). Only proceeds to
 * system Settings if the user explicitly taps Allow.
 */
@Composable
fun AccessibilityConsentDialog(onAllow: () -> Unit, onDecline: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.accessibility_consent_title)) },
        // Long disclosure: must stay fully readable on small screens, buttons always reachable.
        text = { Text(stringResource(R.string.accessibility_consent_body), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            Button(onClick = onAllow) {
                Text(stringResource(R.string.accessibility_consent_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.accessibility_consent_decline))
            }
        }
    )
}

/**
 * A Switch here is a STATUS indicator, not a direct toggle — Android
 * doesn't let an app grant/revoke these three permissions on its own
 * either way, only send the user to the right system Settings screen. So
 * tapping the row or the switch always opens that screen regardless of
 * current state (to grant, or to double-check/revoke there); what changes
 * with [granted] is purely visual — a granted permission doesn't need a
 * loud call-to-action sitting under it forever, so the hint text only
 * shows while it's still off.
 */
@Composable
private fun PermissionRow(title: String, hint: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            if (!granted) {
                Text(
                    hint,
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = granted,
            onCheckedChange = { onClick() },
            colors = SwitchDefaults.colors(checkedTrackColor = WaNeonGreen, checkedThumbColor = Color.Black)
        )
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
    currency: Currency,
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
            Icon(Icons.Filled.Apps, contentDescription = null, tint = WaTextSecondary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
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
                BUILTIN_SUPPORTED_APPS.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("• ${app.displayName}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            val hasHint = packageHints[app.packageId]?.isEmpty == false
                            Text(
                                if (hasHint) {
                                    stringResource(R.string.settings_calibrated)
                                } else {
                                    stringResource(R.string.settings_not_calibrated)
                                },
                                color = if (hasHint) WaNeonGreen else WaTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { calibratingPackage = app.packageId }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.settings_calibrate_action), tint = WaTextSecondary)
                        }
                    }
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
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { calibratingPackage = pkg }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.settings_calibrate_action), tint = WaTextSecondary)
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
            userCurrency = currency,
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

// Package ids kept alongside the display name (not shown in the UI per
// product decision — a bare "com.bolt.deliverycourier" string meant
// nothing to most users) because calibration below is keyed by package id,
// not display name. Must match OrderAccessibilityService.SUPPORTED_PACKAGES.
// Not read from there directly to avoid pulling an accessibility-service
// class into a Compose screen just for a label list; keep the two in sync
// by hand if that set changes.
private data class BuiltInSupportedApp(val displayName: String, val packageId: String)

private val BUILTIN_SUPPORTED_APPS = listOf(
    BuiltInSupportedApp("Bolt Courier", "com.bolt.deliverycourier"),
    BuiltInSupportedApp("Bolt Driver", "ee.mtakso.driver"),
    BuiltInSupportedApp("Uber Driver", "com.ubercab.driver"),
    BuiltInSupportedApp("Wolt Courier", "com.wolt.courierapp"),
    BuiltInSupportedApp("FreeNow Driver", "taxi.android.driver"),
    BuiltInSupportedApp("Stuart Courier", "com.stuart.courier")
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
 * Step-by-step calibration for one app (Settings -> Supported apps -> ✎).
 *
 * Reads the freshest scan WayArs has for THIS package
 * ([com.wayars.app.service.accessibility.ScanDiagnostics.latestByPackage]).
 *
 *  1. First shows what WayArs ALREADY reads from that screen (price /
 *     distance / time). For the built-in apps this is normally all green, and
 *     the dialog says so plainly — no calibration needed.
 *  2. Otherwise (or on "Calibrate anyway") it walks through three steps:
 *     tap the PRICE number, then the DISTANCE number, then the TIME number.
 *     Only lines that contain numbers are shown, and the user taps the exact
 *     number, not a whole line — the unit word next to that number ("PLN",
 *     "км", "мин") becomes the hint token (see [CalibrationHelper]).
 *  3. Finally it re-parses the same scan WITH the new hint and shows whether
 *     WayArs now reads the numbers the user picked, before saving.
 *
 * The old version showed every scanned line (icon glyphs included) with three
 * buttons each, and copied the whole line minus digits into the token
 * fields ("км мин PLN"), which matched nothing.
 */
private enum class CalStep { MONEY, DISTANCE, TIME, DONE }

@Composable
private fun CalibrationDialog(
    packageName: String,
    userCurrency: Currency,
    existingHint: PackageHint?,
    onDismiss: () -> Unit,
    onSave: (PackageHint) -> Unit,
    onClearHint: () -> Unit
) {
    val latestByPackage by com.wayars.app.service.accessibility.ScanDiagnostics.latestByPackage.collectAsState()
    val rawTexts = latestByPackage[packageName]?.rawTexts.orEmpty()
    val lines = remember(rawTexts) { CalibrationHelper.candidateLines(rawTexts) }
    val displayName = BUILTIN_SUPPORTED_APPS.firstOrNull { it.packageId == packageName }?.displayName ?: packageName

    val autoResult = remember(rawTexts, existingHint) {
        if (rawTexts.isEmpty()) null else OrderTextParser.parse(packageName, rawTexts, existingHint).candidate
    }
    val autoOk = autoResult?.isComplete == true

    var wizardOpen by remember(packageName) { mutableStateOf(false) }
    var step by remember(packageName) { mutableStateOf(CalStep.MONEY) }
    var moneyPick by remember(packageName) { mutableStateOf<CalibrationHelper.NumberPick?>(null) }
    var distancePick by remember(packageName) { mutableStateOf<CalibrationHelper.NumberPick?>(null) }
    var timePick by remember(packageName) { mutableStateOf<CalibrationHelper.NumberPick?>(null) }
    var moneyToken by remember(packageName) { mutableStateOf(existingHint?.moneyToken.orEmpty()) }
    var distanceToken by remember(packageName) { mutableStateOf(existingHint?.distanceUnitToken.orEmpty()) }
    var timeToken by remember(packageName) { mutableStateOf(existingHint?.timeUnitToken.orEmpty()) }
    var advancedOpen by remember { mutableStateOf(false) }

    val showWizard = lines.isNotEmpty() && (wizardOpen || !autoOk)
    val newHint = PackageHint(
        packageName = packageName,
        moneyToken = moneyToken.trim().ifBlank { null },
        // The currency is the one the user picked in Settings (any country), never chosen here.
        moneyCurrency = if (moneyToken.isNotBlank()) userCurrency else null,
        distanceUnitToken = distanceToken.trim().ifBlank { null },
        timeUnitToken = timeToken.trim().ifBlank { null }
    )
    val checkResult = remember(rawTexts, newHint) { OrderTextParser.parse(packageName, rawTexts, newHint).candidate }
    val checkOk = checkResult.isComplete &&
        CalibrationHelper.sameNumber(checkResult.earnings, moneyPick?.value) &&
        CalibrationHelper.sameNumber(checkResult.distanceKm, distancePick?.value) &&
        (timePick == null || CalibrationHelper.sameNumber(checkResult.timeMinutes, timePick?.value))

    val moneyColor = WaNeonGreen
    val distanceColor = WaBlue
    val timeColor = WaAmber

    fun restart() {
        moneyPick = null; distancePick = null; timePick = null
        step = CalStep.MONEY
    }

    fun onNumberPicked(pick: CalibrationHelper.NumberPick) {
        val token = CalibrationHelper.unitTokenFor(lines[pick.lineIndex], pick)
        when (step) {
            CalStep.MONEY -> {
                moneyPick = pick
                moneyToken = token
                step = CalStep.DISTANCE
            }
            CalStep.DISTANCE -> {
                distancePick = pick
                distanceToken = token
                step = CalStep.TIME
            }
            CalStep.TIME -> {
                timePick = pick
                timeToken = token
                step = CalStep.DONE
            }
            CalStep.DONE -> Unit
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WaSurface)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    stringResource(R.string.settings_calibration_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(displayName, color = WaTextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            if (rawTexts.isEmpty()) {
                Text(
                    stringResource(R.string.settings_calibration_no_data),
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // ---- 1. What WayArs reads right now ----
                CalibrationResultCard(
                    title = stringResource(R.string.settings_calibration_status_title),
                    result = autoResult,
                    currency = userCurrency
                )
                if (autoOk) {
                    Text(
                        stringResource(R.string.settings_calibration_auto_ok),
                        color = WaNeonGreen,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!wizardOpen) {
                        TextButton(onClick = { wizardOpen = true; restart() }) {
                            Text(stringResource(R.string.settings_calibration_manual), color = WaTextSecondary)
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.settings_calibration_auto_missing),
                        color = WaAmber,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (lines.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_calibration_no_numbers),
                        color = WaTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // ---- 2. Wizard ----
                if (showWizard && step != CalStep.DONE) {
                    val (roleLabel, roleColor, instruction) = when (step) {
                        CalStep.MONEY -> Triple(stringResource(R.string.settings_calibration_role_money), moneyColor, stringResource(R.string.settings_calibration_pick_money))
                        CalStep.DISTANCE -> Triple(stringResource(R.string.settings_calibration_role_distance), distanceColor, stringResource(R.string.settings_calibration_pick_distance))
                        else -> Triple(stringResource(R.string.settings_calibration_role_time), timeColor, stringResource(R.string.settings_calibration_pick_time))
                    }
                    HorizontalDivider(color = WaSurfaceVariant)
                    Text(
                        stringResource(R.string.settings_calibration_step, step.ordinal + 1, roleLabel),
                        color = roleColor,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(instruction, color = Color.White, style = MaterialTheme.typography.bodyMedium)

                    lines.forEachIndexed { index, line ->
                        val numbers = remember(line, index) { CalibrationHelper.numbersIn(index, line) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WaSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(line, color = WaTextSecondary, style = MaterialTheme.typography.bodySmall)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                numbers.forEach { pick ->
                                    val pickedColor = when (pick) {
                                        moneyPick -> moneyColor
                                        distancePick -> distanceColor
                                        timePick -> timeColor
                                        else -> null
                                    }
                                    Text(
                                        pick.text,
                                        color = if (pickedColor != null) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(pickedColor ?: WaSurface)
                                            .border(1.dp, roleColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                            .clickable { onNumberPicked(pick) }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (step != CalStep.MONEY) {
                            TextButton(onClick = {
                                step = when (step) {
                                    CalStep.TIME -> CalStep.DISTANCE
                                    else -> CalStep.MONEY
                                }
                            }) { Text(stringResource(R.string.settings_calibration_back), color = WaTextSecondary) }
                        }
                        if (step == CalStep.TIME) {
                            TextButton(onClick = {
                                timePick = null
                                timeToken = ""
                                step = CalStep.DONE
                            }) { Text(stringResource(R.string.settings_calibration_skip_time), color = timeColor) }
                        }
                    }
                }

                // ---- 3. Check before saving ----
                if (showWizard && step == CalStep.DONE) {
                    HorizontalDivider(color = WaSurfaceVariant)
                    CalibrationResultCard(
                        title = stringResource(R.string.settings_calibration_check_title),
                        result = checkResult,
                        currency = userCurrency
                    )
                    Text(
                        stringResource(if (checkOk) R.string.settings_calibration_check_ok else R.string.settings_calibration_check_fail),
                        color = if (checkOk) WaNeonGreen else WaAmber,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { restart() }) {
                        Text(stringResource(R.string.settings_calibration_restart), color = WaTextSecondary)
                    }

                    Text(
                        stringResource(R.string.settings_calibration_advanced) + if (advancedOpen) " ▴" else " ▾",
                        color = WaTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { advancedOpen = !advancedOpen }.padding(vertical = 4.dp)
                    )
                    if (advancedOpen) {
                        CalibrationTokenField(stringResource(R.string.settings_calibration_money_token_label), moneyToken) { moneyToken = it }
                        CalibrationTokenField(stringResource(R.string.settings_calibration_distance_token_label), distanceToken) { distanceToken = it }
                        CalibrationTokenField(stringResource(R.string.settings_calibration_time_token_label), timeToken) { timeToken = it }
                    }
                }
            }

            // ---- Buttons ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
                    val canSave = showWizard && step == CalStep.DONE && !newHint.isEmpty
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(if (canSave) R.string.settings_cancel else R.string.settings_calibration_close))
                    }
                    if (canSave) {
                        Button(
                            onClick = { onSave(newHint) },
                            colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
                        ) {
                            Text(stringResource(R.string.settings_custom_save))
                        }
                    }
                }
            }
        }
    }
}

/** Price / distance / time as read by the parser, each green when found, "not found" otherwise. */
@Composable
private fun CalibrationResultCard(
    title: String,
    result: com.wayars.app.domain.model.RawOrderCandidate?,
    currency: Currency
) {
    val notFound = stringResource(R.string.settings_calibration_not_found)
    val rows = listOf(
        stringResource(R.string.settings_calibration_role_money) to result?.earnings?.let { String.format(java.util.Locale.US, "%.2f %s", it, currency.symbol) },
        stringResource(R.string.settings_calibration_role_distance) to result?.distanceKm?.let { String.format(java.util.Locale.US, "%.1f km", it) },
        stringResource(R.string.settings_calibration_role_time) to result?.timeMinutes?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.0f min", it) }
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WaSurfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = WaTextSecondary, style = MaterialTheme.typography.bodySmall)
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (value != null) "$value ✓" else notFound,
                    color = if (value != null) WaNeonGreen else WaRed,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun CalibrationTokenField(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, color = WaTextSecondary, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
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
            Icon(Icons.Filled.Tune, contentDescription = null, tint = WaTextSecondary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
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

                Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val bad = badText.toDoubleOrNull()
                        val avg = averageText.toDoubleOrNull()
                        val good = goodText.toDoubleOrNull()
                        if (bad != null && avg != null && good != null) {
                            onSave(bad, avg, good)
                            expanded = false
                            Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text(stringResource(R.string.settings_custom_save), color = WaNeonGreen)
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
    onSave: (VehicleProfile) -> Unit,
    openRequest: Boolean = false,
    onRequestHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    // Opened from the Dashboard "set up your vehicle" prompt: expand this card.
    LaunchedEffect(openRequest) {
        if (openRequest) {
            expanded = true
            onRequestHandled()
        }
    }
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
    val summary = if (profile.category == VehicleCategory.BICYCLE) {
        stringResource(R.string.settings_vehicle_summary_no_fuel, categoryLabel)
    } else {
        stringResource(R.string.settings_vehicle_summary_fuel, categoryLabel, fuelLabel, profile.consumptionPer100Km.toString())
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
            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = WaTextSecondary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
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
                        onClick = {
                            category = VehicleCategory.SCOOTER
                            // Scooter only offers Petrol/Electric — drop DIESEL/LPG carried over from Car.
                            if (fuelType == FuelType.DIESEL || fuelType == FuelType.LPG) {
                                fuelType = FuelType.PETROL
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ChoiceChip(
                        label = stringResource(R.string.settings_vehicle_bicycle),
                        selected = category == VehicleCategory.BICYCLE,
                        onClick = { category = VehicleCategory.BICYCLE },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Step 2: fuel type + consumption/price — Car (4 options) and
                // Scooter (Petrol/Electric only) both burn fuel/energy.
                // Bicycle never shows these — nothing to enter, cost is 0.
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
                } else if (category == VehicleCategory.SCOOTER) {
                    Spacer(Modifier.padding(top = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ChoiceChip(
                            label = stringResource(R.string.settings_fuel_petrol),
                            selected = fuelType == FuelType.PETROL,
                            onClick = { fuelType = FuelType.PETROL },
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
                TextButton(
                    onClick = {
                        val newProfile = if (category == VehicleCategory.CAR || category == VehicleCategory.SCOOTER) {
                            VehicleProfile(
                                category = category,
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
                    }
                ) {
                    Text(stringResource(R.string.settings_vehicle_save), color = WaNeonGreen)
                }
            }
        }
    }
}

private fun formatTaxPercent(value: Double): String =
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
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) WaNeonGreen.copy(alpha = 0.18f) else WaSurfaceVariant)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Long labels (e.g. "Bicycle / e-scooter") wrap onto a second line at a
        // slightly smaller size instead of being cut off with "…" — the chip
        // keeps its normal width/weight, it just grows a little taller when needed.
        Text(
            label,
            color = if (selected) WaNeonGreen else WaTextSecondary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
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
private fun SectionLabel(text: String, bottomPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    if (text.isNotEmpty()) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = WaTextSecondary, modifier = Modifier.padding(bottom = bottomPadding))
    }
}

/** Groups Language + Currency into one card with row icons, instead of two separate floating cards. */
@Composable
private fun GeneralSettingsCard(
    languageCode: String,
    currency: Currency,
    onLanguageSelected: (String) -> Unit,
    onCurrencySelected: (Currency) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = WaTextSecondary)
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.settings_language), bottomPadding = 6.dp)
                LanguagePicker(current = languageCode, onSelect = onLanguageSelected)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = WaTextSecondary)
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.settings_currency), bottomPadding = 6.dp)
                CurrencyPicker(current = currency, onSelect = onCurrencySelected)
            }
        }
    }
}

/**
 * A tappable "field-look" row that opens a centered [Dialog] with the full
 * option list, instead of a Material [DropdownMenu]. The old
 * ExposedDropdownMenuBox + DropdownMenu combo anchored to the wrong edge and
 * used the menu's default surface color (not [WaSurface]), which is what
 * caused the green-tinted, left-clipped popup that overlapped the card icons.
 * A dialog has no anchor to get wrong and always matches the app's own
 * surface color, so this fixes the bug at the root instead of patching the
 * anchor math.
 */
@Composable
private fun SelectorField(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, WaTextSecondary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = WaTextSecondary)
    }
}

/** Shared centered option-list dialog used by [LanguagePicker] and [CurrencyPicker]. */
@Composable
private fun <T> OptionListDialog(
    options: List<T>,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WaSurface)
                .padding(vertical = 8.dp)
        ) {
            Box {
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 420.dp)) {
                    items(options) { option ->
                        Text(
                            labelFor(option),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option); onDismiss() }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                }
                // Scroll hint: a soft fade at the bottom edge of the list,
                // shown only while there's more content below the visible
                // area (canScrollForward). This is the cue that the list —
                // now much longer for both languages and currencies — can
                // be scrolled, without adding any extra text of its own
                // that would need translating and could get in the way. It
                // disappears once the user scrolls to the very bottom.
                if (listState.canScrollForward) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, WaSurface)))
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePicker(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SelectorField(label = LocaleManager.displayName(current)) { expanded = true }
    if (expanded) {
        OptionListDialog(
            options = LocaleManager.supported,
            labelFor = { LocaleManager.displayName(it) },
            onSelect = onSelect,
            onDismiss = { expanded = false }
        )
    }
}

@Composable
private fun CurrencyPicker(current: Currency, onSelect: (Currency) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SelectorField(label = "${current.code} (${current.symbol})") { expanded = true }
    if (expanded) {
        OptionListDialog(
            options = Currency.entries,
            labelFor = { "${it.code} (${it.symbol})" },
            onSelect = onSelect,
            onDismiss = { expanded = false }
        )
    }
}

/**
 * "About the app / Privacy Policy / Terms of Use" — required before
 * publishing. Content is fully localized (see settings_about_content and
 * friends in strings.xml for every supported language) and rendered from
 * this screen so the wording always follows the app's own selected
 * language, not the device locale.
 */
private enum class InfoDoc { ABOUT, PRIVACY, TERMS }

@Composable
private fun InfoSection(termsAcceptedAt: Long?, onReplayTour: () -> Unit) {
    var activeDoc by remember { mutableStateOf<InfoDoc?>(null) }
    var showContacts by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
    ) {
        InfoRow(Icons.Filled.Forum, stringResource(R.string.settings_contacts_title)) { showContacts = true }
        HorizontalDivider(color = WaSurfaceVariant)
        InfoRow(Icons.Filled.School, stringResource(R.string.settings_tutorial_title)) { onReplayTour() }
        HorizontalDivider(color = WaSurfaceVariant)
        InfoRow(Icons.Filled.Info, stringResource(R.string.settings_about_title)) { activeDoc = InfoDoc.ABOUT }
        HorizontalDivider(color = WaSurfaceVariant)
        InfoRow(Icons.Filled.PrivacyTip, stringResource(R.string.settings_privacy_title)) { activeDoc = InfoDoc.PRIVACY }
        HorizontalDivider(color = WaSurfaceVariant)
        InfoRow(Icons.Filled.Gavel, stringResource(R.string.settings_terms_title)) { activeDoc = InfoDoc.TERMS }
    }

    activeDoc?.let { doc ->
        InfoDialog(doc = doc, termsAcceptedAt = termsAcceptedAt, onDismiss = { activeDoc = null })
    }
    if (showContacts) {
        ContactsDialog(onDismiss = { showContacts = false })
    }
}

/** One way to reach the developer. [uri] is opened with ACTION_VIEW, or ACTION_SENDTO for mailto:. */
private data class ContactLink(
    val name: String,
    val handle: String,
    val uri: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

private val CONTACT_LINKS = listOf(
    ContactLink("Telegram", "@wayars_app", "https://t.me/wayars_app", Icons.Filled.Send, Color(0xFF229ED9)),
    ContactLink("Instagram", "@apstudiomobile", "https://www.instagram.com/apstudiomobile/", Icons.Filled.PhotoCamera, Color(0xFFE1306C)),
    ContactLink("TikTok", "@apstudiomobile.pl", "https://www.tiktok.com/@apstudiomobile.pl", Icons.Filled.MusicNote, Color(0xFF25F4EE)),
    ContactLink("E-mail", "apstudiomobile@gmail.com", "mailto:apstudiomobile@gmail.com", Icons.Filled.Email, WaNeonGreen)
)

@Composable
private fun ContactsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val failedText = stringResource(R.string.settings_contacts_open_failed)

    fun open(link: ContactLink) {
        val uri = Uri.parse(link.uri)
        val intent = if (link.uri.startsWith("mailto:")) {
            Intent(Intent.ACTION_SENDTO, uri).putExtra(Intent.EXTRA_SUBJECT, "WayArs")
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WaSurface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.settings_contacts_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                stringResource(R.string.settings_contacts_subtitle),
                color = WaTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            CONTACT_LINKS.forEach { link ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(WaSurfaceVariant)
                        .clickable { open(link) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(link.color.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(link.icon, contentDescription = null, tint = link.color)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(link.name, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(link.handle, color = WaTextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = WaTextSecondary)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.settings_calibration_close), color = WaNeonGreen)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = WaTextSecondary)
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = WaTextSecondary)
    }
}

@Composable
private fun InfoDialog(doc: InfoDoc, termsAcceptedAt: Long?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val title: String
    val rawContent: String
    when (doc) {
        InfoDoc.ABOUT -> {
            title = stringResource(R.string.settings_about_title)
            rawContent = stringResource(R.string.settings_about_content, appVersionLabel(context))
        }
        InfoDoc.PRIVACY -> {
            title = stringResource(R.string.settings_privacy_title)
            rawContent = stringResource(R.string.settings_privacy_content)
        }
        InfoDoc.TERMS -> {
            title = stringResource(R.string.settings_terms_title)
            rawContent = stringResource(R.string.settings_terms_content)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(WaSurface)
                .padding(20.dp)
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)

            Text(
                text = parseSimpleMarkdown(rawContent),
                color = WaTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp, bottom = 8.dp)
            )

            // Read-only, permanent record: once terms_accepted_at is set
            // (TermsGateScreen -> MainViewModel.acceptTerms(), first write
            // wins per SettingsDataStore.setTermsAccepted) there is no
            // control anywhere in Settings that changes or clears it — this
            // is display-only, matching the "cannot be revoked" spec.
            if (doc == InfoDoc.TERMS && termsAcceptedAt != null) {
                val formatted = remember(termsAcceptedAt) {
                    java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(termsAcceptedAt))
                }
                Text(
                    text = stringResource(R.string.settings_terms_accepted_at, formatted),
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                if (doc == InfoDoc.ABOUT) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            val url = context.getString(R.string.settings_developer_website_url)
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WaNeonGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WaNeonGreen)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_visit_website),
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WaSurfaceVariant, contentColor = WaTextPrimary)
                ) {
                    Text(
                        text = stringResource(R.string.settings_close),
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** e.g. "1.0.0 (3)" — read live from PackageManager so it never drifts from the real build. */
private fun appVersionLabel(context: android.content.Context): String = try {
    val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        pkgInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        pkgInfo.versionCode.toLong()
    }
    "${pkgInfo.versionName} ($versionCode)"
} catch (e: Exception) {
    ""
}

/**
 * Tiny **bold** markdown parser for the legal/about text blocks — just
 * enough to render section headings without needing HTML string resources
 * (which don't survive Android's string-resource escaping rules well across
 * 7 locales). Everything outside `**...**` renders as plain text.
 */
/**
 * Made internal (not private) so [com.wayars.app.presentation.ui.screen.terms.TermsGateScreen]
 * can reuse the exact same renderer for the identical Terms of Use text —
 * one parser, not a second copy that could drift from this one.
 */
@Composable
internal fun parseSimpleMarkdown(raw: String) = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        val start = raw.indexOf("**", i)
        if (start == -1) {
            append(raw.substring(i))
            break
        }
        append(raw.substring(i, start))
        val end = raw.indexOf("**", start + 2)
        if (end == -1) {
            append(raw.substring(start))
            break
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
            append(raw.substring(start + 2, end))
        }
        i = end + 2
    }
}

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

/** Partner (fleet) commission and weekly payment: subtracted to show what really stays "in hand". */
@Composable
private fun PartnerSection(currency: Currency) {
    val context = LocalContext.current
    val commission by com.wayars.app.data.prefs.DriverPrefs.commissionPercent.collectAsState()
    val fee by com.wayars.app.data.prefs.DriverPrefs.weeklyFee.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var commissionText by remember(commission) { mutableStateOf(if (commission > 0.0) formatTaxPercent(commission) else "") }
    var feeText by remember(fee) { mutableStateOf(if (fee > 0.0) formatTaxPercent(fee) else "") }
    val summary = if (commission > 0.0 || fee > 0.0) {
        stringResource(R.string.settings_partner_summary, formatTaxPercent(commission), CurrencyFormatter.format(fee, currency, 0))
    } else {
        stringResource(R.string.settings_partner_summary_none)
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
            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = WaTextSecondary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    stringResource(R.string.settings_partner_title),
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
                    stringResource(R.string.settings_partner_desc),
                    color = WaTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.padding(top = 12.dp))
                ThresholdField(
                    label = stringResource(R.string.settings_partner_commission_label),
                    value = commissionText,
                    accent = WaNeonGreen,
                    onValueChange = { commissionText = it }
                )
                Spacer(Modifier.padding(top = 10.dp))
                ThresholdField(
                    label = stringResource(R.string.settings_partner_fee_label, currency.symbol),
                    value = feeText,
                    accent = WaNeonGreen,
                    onValueChange = { feeText = it }
                )
                Spacer(Modifier.padding(top = 14.dp))
                TextButton(
                    onClick = {
                        val c = (commissionText.replace(',', '.').toDoubleOrNull() ?: 0.0).coerceIn(0.0, 90.0)
                        val f = (feeText.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        com.wayars.app.data.prefs.DriverPrefs.setPartner(context, c, f)
                        com.wayars.app.features.EarningsWidget.requestUpdate(context)
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
private fun ExtrasToggle(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            Text(desc, color = WaTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = WaNeonGreen, checkedThumbColor = Color.Black)
        )
    }
}

/** Day/week summaries, verdict on the watch, home-screen widget and "invite a driver". */
@Composable
private fun ExtrasSection() {
    val context = LocalContext.current
    val summaries by com.wayars.app.data.prefs.DriverPrefs.summariesEnabled.collectAsState()
    val watch by com.wayars.app.data.prefs.DriverPrefs.watchEnabled.collectAsState()
    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    fun askNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { notificationLauncher.launch("android.permission.POST_NOTIFICATIONS") }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.settings_extras_title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.padding(top = 6.dp))
        ExtrasToggle(
            title = stringResource(R.string.settings_summaries_title),
            desc = stringResource(R.string.settings_summaries_desc),
            checked = summaries
        ) { on ->
            com.wayars.app.data.prefs.DriverPrefs.setSummariesEnabled(context, on)
            com.wayars.app.features.Summaries.schedule(context)
            if (on) askNotifications()
        }
        ExtrasToggle(
            title = stringResource(R.string.settings_watch_title),
            desc = stringResource(R.string.settings_watch_desc),
            checked = watch
        ) { on ->
            com.wayars.app.data.prefs.DriverPrefs.setWatchEnabled(context, on)
            if (on) askNotifications()
        }
        Spacer(Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.settings_widget_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.settings_widget_desc),
            color = WaTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )
        TextButton(onClick = {
            if (!com.wayars.app.features.EarningsWidget.requestPin(context)) {
                Toast.makeText(context, context.getString(R.string.settings_widget_desc), Toast.LENGTH_LONG).show()
            }
        }) { Text(stringResource(R.string.settings_widget_add), color = WaNeonGreen) }
        Spacer(Modifier.padding(top = 4.dp))
        Text(stringResource(R.string.settings_invite_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.settings_invite_desc),
            color = WaTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )
        val inviteCode = remember { com.wayars.app.data.prefs.DriverPrefs.inviteCode(context) }
        Text(
            stringResource(R.string.settings_invite_code, inviteCode),
            color = WaNeonGreen,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(WaSurfaceVariant)
                .clickable {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("WayArs", inviteCode))
                    Toast.makeText(context, context.getString(R.string.invite_code_copied), Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        TextButton(onClick = {
            val code = inviteCode
            val link = "https://play.google.com/store/apps/details?id=" + context.packageName +
                "&referrer=utm_source%3Dinvite%26utm_medium%3Ddriver%26utm_campaign%3D" + code
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.invite_share_text, link, code))
            runCatching {
                context.startActivity(
                    Intent.createChooser(send, context.getString(R.string.settings_invite_button))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }) { Text(stringResource(R.string.settings_invite_button), color = WaNeonGreen) }
    }
}
