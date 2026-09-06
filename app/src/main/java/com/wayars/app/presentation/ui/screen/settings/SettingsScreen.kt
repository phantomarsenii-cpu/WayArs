package com.wayars.app.presentation.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wayars.app.R
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
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
    onLanguageSelected: (String) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSaveCustomThresholds: (bad: Double, average: Double, good: Double) -> Unit,
    onClearCustomThresholds: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
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
            PermissionsSection(
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenNotificationSettings = onOpenNotificationSettings
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
                            if (bad != null && avg != null && good != null) onSave(bad, avg, good)
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
