package com.wayars.app.presentation.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaTextSecondary

enum class MainTab { HOME, STATS, PRESETS, SETTINGS }

@Composable
fun BottomNavBar(current: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = WaSurface) {
        NavigationBarItem(
            selected = current == MainTab.HOME,
            onClick = { onSelect(MainTab.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { NavLabel(stringResource(R.string.nav_home)) },
            colors = navColors()
        )
        NavigationBarItem(
            selected = current == MainTab.STATS,
            onClick = { onSelect(MainTab.STATS) },
            icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
            label = { NavLabel(stringResource(R.string.nav_stats)) },
            colors = navColors()
        )
        NavigationBarItem(
            selected = current == MainTab.PRESETS,
            onClick = { onSelect(MainTab.PRESETS) },
            icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
            label = { NavLabel(stringResource(R.string.nav_presets)) },
            colors = navColors()
        )
        NavigationBarItem(
            selected = current == MainTab.SETTINGS,
            onClick = { onSelect(MainTab.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { NavLabel(stringResource(R.string.nav_settings)) },
            colors = navColors()
        )
    }
}

/**
 * Some locales (Ukrainian "Налаштування" in particular) are long enough to
 * wrap onto a second line at the default nav-item text size, which pushes
 * the whole bar's height around. Force one line, shrink slightly, and
 * ellipsize as a last resort instead of ever wrapping.
 */
@Composable
private fun NavLabel(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, softWrap = false)
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = WaNeonGreen,
    selectedTextColor = WaNeonGreen,
    unselectedIconColor = WaTextSecondary,
    unselectedTextColor = WaTextSecondary,
    indicatorColor = WaNeonGreen.copy(alpha = 0.15f)
)
