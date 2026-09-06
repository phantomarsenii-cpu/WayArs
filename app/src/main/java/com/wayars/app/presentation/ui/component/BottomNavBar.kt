package com.wayars.app.presentation.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaTextSecondary

enum class MainTab { HOME, STATS, PRESETS, SETTINGS }

/**
 * Floating rounded-pill nav bar (Revolut-style) instead of a full-width,
 * screen-edge-to-edge Material3 NavigationBar. Two problems this solves at
 * once: (1) a docked full-width bar was the thing exposing the mismatched
 * system-nav-bar strip underneath it; a floating pill with visible margin on
 * every side just shows the app's own background in that margin instead —
 * no seam to notice. (2) it matches the look the user asked for directly.
 */
@Composable
fun BottomNavBar(current: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(WaSurface)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(
            icon = Icons.Filled.Home,
            label = stringResource(R.string.nav_home),
            selected = current == MainTab.HOME,
            onClick = { onSelect(MainTab.HOME) }
        )
        NavItem(
            icon = Icons.Filled.BarChart,
            label = stringResource(R.string.nav_stats),
            selected = current == MainTab.STATS,
            onClick = { onSelect(MainTab.STATS) }
        )
        NavItem(
            icon = Icons.Filled.Tune,
            label = stringResource(R.string.nav_presets),
            selected = current == MainTab.PRESETS,
            onClick = { onSelect(MainTab.PRESETS) }
        )
        NavItem(
            icon = Icons.Filled.Settings,
            label = stringResource(R.string.nav_settings),
            selected = current == MainTab.SETTINGS,
            onClick = { onSelect(MainTab.SETTINGS) }
        )
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) WaNeonGreen.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) WaNeonGreen else WaTextSecondary
        )
        Text(
            label,
            color = if (selected) WaNeonGreen else WaTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
