package com.wayars.app.presentation.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.domain.model.PresetType

data class PresetUi(val type: PresetType, val title: String, val description: String)

/** Gradient pair (start -> end) sampled from the design template for each preset card. */
private fun presetGradient(type: PresetType): Pair<Color, Color> = when (type) {
    PresetType.ECONOMY -> Color(0xFF14B65C) to Color(0xFF06542E)
    PresetType.BALANCE -> Color(0xFF2679D6) to Color(0xFF0E3E68)
    PresetType.PROFITABLE_ONLY -> Color(0xFF7A46DE) to Color(0xFF3B2170)
}

fun presetAccentColor(type: PresetType): Color = presetGradient(type).first

@Composable
fun PresetCard(
    preset: PresetUi,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (start, end) = presetGradient(preset.type)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(start, end)))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (preset.type) {
                    PresetType.ECONOMY -> Icons.Filled.Eco
                    PresetType.BALANCE -> Icons.Filled.Balance
                    PresetType.PROFITABLE_ONLY -> Icons.Filled.WorkspacePremium
                },
                contentDescription = null,
                tint = Color.White
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(preset.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(preset.description, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White)
            )
        }
    }
}
