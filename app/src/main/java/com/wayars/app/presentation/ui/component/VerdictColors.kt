package com.wayars.app.presentation.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.wayars.app.R
import com.wayars.app.domain.model.Verdict
import com.wayars.app.presentation.ui.theme.WaAmber
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaRed

fun verdictColor(verdict: Verdict): Color = when (verdict) {
    Verdict.GOOD -> WaNeonGreen
    Verdict.AVERAGE -> WaAmber
    Verdict.BAD -> WaRed
}

/** Localized label — was hardcoded English before, which never respected the app's language setting. */
@Composable
fun verdictLabel(verdict: Verdict): String = when (verdict) {
    Verdict.GOOD -> stringResource(R.string.verdict_good)
    Verdict.AVERAGE -> stringResource(R.string.verdict_average)
    Verdict.BAD -> stringResource(R.string.verdict_bad)
}
