package com.wayars.app.presentation.ui.screen.terms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wayars.app.R
import com.wayars.app.presentation.ui.screen.settings.parseSimpleMarkdown
import com.wayars.app.presentation.ui.theme.WaBackground
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextPrimary
import com.wayars.app.presentation.ui.theme.WaTextSecondary

/**
 * Mandatory gate shown once, right after the splash animation and before
 * the paywall/app itself — the user must scroll through the Terms of Use,
 * tick the checkbox, and tap Accept before going any further. There is no
 * skip, back, or "decide later": [onAccept] is only ever invoked from the
 * one Accept button below, and that button is disabled until the checkbox
 * is checked. Once accepted, MainViewModel.acceptTerms() persists a
 * one-way timestamp (see SettingsDataStore.setTermsAccepted) that nothing
 * in this app ever offers a way to undo — this screen simply never shows
 * again for that install (see the SPLASH gating logic in WayArsNavHost).
 */
@Composable
fun TermsGateScreen(onAccept: () -> Unit) {
    var checked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WaBackground)
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.terms_gate_title),
            style = MaterialTheme.typography.headlineMedium,
            color = WaTextPrimary
        )
        Text(
            text = stringResource(R.string.terms_gate_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = WaTextSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(WaSurface)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = parseSimpleMarkdown(stringResource(R.string.settings_terms_content)),
                style = MaterialTheme.typography.bodyMedium,
                color = WaTextSecondary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(WaSurface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = WaNeonGreen,
                    uncheckedColor = WaTextSecondary,
                    checkmarkColor = WaBackground
                )
            )
            Text(
                text = stringResource(R.string.terms_gate_checkbox_label),
                style = MaterialTheme.typography.bodyMedium,
                color = WaTextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
        }

        Button(
            onClick = onAccept,
            enabled = checked,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WaNeonGreen,
                contentColor = WaBackground,
                disabledContainerColor = WaSurfaceVariant,
                disabledContentColor = WaTextSecondary
            )
        ) {
            Text(
                text = stringResource(R.string.terms_gate_accept_button),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
