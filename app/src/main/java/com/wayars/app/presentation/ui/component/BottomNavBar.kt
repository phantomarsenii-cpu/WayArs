package com.wayars.app.presentation.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaTextSecondary

enum class MainTab { HOME, STATS, PRESETS, SETTINGS }

/**
 * The pill's own fixed footprint BELOW the system navigation-bar inset:
 * bottom margin (12dp, see [BottomNavBar]) + its content height (roughly
 * 10dp vertical padding on each side + a ~24dp icon + 2dp gap + an ~14sp
 * label ≈ 62dp). Kept as one named constant so [bottomNavBarClearance] and
 * [BottomNavBar]'s own `.padding(bottom = 12.dp)` can't silently drift
 * apart from each other.
 */
private val PILL_FOOTPRINT_ABOVE_INSET = 74.dp

/**
 * How much bottom space a screen needs to reserve (as a trailing spacer or
 * contentPadding) so its last item doesn't end up hidden behind the
 * floating [BottomNavBar] pill.
 *
 * This used to be a hardcoded `90.dp` duplicated across DashboardScreen,
 * StatsScreen and SettingsScreen — a guess baked in for one particular
 * device's system-navigation-bar height. [BottomNavBar] itself lifts the
 * pill clear of the system nav bar correctly, using the REAL
 * `navigationBarsPadding()` for whichever device it's running on; the
 * screens behind it need that exact same real inset, not a fixed guess,
 * or the reserved gap silently falls short on any device whose nav bar is
 * taller than what 90dp happened to assume (typically 3-button navigation,
 * which reserves more space than a gesture pill) — leaving the last bit of
 * content or the last item actually obscured behind the pill.
 */
@Composable
fun bottomNavBarClearance(): Dp {
    val systemInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return systemInset + PILL_FOOTPRINT_ABOVE_INSET
}

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
