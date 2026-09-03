package com.wayars.app.presentation.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.wayars.app.R
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.PresetType
import com.wayars.app.presentation.ui.component.PresetCard
import com.wayars.app.presentation.ui.component.PresetUi
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.LocaleManager

@Composable
fun SettingsScreen(
    languageCode: String,
    currency: Currency,
    preset: PresetType,
    onLanguageSelected: (String) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onPresetSelected: (PresetType) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
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
            SectionLabel(stringResource(R.string.settings_preset))
        }
        items(
            listOf(
                PresetUi(PresetType.ECONOMY, stringResource(R.string.preset_economy_title), stringResource(R.string.preset_economy_desc)),
                PresetUi(PresetType.BALANCE, stringResource(R.string.preset_balance_title), stringResource(R.string.preset_balance_desc)),
                PresetUi(PresetType.PROFITABLE_ONLY, stringResource(R.string.preset_profitable_title), stringResource(R.string.preset_profitable_desc))
            )
        ) { p ->
            PresetCard(preset = p, selected = p.type == preset, onClick = { onPresetSelected(p.type) })
        }

        item {
            SectionLabel("")
            PermissionRow(
                title = stringResource(R.string.settings_enable_accessibility),
                hint = stringResource(R.string.settings_accessibility_hint),
                onClick = onOpenAccessibilitySettings
            )
        }
        item {
            PermissionRow(
                title = stringResource(R.string.settings_enable_overlay),
                hint = stringResource(R.string.settings_overlay_hint),
                onClick = onOpenOverlaySettings
            )
        }
    }
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

@Composable
private fun PermissionRow(title: String, hint: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(16.dp)
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        Text(hint, color = WaTextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
        ) {
            Text(title)
        }
    }
}
