package com.wayars.app.presentation.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wayars.app.domain.model.PresetType
import com.wayars.app.presentation.ui.theme.WaBlue
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaPurple
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaTextSecondary

data class PresetUi(val type: PresetType, val title: String, val description: String)

fun presetAccentColor(type: PresetType): Color = when (type) {
    PresetType.ECONOMY -> WaNeonGreen
    PresetType.BALANCE -> WaBlue
    PresetType.PROFITABLE_ONLY -> WaPurple
}

@Composable
fun PresetCard(
    preset: PresetUi,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = presetAccentColor(preset.type)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else WaSurface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (preset.type) {
                PresetType.ECONOMY -> Icons.Filled.Eco
                PresetType.BALANCE -> Icons.Filled.Balance
                PresetType.PROFITABLE_ONLY -> Icons.Filled.WorkspacePremium
            },
            contentDescription = null,
            tint = accent
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(preset.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(preset.description, style = MaterialTheme.typography.bodyMedium, color = WaTextSecondary)
        }
        if (selected) {
            Box(accent)
        }
    }
}

@Composable
private fun Box(color: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(10.dp).clip(CircleShape).background(color)
    )
}
