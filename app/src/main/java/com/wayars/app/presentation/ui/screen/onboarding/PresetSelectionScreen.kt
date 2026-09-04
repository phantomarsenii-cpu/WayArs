package com.wayars.app.presentation.ui.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.domain.model.PresetType
import com.wayars.app.presentation.ui.component.PresetCard
import com.wayars.app.presentation.ui.component.PresetUi
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaTextSecondary

@Composable
fun PresetSelectionScreen(
    selected: PresetType,
    onSelect: (PresetType) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        PresetUi(PresetType.ECONOMY, stringResource(R.string.preset_economy_title), stringResource(R.string.preset_economy_desc)),
        PresetUi(PresetType.BALANCE, stringResource(R.string.preset_balance_title), stringResource(R.string.preset_balance_desc)),
        PresetUi(PresetType.PROFITABLE_ONLY, stringResource(R.string.preset_profitable_title), stringResource(R.string.preset_profitable_desc))
    )

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.onboarding_title),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.onboarding_subtitle),
            color = WaTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(presets) { preset ->
                PresetCard(
                    preset = preset,
                    selected = preset.type == selected,
                    onClick = {
                        onSelect(preset.type)
                        onContinue()
                    }
                )
            }
        }

        TextButton(onClick = onContinue) {
            Text(stringResource(R.string.onboarding_customize_later), color = WaNeonGreen)
        }
    }
}
