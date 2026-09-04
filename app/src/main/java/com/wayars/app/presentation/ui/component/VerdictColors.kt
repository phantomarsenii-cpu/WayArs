package com.wayars.app.presentation.ui.component

import androidx.compose.ui.graphics.Color
import com.wayars.app.domain.model.Verdict
import com.wayars.app.presentation.ui.theme.WaAmber
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaRed

fun verdictColor(verdict: Verdict): Color = when (verdict) {
    Verdict.GOOD -> WaNeonGreen
    Verdict.AVERAGE -> WaAmber
    Verdict.BAD -> WaRed
}

fun verdictLabel(verdict: Verdict): String = when (verdict) {
    Verdict.GOOD -> "GOOD"
    Verdict.AVERAGE -> "AVERAGE"
    Verdict.BAD -> "BAD"
}
