#!/usr/bin/env bash
# WayArs — Update 07: обучающий тур при первом запуске + плашка "Доступно обновление".
# ТОЛЬКО для приложения WayArs (com.wayars.app). Запускать из КОРНЯ репозитория WayArs.
#
#  1) Обучающий тур (Jetpack Compose): затемнение, подсветка нужного элемента, карточка со счётчиком шагов
#     и кнопками "Пропустить / Назад / Далее", анимации переходов. 11 шагов: приветствие, переключатель Active,
#     итоги дня, оценка заказа, Статистика, Пресеты, свои пороги, транспорт и топливо, разрешения,
#     поддерживаемые приложения, финал. Шаги сами переключают вкладки и прокручивают Настройки.
#     Запускается ОДИН раз на главном экране — только после экрана подписки (подписка активна),
#     после выбора пресета. Повтор: Настройки -> "Обучение".
#  2) Плашка "Доступно обновление" в стиле приложения (тёмная карточка, неоново-зелёная кнопка) над нижней
#     навигацией: только для подписанного пользователя, только после прохождения обучения, только если
#     приложение установлено из Google Play и в Play есть версия новее. "Позже" скрывает на 3 дня.
#  3) Тексты тура и плашки — на всех 17 языках приложения (en + az bg cs da de fr hu hy ka nb pl ro ru sv uk uz).
#  4) Зависимость: com.google.android.play:app-update:2.1.0.
set -euo pipefail

P=app/src/main/java/com/wayars/app
for f in "$P/WayArsApplication.kt" "$P/presentation/ui/navigation/MainScreen.kt" "$P/presentation/ui/navigation/WayArsNavHost.kt" "app/build.gradle.kts"; do
  [ -f "$f" ] || { echo "СТОП: это не репозиторий WayArs (нет $f). Запусти из корня WayArs."; exit 1; }
done
grep -q "com.wayars.app" "$P/MainActivity.kt" || { echo "СТОП: не WayArs"; exit 1; }

BK=".update07_backup_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BK"
for f in "$P/presentation/ui/navigation/MainScreen.kt" "$P/presentation/ui/navigation/WayArsNavHost.kt" \
         "$P/presentation/ui/screen/dashboard/DashboardScreen.kt" "$P/presentation/ui/screen/settings/SettingsScreen.kt" \
         "$P/presentation/ui/component/BottomNavBar.kt" "$P/presentation/MainViewModel.kt" \
         "$P/data/prefs/SettingsDataStore.kt" "$P/domain/repository/SettingsRepository.kt" \
         "$P/data/repository/SettingsRepositoryImpl.kt" app/build.gradle.kts; do
  cp "$f" "$BK/$(echo "$f" | tr '/' '_')"
done
grep -qxF '.update*_backup_*/' .gitignore 2>/dev/null || echo '.update*_backup_*/' >> .gitignore

mkdir -p "$P/presentation/ui/tour"

cat > "$P/presentation/ui/tour/TourController.kt" <<'EOF_TOURCTRL'
package com.wayars.app.presentation.ui.tour

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.wayars.app.R
import com.wayars.app.presentation.ui.component.MainTab

/** Elements of the UI that the tour can spotlight. */
enum class TourTarget {
    ACTIVE_SWITCH,
    SUMMARY_CARD,
    VERDICT_CARD,
    NAV_STATS,
    NAV_PRESETS,
    SETTINGS_THRESHOLDS,
    SETTINGS_VEHICLE,
    SETTINGS_PERMISSIONS,
    SETTINGS_APPS
}

/**
 * One step of the tour.
 *  - [target] null = just a centered card over the dimmed screen.
 *  - [tab] the main tab that must be showing for this step.
 *  - [settingsItem] index of the LazyColumn item in SettingsScreen to scroll to first
 *    (0 title, 1 general, 2 thresholds, 3 vehicle, 4 permissions, 5 supported apps, 6 info).
 */
class TourStep(
    val target: TourTarget?,
    val tab: MainTab,
    val settingsItem: Int?,
    @StringRes val title: Int,
    @StringRes val text: Int
)

object TourSteps {
    val all: List<TourStep> = listOf(
        TourStep(null, MainTab.HOME, null, R.string.tour_welcome_title, R.string.tour_welcome_text),
        TourStep(TourTarget.ACTIVE_SWITCH, MainTab.HOME, null, R.string.tour_active_title, R.string.tour_active_text),
        TourStep(TourTarget.SUMMARY_CARD, MainTab.HOME, null, R.string.tour_summary_title, R.string.tour_summary_text),
        TourStep(TourTarget.VERDICT_CARD, MainTab.HOME, null, R.string.tour_verdict_title, R.string.tour_verdict_text),
        TourStep(TourTarget.NAV_STATS, MainTab.STATS, null, R.string.tour_stats_title, R.string.tour_stats_text),
        TourStep(TourTarget.NAV_PRESETS, MainTab.PRESETS, null, R.string.tour_presets_title, R.string.tour_presets_text),
        TourStep(TourTarget.SETTINGS_THRESHOLDS, MainTab.SETTINGS, 2, R.string.tour_thresholds_title, R.string.tour_thresholds_text),
        TourStep(TourTarget.SETTINGS_VEHICLE, MainTab.SETTINGS, 3, R.string.tour_vehicle_title, R.string.tour_vehicle_text),
        TourStep(TourTarget.SETTINGS_PERMISSIONS, MainTab.SETTINGS, 4, R.string.tour_permissions_title, R.string.tour_permissions_text),
        TourStep(TourTarget.SETTINGS_APPS, MainTab.SETTINGS, 5, R.string.tour_apps_title, R.string.tour_apps_text),
        TourStep(null, MainTab.HOME, null, R.string.tour_final_title, R.string.tour_final_text)
    )
}

/**
 * State of the first-run tour. Created once in the nav host and handed to MainScreen (which
 * switches tabs/scrolls per step) and to [TourHost] (which draws the overlay).
 *
 * Target positions are NOT kept in observable state (that would recompose on every layout
 * pass); they are read once from the stored LayoutCoordinates when a step has settled.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class TourController {
    val steps: List<TourStep> = TourSteps.all

    var running by mutableStateOf(false)
        private set
    var index by mutableIntStateOf(0)
        private set
    /** true once the tab switch / scroll of the current step is done and [targetRect] is final. */
    var settled by mutableStateOf(false)
        private set
    var targetRect by mutableStateOf<Rect?>(null)
        private set

    /** Called when the user finishes or skips the tour (not when it is stopped by leaving the screen). */
    var onFinished: (() -> Unit)? = null

    private val coords = HashMap<TourTarget, LayoutCoordinates>()
    private val requesters = HashMap<TourTarget, BringIntoViewRequester>()

    val current: TourStep get() = steps[index]

    fun start() {
        index = 0
        settled = false
        targetRect = null
        running = true
    }

    fun next() { if (index >= steps.lastIndex) finish() else index++ }

    fun back() { if (index <= 0) finish() else index-- }

    fun finish() {
        if (!running) return
        stop()
        onFinished?.invoke()
    }

    /** Hides the tour without marking it as completed (e.g. the screen was left). */
    fun stop() {
        running = false
        settled = false
        targetRect = null
    }

    fun beginStep() {
        settled = false
        targetRect = null
    }

    fun settle() {
        val t = current.target
        targetRect = if (t == null) null
        else coords[t]?.takeIf { it.isAttached }?.boundsInWindow()
        settled = true
    }

    fun isTargetReady(target: TourTarget): Boolean = coords[target]?.isAttached == true

    suspend fun bringIntoView(target: TourTarget) {
        requesters[target]?.bringIntoView()
    }

    internal fun register(target: TourTarget, c: LayoutCoordinates) { coords[target] = c }
    internal fun register(target: TourTarget, r: BringIntoViewRequester) { requesters[target] = r }
    internal fun unregister(target: TourTarget) { coords.remove(target); requesters.remove(target) }
}

val LocalTourController = staticCompositionLocalOf<TourController?> { null }

/**
 * Marks a composable as a tour target. Costs nothing when no tour controller is provided and
 * only stores a reference (no state writes) otherwise.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tourTarget(target: TourTarget?): Modifier {
    if (target == null) return this
    val controller = LocalTourController.current ?: return this
    val requester = remember(target) { BringIntoViewRequester() }
    DisposableEffect(controller, target) {
        controller.register(target, requester)
        onDispose { controller.unregister(target) }
    }
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { controller.register(target, it) }
}
EOF_TOURCTRL
echo "создан: TourController.kt"

cat > "$P/presentation/ui/tour/TourOverlay.kt" <<'EOF_TOUROVL'
package com.wayars.app.presentation.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.rememberReducedMotionPreferred
import kotlin.math.max
import kotlin.math.roundToInt

private val DimColor = Color(0xD2040810)

/** Draws the tour on top of everything, but only while the tour is running. */
@Composable
fun TourHost(controller: TourController) {
    if (controller.running) TourOverlay(controller)
}

@Composable
private fun TourOverlay(c: TourController) {
    val density = LocalDensity.current
    val reduced = rememberReducedMotionPreferred()

    var origin by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var cardHeight by remember { mutableIntStateOf(0) }

    val index = c.index
    val total = c.steps.size
    val isLast = index >= total - 1

    // System Back = previous step (first step: leave the tour).
    BackHandler { c.back() }

    // ---- spotlight hole (animates from step to step) ----
    val padPx = with(density) { 6.dp.toPx() }
    val rect: Rect? = c.targetRect?.translate(-origin)?.inflate(padPx)
    val center = Offset(area.width / 2f, area.height / 2f)
    val holeTarget = rect ?: Rect(center, 0f)
    val holeState = animateRectAsState(
        targetValue = holeTarget,
        animationSpec = if (reduced) snap<Rect>() else tween<Rect>(340, easing = FastOutSlowInEasing),
        label = "tourHole"
    )
    val pulseState: State<Float> = if (reduced) {
        remember { mutableFloatStateOf(0.9f) }
    } else {
        rememberInfiniteTransition(label = "tourPulse").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "tourPulseValue"
        )
    }

    // ---- card position ----
    val statusTop = WindowInsets.statusBars.getTop(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val gap = with(density) { 16.dp.toPx() }
    val edge = with(density) { 12.dp.toPx() }
    val minY = statusTop + edge
    val maxY = max(minY, area.height - navBottom - edge - cardHeight)
    val targetY = (
        if (rect == null) {
            (area.height - cardHeight) / 2f
        } else {
            val below = area.height - navBottom - rect.bottom
            val above = rect.top - statusTop
            if (below >= cardHeight + gap || below >= above) rect.bottom + gap
            else rect.top - gap - cardHeight
        }
        ).coerceIn(minY, maxY)
    val cardY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = if (reduced) snap<Float>() else tween<Float>(320, easing = FastOutSlowInEasing),
        label = "tourCardY"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (c.settled) 1f else 0f,
        animationSpec = if (reduced) snap<Float>() else tween<Float>(220),
        label = "tourCardAlpha"
    )
    val progress by animateFloatAsState(
        targetValue = (index + 1).toFloat() / total,
        animationSpec = if (reduced) snap<Float>() else tween<Float>(300),
        label = "tourProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                area = it.size
            }
            // Swallow every touch so the app underneath can't be used during the tour.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hole = holeState.value
            val hasHole = hole.width > 2f && hole.height > 2f
            val radius = if (hasHole) minOf(with(density) { 16.dp.toPx() }, minOf(hole.width, hole.height) / 2f) else 0f
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                if (hasHole) addRoundRect(RoundRect(hole, CornerRadius(radius, radius)))
            }
            drawPath(path, DimColor)
            if (hasHole) {
                drawRoundRect(
                    color = WaNeonGreen.copy(alpha = pulseState.value),
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset { IntOffset(0, cardY.roundToInt()) }
                .graphicsLayer { alpha = cardAlpha }
                .onSizeChanged { cardHeight = it.height }
                .clip(shape)
                .background(WaSurface)
                .border(1.dp, WaSurfaceVariant, shape)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.tour_counter, index + 1, total),
                    color = WaTextSecondary,
                    fontSize = 13.sp
                )
            }
            // thin progress line
            Box(
                modifier = Modifier
                    .padding(top = 6.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(WaSurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(WaNeonGreen)
                )
            }

            AnimatedContent(
                targetState = index,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "tourText"
            ) { i ->
                val step = c.steps[i]
                Column {
                    Text(
                        stringResource(step.title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    Text(
                        stringResource(step.text),
                        color = WaTextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLast) {
                    TextButton(onClick = { c.finish() }) {
                        Text(stringResource(R.string.tour_skip), color = WaTextSecondary, fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (index > 0) {
                    TextButton(onClick = { c.back() }) {
                        Text(stringResource(R.string.tour_back), color = Color.White, fontSize = 15.sp)
                    }
                }
                Button(
                    onClick = { c.next() },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
                ) {
                    Text(
                        stringResource(if (isLast) R.string.tour_done else R.string.tour_next),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
EOF_TOUROVL
echo "создан: TourOverlay.kt"

cat > "$P/util/PlayUpdateChecker.kt" <<'EOF_CHECKER'
package com.wayars.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Asks Google Play whether a newer version of this app is published.
 * Only meaningful for installs that came from Google Play (a sideloaded APK has no store
 * listing to compare with), so every other install simply reports "no update".
 */
object PlayUpdateChecker {

    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun isInstalledFromPlay(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        installer == PLAY_STORE_PACKAGE
    }.getOrDefault(false)

    suspend fun isUpdateAvailable(context: Context): Boolean {
        if (!isInstalledFromPlay(context)) return false
        return suspendCancellableCoroutine { cont ->
            try {
                AppUpdateManagerFactory.create(context.applicationContext)
                    .appUpdateInfo
                    .addOnSuccessListener { info ->
                        if (cont.isActive) {
                            cont.resume(info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                        }
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(false)
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    /** Opens this app's page in Google Play (falls back to the web page if Play is missing). */
    fun openStorePage(context: Context) {
        val pkg = context.packageName
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .setPackage(PLAY_STORE_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
EOF_CHECKER
echo "создан: PlayUpdateChecker.kt"

cat > "$P/presentation/ui/component/UpdatePromptCard.kt" <<'EOF_CARD'
package com.wayars.app.presentation.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaTextSecondary

/**
 * "Update available" card that slides up above the floating nav pill.
 * Same look as the app's other cards: dark surface, rounded corners, neon-green action.
 */
@Composable
fun UpdatePromptCard(
    visible: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(320)),
        exit = slideOutVertically(tween(220)) { it / 2 } + fadeOut(tween(220))
    ) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(WaSurface)
                .border(1.dp, WaNeonGreen.copy(alpha = 0.35f), shape)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(WaNeonGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = WaNeonGreen)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        stringResource(R.string.update_available_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        stringResource(R.string.update_available_text),
                        color = WaTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onLater) {
                    Text(stringResource(R.string.update_later), color = WaTextSecondary, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.size(6.dp))
                Button(
                    onClick = onUpdate,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
                ) {
                    Text(stringResource(R.string.update_now), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
EOF_CARD
echo "создан: UpdatePromptCard.kt"

cat > "$P/presentation/ui/navigation/MainScreen.kt" <<'EOF_MAIN'
package com.wayars.app.presentation.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.PresetType
import com.wayars.app.domain.model.VehicleProfile
import com.wayars.app.domain.repository.OrderRecord
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.presentation.TodaySummary
import com.wayars.app.presentation.ui.component.BottomNavBar
import com.wayars.app.presentation.ui.component.MainTab
import com.wayars.app.presentation.ui.component.UpdatePromptCard
import com.wayars.app.presentation.ui.component.bottomNavBarClearance
import com.wayars.app.presentation.ui.screen.dashboard.DashboardScreen
import com.wayars.app.presentation.ui.screen.dashboard.StatsScreen
import com.wayars.app.presentation.ui.screen.onboarding.PresetSelectionScreen
import com.wayars.app.presentation.ui.screen.settings.SettingsScreen
import com.wayars.app.presentation.ui.tour.LocalTourController
import com.wayars.app.presentation.ui.tour.TourController
import com.wayars.app.util.PlayUpdateChecker
import kotlinx.coroutines.delay

/**
 * Post-onboarding home: bottom-nav host for Home / Stats / Presets / Settings.
 *
 * Uses a plain Box instead of Scaffold(bottomBar = ...) on purpose: Scaffold
 * docks the bar full-width flush against the screen edge and applies its own
 * automatic inset handling, which is exactly what was producing the
 * mismatched dark strip near the system navigation bar. Layering the
 * floating pill nav bar over the content directly gives full control over
 * its own margin/inset handling instead (see BottomNavBar).
 *
 * This screen is only ever reached for a subscribed user (see WayArsNavHost), so it is also
 * where the first-run tour and the "update available" card are driven from.
 */
@Composable
fun MainScreen(
    summary: TodaySummary,
    latestEvaluation: OrderEvaluation?,
    todayOrders: List<OrderRecord>,
    languageCode: String,
    currency: Currency,
    preset: PresetType,
    termsAcceptedAt: Long?,
    customThresholds: CustomThresholds?,
    vehicleProfile: VehicleProfile,
    onLanguageSelected: (String) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onPresetSelected: (PresetType) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSaveCustomThresholds: (bad: Double, average: Double, good: Double) -> Unit,
    onClearCustomThresholds: () -> Unit,
    onSaveVehicleProfile: (VehicleProfile) -> Unit,
    customPackages: Set<String>,
    onAddCustomPackage: (String) -> Unit,
    onRemoveCustomPackage: (String) -> Unit,
    packageHints: Map<String, com.wayars.app.domain.model.PackageHint>,
    onSavePackageHint: (com.wayars.app.domain.model.PackageHint) -> Unit,
    onClearPackageHint: (String) -> Unit,
    isSubscribed: Boolean,
    onSubscriptionRequired: () -> Unit,
    tour: TourController,
    tourDone: Boolean?,
    onTourFinished: () -> Unit,
    updateSnoozeUntil: Long?,
    onUpdateLater: () -> Unit
) {
    var tab by remember { mutableStateOf(MainTab.HOME) }
    val settingsListState = rememberLazyListState()
    val context = LocalContext.current

    // ---------------------------------------------------------------- first-run tour
    SideEffect { tour.onFinished = onTourFinished }

    // Leaving this screen (e.g. the subscription lapsed -> paywall) must never leave the
    // overlay hanging over another screen. Not marked as done -- it was not completed.
    DisposableEffect(tour) { onDispose { tour.stop() } }

    // Starts once, on the Home screen, for a subscribed user who has not seen it yet.
    // tourDone is null until DataStore has loaded, so a returning user never sees a flash.
    LaunchedEffect(tourDone, isSubscribed) {
        if (tourDone == false && isSubscribed && !tour.running) {
            delay(900)
            tour.start()
        }
    }

    // Per step: show the right tab, scroll to the target, wait for it to be laid out, then
    // "settle" (the overlay reads the final position of the target from here).
    LaunchedEffect(tour.running, tour.index) {
        if (!tour.running) return@LaunchedEffect
        val step = tour.current
        tour.beginStep()
        val switching = tab != step.tab
        tab = step.tab
        delay(if (switching) 180L else 40L)

        val target = step.target
        if (step.settingsItem != null) {
            runCatching { settingsListState.animateScrollToItem(step.settingsItem) }
        } else if (target != null) {
            runCatching { tour.bringIntoView(target) }
        }
        var tries = 0
        while (target != null && !tour.isTargetReady(target) && tries < 10) {
            delay(100L)
            tries++
        }
        delay(120L)
        tour.settle()
    }

    // When the tour ends, land on the Home tab again.
    var tourWasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(tour.running) {
        if (tourWasRunning && !tour.running) tab = MainTab.HOME
        tourWasRunning = tour.running
    }

    // ------------------------------------------------------- "update available" card
    // Checked once per visit to this screen: only for a subscribed user, only after the tour
    // is done, and not while the user has pressed "Later" (3-day snooze, see MainViewModel).
    var updateAvailable by remember { mutableStateOf(false) }
    var updateChecked by remember { mutableStateOf(false) }
    LaunchedEffect(isSubscribed, tourDone, updateSnoozeUntil) {
        if (updateChecked || !isSubscribed || tourDone != true || updateSnoozeUntil == null) {
            return@LaunchedEffect
        }
        if (System.currentTimeMillis() < updateSnoozeUntil) return@LaunchedEffect
        delay(2500)
        updateChecked = true
        updateAvailable = PlayUpdateChecker.isUpdateAvailable(context)
    }

    // No outer padding reserved for the pill anymore -- that reserved gap was
    // exactly what read as a separate-colored strip above the nav bar
    // (WaBackground behind it vs WaSurface cards above it). Content now
    // fills the full screen and scrolls BEHIND the floating pill instead;
    // each screen adds its own bottom inset as part of its scrollable
    // content (see their contentPadding/trailing spacer) so the last item
    // isn't permanently stuck under the opaque pill, without introducing a
    // separately-colored dead zone.
    CompositionLocalProvider(LocalTourController provides tour) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                MainTab.HOME -> DashboardScreen(
                    summary = summary,
                    latestEvaluation = latestEvaluation,
                    isSubscribed = isSubscribed,
                    onSubscriptionRequired = onSubscriptionRequired
                )
                MainTab.STATS -> StatsScreen(orders = todayOrders)
                MainTab.PRESETS -> PresetSelectionScreen(
                    selected = preset,
                    onSelect = onPresetSelected,
                    onContinue = { tab = MainTab.HOME }
                )
                MainTab.SETTINGS -> SettingsScreen(
                    languageCode = languageCode,
                    currency = currency,
                    customThresholds = customThresholds,
                    vehicleProfile = vehicleProfile,
                    onLanguageSelected = onLanguageSelected,
                    onCurrencySelected = onCurrencySelected,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onSaveCustomThresholds = onSaveCustomThresholds,
                    onClearCustomThresholds = onClearCustomThresholds,
                    onSaveVehicleProfile = onSaveVehicleProfile,
                    customPackages = customPackages,
                    onAddCustomPackage = onAddCustomPackage,
                    onRemoveCustomPackage = onRemoveCustomPackage,
                    packageHints = packageHints,
                    onSavePackageHint = onSavePackageHint,
                    onClearPackageHint = onClearPackageHint,
                    termsAcceptedAt = termsAcceptedAt,
                    listState = settingsListState,
                    onReplayTour = { tour.start() }
                )
            }

            BottomNavBar(
                current = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            UpdatePromptCard(
                visible = updateAvailable && !tour.running,
                onUpdate = {
                    PlayUpdateChecker.openStorePage(context)
                    updateAvailable = false
                    onUpdateLater()
                },
                onLater = {
                    updateAvailable = false
                    onUpdateLater()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = bottomNavBarClearance() + 8.dp)
            )
        }
    }
}
EOF_MAIN
echo "обновлён: MainScreen.kt"

python3 - <<'PY'
import re, os
M = "app/src/main"
P = M + "/java/com/wayars/app"
def read(p): return open(p, encoding="utf-8").read()
def write(p, s): open(p, "w", encoding="utf-8").write(s); print("изменён:", p)
def rep(s, old, new, what):
    assert s.count(old) == 1, "не найден (или не уникален) фрагмент: " + what
    return s.replace(old, new)
def add_import(s, line, after):
    if line in s: return s
    assert s.count(after) >= 1, "нет якоря импорта: " + after
    return s.replace(after, after + "\n" + line, 1)

# ============ build.gradle.kts: Play Core (in-app updates) ============
p = "app/build.gradle.kts"
s = read(p)
if "play:app-update" not in s:
    old = '    implementation("com.revenuecat.purchases:purchases:10.21.1")\n'
    s = rep(s, old, old + '    // Update 07: "update available" prompt (Google Play in-app updates API, availability check only)\n'
                           '    implementation("com.google.android.play:app-update:2.1.0")\n', "build.gradle.kts revenuecat")
    write(p, s)
else:
    print("build.gradle.kts: уже применено")

# ============ SettingsDataStore ============
p = P + "/data/prefs/SettingsDataStore.kt"
s = read(p)
if "TOUR_DONE" not in s:
    s = rep(s, '        val PACKAGE_HINTS = stringSetPreferencesKey("package_parsing_hints")\n',
        '        val PACKAGE_HINTS = stringSetPreferencesKey("package_parsing_hints")\n'
        '        // First-run tutorial finished/skipped, and the "update available" card snooze.\n'
        '        val TOUR_DONE = booleanPreferencesKey("tour_done")\n'
        '        val UPDATE_SNOOZE_UNTIL = longPreferencesKey("update_snooze_until")\n', "keys")
    s = rep(s, '    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }\n',
        '    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }\n\n'
        '    val tourDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.TOUR_DONE] ?: false }\n\n'
        '    /** Epoch millis until which the "update available" card stays hidden (0 = not snoozed). */\n'
        '    val updateSnoozeUntil: Flow<Long> = context.dataStore.data.map { it[Keys.UPDATE_SNOOZE_UNTIL] ?: 0L }\n', "flows")
    s = rep(s, '    suspend fun setOnboardingDone(done: Boolean) {\n        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }\n    }\n',
        '    suspend fun setOnboardingDone(done: Boolean) {\n        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }\n    }\n\n'
        '    suspend fun setTourDone(done: Boolean) {\n        context.dataStore.edit { it[Keys.TOUR_DONE] = done }\n    }\n\n'
        '    suspend fun setUpdateSnoozeUntil(epochMillis: Long) {\n        context.dataStore.edit { it[Keys.UPDATE_SNOOZE_UNTIL] = epochMillis }\n    }\n', "setters")
    write(p, s)
else:
    print("SettingsDataStore: уже применено")

# ============ SettingsRepository + Impl ============
p = P + "/domain/repository/SettingsRepository.kt"
s = read(p)
if "tourDone" not in s:
    s = rep(s, "    val onboardingDone: Flow<Boolean>\n", "    val onboardingDone: Flow<Boolean>\n    val tourDone: Flow<Boolean>\n    val updateSnoozeUntil: Flow<Long>\n", "repo flows")
    s = rep(s, "    suspend fun setOnboardingDone(done: Boolean)\n",
            "    suspend fun setOnboardingDone(done: Boolean)\n    suspend fun setTourDone(done: Boolean)\n    suspend fun setUpdateSnoozeUntil(epochMillis: Long)\n", "repo setters")
    write(p, s)
else:
    print("SettingsRepository: уже применено")

p = P + "/data/repository/SettingsRepositoryImpl.kt"
s = read(p)
if "tourDone" not in s:
    s = rep(s, "    override val onboardingDone: Flow<Boolean> = store.onboardingDone\n",
            "    override val onboardingDone: Flow<Boolean> = store.onboardingDone\n"
            "    override val tourDone: Flow<Boolean> = store.tourDone\n"
            "    override val updateSnoozeUntil: Flow<Long> = store.updateSnoozeUntil\n", "impl flows")
    s = rep(s, "    override suspend fun setOnboardingDone(done: Boolean) = store.setOnboardingDone(done)\n",
            "    override suspend fun setOnboardingDone(done: Boolean) = store.setOnboardingDone(done)\n"
            "    override suspend fun setTourDone(done: Boolean) = store.setTourDone(done)\n"
            "    override suspend fun setUpdateSnoozeUntil(epochMillis: Long) = store.setUpdateSnoozeUntil(epochMillis)\n", "impl setters")
    write(p, s)
else:
    print("SettingsRepositoryImpl: уже применено")

# ============ MainViewModel ============
p = P + "/presentation/MainViewModel.kt"
s = read(p)
if "tourDone" not in s:
    s = rep(s, "    val onboardingDone: StateFlow<Boolean> =\n        settings.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, false)\n",
        "    val onboardingDone: StateFlow<Boolean> =\n        settings.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, false)\n\n"
        "    /** Null until DataStore has loaded, so a returning user never gets a flash of the tutorial. */\n"
        "    val tourDone: StateFlow<Boolean?> =\n        settings.tourDone.stateIn(viewModelScope, SharingStarted.Eagerly, null)\n\n"
        "    /** Null until loaded; epoch millis until which the \"update available\" card is snoozed. */\n"
        "    val updateSnoozeUntil: StateFlow<Long?> =\n        settings.updateSnoozeUntil.stateIn(viewModelScope, SharingStarted.Eagerly, null)\n", "vm flows")
    s = rep(s, "    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone(true) }\n",
        "    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone(true) }\n"
        "    fun setTourDone(done: Boolean) = viewModelScope.launch { settings.setTourDone(done) }\n"
        "    fun snoozeUpdatePrompt() = viewModelScope.launch {\n"
        "        settings.setUpdateSnoozeUntil(System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000)\n"
        "    }\n", "vm funcs")
    write(p, s)
else:
    print("MainViewModel: уже применено")

# ============ WayArsNavHost ============
p = P + "/presentation/ui/navigation/WayArsNavHost.kt"
s = read(p)
if "TourController" not in s:
    s = add_import(s, "import com.wayars.app.presentation.ui.tour.TourController\nimport com.wayars.app.presentation.ui.tour.TourHost",
                   "import com.wayars.app.presentation.ui.screen.terms.TermsGateScreen")
    s = rep(s, "    val packageHints by viewModel.packageHints.collectAsState()\n",
        "    val packageHints by viewModel.packageHints.collectAsState()\n"
        "    val tourDone by viewModel.tourDone.collectAsState()\n"
        "    val updateSnoozeUntil by viewModel.updateSnoozeUntil.collectAsState()\n"
        "    // One controller for the whole nav host: MainScreen drives the steps, TourHost (below) draws them\n"
        "    // above every route, including the status/navigation bar area.\n"
        "    val tour = remember { TourController() }\n", "navhost collect")
    s = rep(s, "                    onSubscriptionRequired = { goToPaywall() }\n                )\n",
        "                    onSubscriptionRequired = { goToPaywall() },\n"
        "                    tour = tour,\n"
        "                    tourDone = tourDone,\n"
        "                    onTourFinished = { viewModel.setTourDone(true) },\n"
        "                    updateSnoozeUntil = updateSnoozeUntil,\n"
        "                    onUpdateLater = { viewModel.snoozeUpdatePrompt() }\n"
        "                )\n", "navhost MainScreen call")
    tail = "        }\n    }\n}\n"
    idx = s.rstrip("\n").rfind("        }\n    }\n}")
    assert idx > 0, "navhost: нет конца файла"
    s = s[:idx] + "        }\n\n        TourHost(tour)\n    }\n}\n"
    write(p, s)
else:
    print("WayArsNavHost: уже применено")

# ============ DashboardScreen ============
p = P + "/presentation/ui/screen/dashboard/DashboardScreen.kt"
s = read(p)
if "TourTarget" not in s:
    s = add_import(s, "import com.wayars.app.presentation.ui.tour.TourTarget\nimport com.wayars.app.presentation.ui.tour.tourTarget", "import com.wayars.app.R")
    s = rep(s, '            Row(verticalAlignment = Alignment.CenterVertically) {\n                Text(\n                    "Active",',
               '            Row(\n                modifier = Modifier.tourTarget(TourTarget.ACTIVE_SWITCH),\n                verticalAlignment = Alignment.CenterVertically\n            ) {\n                Text(\n                    "Active",', "dashboard active row")
    s = rep(s, "            modifier = Modifier\n                .fillMaxWidth()\n                .clip(RoundedCornerShape(20.dp))\n                .background(WaSurface)\n                .padding(18.dp),",
               "            modifier = Modifier\n                .fillMaxWidth()\n                .tourTarget(TourTarget.SUMMARY_CARD)\n                .clip(RoundedCornerShape(20.dp))\n                .background(WaSurface)\n                .padding(18.dp),", "dashboard summary")
    s = rep(s, "        VerdictCard(\n            evaluation = latestEvaluation,\n            emptyLabel = stringResource(R.string.dashboard_no_order)\n        )",
               "        VerdictCard(\n            evaluation = latestEvaluation,\n            emptyLabel = stringResource(R.string.dashboard_no_order),\n            modifier = Modifier.tourTarget(TourTarget.VERDICT_CARD)\n        )", "dashboard verdict")
    write(p, s)
else:
    print("DashboardScreen: уже применено")

# ============ BottomNavBar ============
p = P + "/presentation/ui/component/BottomNavBar.kt"
s = read(p)
if "TourTarget" not in s:
    s = add_import(s, "import com.wayars.app.presentation.ui.tour.TourTarget\nimport com.wayars.app.presentation.ui.tour.tourTarget", "import com.wayars.app.R")
    s = rep(s, "    selected: Boolean,\n    onClick: () -> Unit\n) {\n    Column(\n        modifier = Modifier\n            .clip(RoundedCornerShape(22.dp))",
               "    selected: Boolean,\n    onClick: () -> Unit,\n    tourTarget: TourTarget? = null\n) {\n    Column(\n        modifier = Modifier\n            .tourTarget(tourTarget)\n            .clip(RoundedCornerShape(22.dp))", "navitem signature")
    s = rep(s, "            selected = current == MainTab.STATS,\n            onClick = { onSelect(MainTab.STATS) }\n",
               "            selected = current == MainTab.STATS,\n            onClick = { onSelect(MainTab.STATS) },\n            tourTarget = TourTarget.NAV_STATS\n", "nav stats")
    s = rep(s, "            selected = current == MainTab.PRESETS,\n            onClick = { onSelect(MainTab.PRESETS) }\n",
               "            selected = current == MainTab.PRESETS,\n            onClick = { onSelect(MainTab.PRESETS) },\n            tourTarget = TourTarget.NAV_PRESETS\n", "nav presets")
    write(p, s)
else:
    print("BottomNavBar: уже применено")

# ============ SettingsScreen ============
p = P + "/presentation/ui/screen/settings/SettingsScreen.kt"
s = read(p)
if "TourTarget" not in s:
    s = add_import(s, "import com.wayars.app.presentation.ui.tour.TourTarget\nimport com.wayars.app.presentation.ui.tour.tourTarget", "import com.wayars.app.R")
    s = add_import(s, "import androidx.compose.foundation.lazy.LazyListState", "import androidx.compose.foundation.lazy.LazyColumn")
    s = add_import(s, "import androidx.compose.material.icons.filled.School", "import androidx.compose.material.icons.filled.Info")
    s = rep(s, "    termsAcceptedAt: Long?,\n    modifier: Modifier = Modifier\n) {\n    LazyColumn(\n        modifier = modifier.fillMaxSize().padding(20.dp),",
               "    termsAcceptedAt: Long?,\n    modifier: Modifier = Modifier,\n    listState: LazyListState = rememberLazyListState(),\n    onReplayTour: () -> Unit = {}\n) {\n    LazyColumn(\n        state = listState,\n        modifier = modifier.fillMaxSize().padding(20.dp),", "settings signature")
    def wrap_item(s, call, target):
        start = "        item {\n            " + call + "("
        assert s.count(start) == 1, "settings item не найден: " + call
        i = s.index(start)
        end_marker = "\n        }\n"
        j = s.index(end_marker, i)
        body = s[i + len("        item {\n"): j]
        new = "        item {\n            Box(modifier = Modifier.tourTarget(TourTarget." + target + ")) {\n" + \
              "\n".join(("    " + ln) if ln.strip() else ln for ln in body.split("\n")) + "\n            }"
        return s[:i] + new + s[j:]
    s = wrap_item(s, "CustomThresholdsSection", "SETTINGS_THRESHOLDS")
    s = wrap_item(s, "VehicleSection", "SETTINGS_VEHICLE")
    s = wrap_item(s, "PermissionsSection", "SETTINGS_PERMISSIONS")
    s = wrap_item(s, "SupportedAppsSection", "SETTINGS_APPS")
    s = rep(s, "            InfoSection(termsAcceptedAt = termsAcceptedAt)\n",
               "            InfoSection(termsAcceptedAt = termsAcceptedAt, onReplayTour = onReplayTour)\n", "info call")
    s = rep(s, "private fun InfoSection(termsAcceptedAt: Long?) {", "private fun InfoSection(termsAcceptedAt: Long?, onReplayTour: () -> Unit) {", "info signature")
    s = rep(s, "        InfoRow(Icons.Filled.Info, stringResource(R.string.settings_about_title)) { activeDoc = InfoDoc.ABOUT }\n",
               "        InfoRow(Icons.Filled.School, stringResource(R.string.settings_tutorial_title)) { onReplayTour() }\n"
               "        HorizontalDivider(color = WaSurfaceVariant)\n"
               "        InfoRow(Icons.Filled.Info, stringResource(R.string.settings_about_title)) { activeDoc = InfoDoc.ABOUT }\n", "info rows")
    write(p, s)
else:
    print("SettingsScreen: уже применено")

# ============ strings.xml для всех языков ============
import sys
sys.path.insert(0, ".")
# -*- coding: utf-8 -*-
KEYS = [
 "tour_skip","tour_back","tour_next","tour_done",
 "tour_welcome_title","tour_welcome_text",
 "tour_active_title","tour_active_text",
 "tour_summary_title","tour_summary_text",
 "tour_verdict_title","tour_verdict_text",
 "tour_stats_title","tour_stats_text",
 "tour_presets_title","tour_presets_text",
 "tour_thresholds_title","tour_thresholds_text",
 "tour_vehicle_title","tour_vehicle_text",
 "tour_permissions_title","tour_permissions_text",
 "tour_apps_title","tour_apps_text",
 "tour_final_title","tour_final_text",
 "settings_tutorial_title",
 "update_available_title","update_available_text","update_now","update_later",
]
L = {}
L["values"] = [
 "Skip","Back","Next","Got it",
 "Welcome to WayArs",
 "A quick tour shows how WayArs rates your orders in real time. It takes about a minute, and you can replay it any time in Settings.",
 "Active switch",
 "Turn it on when you start working. WayArs then reads incoming orders in your delivery and taxi apps and rates each one for you.",
 "Today’s summary",
 "Orders, earnings, active time, distance and average rate per km for today. It updates after every order.",
 "Order evaluation",
 "Every order gets a verdict: good, average or bad. The same verdict also appears in a small floating widget over your delivery app.",
 "Stats",
 "The list of today’s rated orders with pay, distance, time and verdict.",
 "Presets",
 "Choose how strict the rating is: Economy, Balance or Profitable only.",
 "Your own thresholds",
 "Optional. Set your own rate-per-km limits for bad, average and good orders instead of using a preset.",
 "Vehicle and fuel",
 "Enter your vehicle, fuel type, consumption and fuel price, so the net profit of each order is calculated after fuel costs.",
 "Permissions",
 "Enable screen reading (read-only, no automatic taps) and the floating widget before turning Active on. Notification access is optional.",
 "Supported apps",
 "Popular delivery and taxi apps are built in. If yours is missing, add it here by hand.",
 "You’re all set!",
 "Enable the permissions in Settings, then switch Active on the Home screen. You can replay this tutorial any time in Settings.",
 "Tutorial",
 "Update available",
 "A new version of WayArs is available with improvements and fixes.",
 "Update","Later",
]
L["values-ru"] = [
 "Пропустить","Назад","Далее","Понятно",
 "Добро пожаловать в WayArs",
 "Короткий тур покажет, как WayArs оценивает ваши заказы в реальном времени. Это займёт около минуты, а пройти его снова можно в любой момент в Настройках.",
 "Переключатель Active",
 "Включайте его, когда выходите на линию. Тогда WayArs читает входящие заказы в приложениях доставки и такси и оценивает каждый из них.",
 "Итоги за сегодня",
 "Заказы, заработок, время в работе, расстояние и средняя ставка за км за сегодня. Данные обновляются после каждого заказа.",
 "Оценка заказа",
 "Каждый заказ получает вердикт: хороший, средний или плохой. Тот же вердикт показывается в небольшом плавающем виджете поверх приложения доставки.",
 "Статистика",
 "Список оценённых за сегодня заказов: оплата, расстояние, время и вердикт.",
 "Пресеты",
 "Выберите, насколько строгой будет оценка: экономный, сбалансированный или только выгодные заказы.",
 "Свои пороги",
 "Необязательно. Задайте свои границы ставки за км для плохих, средних и хороших заказов вместо готового пресета.",
 "Транспорт и топливо",
 "Укажите транспорт, тип топлива, расход и цену топлива, чтобы чистая прибыль по каждому заказу считалась с учётом затрат на топливо.",
 "Разрешения",
 "Перед включением Active включите чтение экрана (только чтение, без автоматических нажатий) и плавающий виджет. Доступ к уведомлениям — по желанию.",
 "Поддерживаемые приложения",
 "Популярные приложения доставки и такси уже добавлены. Если вашего нет в списке, добавьте его вручную.",
 "Всё готово!",
 "Включите разрешения в Настройках, затем включите Active на главном экране. Пройти обучение снова можно в любой момент в Настройках.",
 "Обучение",
 "Доступно обновление",
 "Вышла новая версия WayArs с улучшениями и исправлениями.",
 "Обновить","Позже",
]
L["values-uk"] = [
 "Пропустити","Назад","Далі","Зрозуміло",
 "Ласкаво просимо до WayArs",
 "Коротке навчання покаже, як WayArs оцінює ваші замовлення в реальному часі. Це займе близько хвилини, а пройти його знову можна будь-коли в Налаштуваннях.",
 "Перемикач Active",
 "Вмикайте його, коли виходите на лінію. Тоді WayArs читає вхідні замовлення в застосунках доставки й таксі та оцінює кожне з них.",
 "Підсумки за сьогодні",
 "Замовлення, заробіток, час у роботі, відстань і середня ставка за км за сьогодні. Дані оновлюються після кожного замовлення.",
 "Оцінка замовлення",
 "Кожне замовлення отримує вердикт: хороше, середнє чи погане. Той самий вердикт показується в невеликому плаваючому віджеті поверх застосунку доставки.",
 "Статистика",
 "Список оцінених за сьогодні замовлень: оплата, відстань, час і вердикт.",
 "Пресети",
 "Оберіть, наскільки суворою буде оцінка: економний, збалансований або лише вигідні замовлення.",
 "Власні пороги",
 "Необов’язково. Задайте власні межі ставки за км для поганих, середніх і хороших замовлень замість готового пресету.",
 "Транспорт і пальне",
 "Вкажіть транспорт, тип пального, витрату й ціну пального, щоб чистий прибуток за кожне замовлення рахувався з урахуванням витрат на пальне.",
 "Дозволи",
 "Перед вмиканням Active увімкніть читання екрана (лише читання, без автоматичних натискань) і плаваючий віджет. Доступ до сповіщень — за бажанням.",
 "Підтримувані застосунки",
 "Популярні застосунки доставки й таксі вже додано. Якщо вашого немає в списку, додайте його вручну.",
 "Усе готово!",
 "Увімкніть дозволи в Налаштуваннях, а потім увімкніть Active на головному екрані. Пройти навчання знову можна будь-коли в Налаштуваннях.",
 "Навчання",
 "Доступне оновлення",
 "Вийшла нова версія WayArs з покращеннями та виправленнями.",
 "Оновити","Пізніше",
]
L["values-pl"] = [
 "Pomiń","Wstecz","Dalej","Rozumiem",
 "Witaj w WayArs",
 "Krótki przewodnik pokaże, jak WayArs ocenia Twoje zamówienia w czasie rzeczywistym. Zajmie około minuty, a w Ustawieniach możesz go powtórzyć w dowolnej chwili.",
 "Przełącznik Active",
 "Włącz go, gdy zaczynasz pracę. WayArs czyta wtedy przychodzące zamówienia w aplikacjach dowozu i taksówek i ocenia każde z nich.",
 "Podsumowanie dnia",
 "Zamówienia, zarobki, czas pracy, dystans i średnia stawka za km z dzisiaj. Dane odświeżają się po każdym zamówieniu.",
 "Ocena zamówienia",
 "Każde zamówienie dostaje werdykt: dobre, średnie lub złe. Ten sam werdykt pojawia się w małym pływającym widżecie nad aplikacją dowozu.",
 "Statystyki",
 "Lista dzisiejszych ocenionych zamówień: wynagrodzenie, dystans, czas i werdykt.",
 "Presety",
 "Wybierz, jak surowa ma być ocena: oszczędny, zrównoważony albo tylko opłacalne zamówienia.",
 "Własne progi",
 "Opcjonalnie. Ustaw własne granice stawki za km dla zamówień złych, średnich i dobrych zamiast gotowego presetu.",
 "Pojazd i paliwo",
 "Podaj pojazd, rodzaj paliwa, spalanie i cenę paliwa, aby zysk netto każdego zamówienia był liczony po odjęciu kosztów paliwa.",
 "Uprawnienia",
 "Przed włączeniem Active włącz odczyt ekranu (tylko odczyt, bez automatycznych kliknięć) i pływający widżet. Dostęp do powiadomień jest opcjonalny.",
 "Obsługiwane aplikacje",
 "Popularne aplikacje dowozu i taksówek są już wbudowane. Jeśli Twojej brakuje, dodaj ją tutaj ręcznie.",
 "Wszystko gotowe!",
 "Włącz uprawnienia w Ustawieniach, a potem włącz Active na ekranie głównym. Ten samouczek możesz powtórzyć w Ustawieniach w dowolnej chwili.",
 "Samouczek",
 "Dostępna aktualizacja",
 "Dostępna jest nowa wersja WayArs z ulepszeniami i poprawkami.",
 "Aktualizuj","Później",
]
L["values-de"] = [
 "Überspringen","Zurück","Weiter","Verstanden",
 "Willkommen bei WayArs",
 "Eine kurze Tour zeigt, wie WayArs deine Aufträge in Echtzeit bewertet. Sie dauert etwa eine Minute und lässt sich jederzeit in den Einstellungen wiederholen.",
 "Schalter „Active“",
 "Schalte ihn ein, wenn du mit der Arbeit beginnst. WayArs liest dann eingehende Aufträge in deinen Liefer- und Taxi-Apps und bewertet jeden einzelnen.",
 "Heutige Übersicht",
 "Aufträge, Verdienst, aktive Zeit, Strecke und durchschnittlicher Preis pro km für heute. Sie aktualisiert sich nach jedem Auftrag.",
 "Auftragsbewertung",
 "Jeder Auftrag bekommt ein Urteil: gut, durchschnittlich oder schlecht. Dasselbe Urteil erscheint auch in einem kleinen schwebenden Widget über deiner Liefer-App.",
 "Statistik",
 "Die Liste der heute bewerteten Aufträge mit Bezahlung, Strecke, Zeit und Urteil.",
 "Voreinstellungen",
 "Wähle, wie streng die Bewertung sein soll: sparsam, ausgewogen oder nur lohnende Aufträge.",
 "Eigene Schwellenwerte",
 "Optional. Lege eigene Grenzen für den Preis pro km für schlechte, durchschnittliche und gute Aufträge fest, statt eine Voreinstellung zu nutzen.",
 "Fahrzeug und Kraftstoff",
 "Gib Fahrzeug, Kraftstoffart, Verbrauch und Kraftstoffpreis an, damit der Nettogewinn jedes Auftrags nach Abzug der Kraftstoffkosten berechnet wird.",
 "Berechtigungen",
 "Aktiviere vor dem Einschalten von Active das Lesen des Bildschirms (nur Lesen, keine automatischen Tipps) und das schwebende Widget. Der Zugriff auf Benachrichtigungen ist optional.",
 "Unterstützte Apps",
 "Beliebte Liefer- und Taxi-Apps sind bereits integriert. Fehlt deine App, füge sie hier von Hand hinzu.",
 "Alles bereit!",
 "Aktiviere die Berechtigungen in den Einstellungen und schalte dann Active auf dem Startbildschirm ein. Diese Anleitung kannst du jederzeit in den Einstellungen wiederholen.",
 "Anleitung",
 "Update verfügbar",
 "Eine neue Version von WayArs mit Verbesserungen und Fehlerbehebungen ist verfügbar.",
 "Aktualisieren","Später",
]
L["values-fr"] = [
 "Passer","Retour","Suivant","Compris",
 "Bienvenue dans WayArs",
 "Une courte visite montre comment WayArs évalue vos commandes en temps réel. Elle dure environ une minute et vous pouvez la revoir à tout moment dans les Réglages.",
 "Interrupteur Active",
 "Activez-le quand vous commencez à travailler. WayArs lit alors les commandes reçues dans vos applications de livraison et de taxi et évalue chacune d’elles.",
 "Résumé du jour",
 "Commandes, gains, temps actif, distance et tarif moyen au km pour aujourd’hui. Les données se mettent à jour après chaque commande.",
 "Évaluation de la commande",
 "Chaque commande reçoit un verdict : bonne, moyenne ou mauvaise. Le même verdict s’affiche aussi dans un petit widget flottant au-dessus de votre application de livraison.",
 "Statistiques",
 "La liste des commandes évaluées aujourd’hui avec la rémunération, la distance, la durée et le verdict.",
 "Préréglages",
 "Choisissez la sévérité de l’évaluation : économe, équilibré ou uniquement les commandes rentables.",
 "Vos propres seuils",
 "Facultatif. Définissez vos propres limites de tarif au km pour les commandes mauvaises, moyennes et bonnes, au lieu d’un préréglage.",
 "Véhicule et carburant",
 "Indiquez votre véhicule, le type de carburant, la consommation et le prix du carburant pour que le bénéfice net de chaque commande soit calculé après les frais de carburant.",
 "Autorisations",
 "Avant d’activer Active, autorisez la lecture de l’écran (lecture seule, sans appuis automatiques) et le widget flottant. L’accès aux notifications est facultatif.",
 "Applications prises en charge",
 "Les applications de livraison et de taxi populaires sont déjà intégrées. Si la vôtre manque, ajoutez-la ici manuellement.",
 "Tout est prêt !",
 "Activez les autorisations dans les Réglages, puis activez Active sur l’écran d’accueil. Vous pouvez revoir ce tutoriel à tout moment dans les Réglages.",
 "Tutoriel",
 "Mise à jour disponible",
 "Une nouvelle version de WayArs est disponible avec des améliorations et des corrections.",
 "Mettre à jour","Plus tard",
]
L["values-cs"] = [
 "Přeskočit","Zpět","Další","Rozumím",
 "Vítejte ve WayArs",
 "Krátká prohlídka ukáže, jak WayArs hodnotí vaše objednávky v reálném čase. Zabere asi minutu a v Nastavení ji můžete kdykoli zopakovat.",
 "Přepínač Active",
 "Zapněte ho, když začínáte pracovat. WayArs pak čte příchozí objednávky ve vašich aplikacích pro rozvoz a taxi a každou z nich ohodnotí.",
 "Dnešní přehled",
 "Objednávky, výdělek, aktivní čas, vzdálenost a průměrná sazba za km za dnešek. Aktualizuje se po každé objednávce.",
 "Hodnocení objednávky",
 "Každá objednávka dostane verdikt: dobrá, průměrná nebo špatná. Stejný verdikt se zobrazí i v malém plovoucím widgetu nad vaší aplikací pro rozvoz.",
 "Statistiky",
 "Seznam dnešních ohodnocených objednávek s odměnou, vzdáleností, časem a verdiktem.",
 "Předvolby",
 "Vyberte, jak přísné má hodnocení být: úsporné, vyvážené, nebo jen výdělečné objednávky.",
 "Vlastní hranice",
 "Volitelné. Nastavte si vlastní hranice sazby za km pro špatné, průměrné a dobré objednávky místo hotové předvolby.",
 "Vozidlo a palivo",
 "Zadejte vozidlo, druh paliva, spotřebu a cenu paliva, aby se čistý zisk každé objednávky počítal po odečtení nákladů na palivo.",
 "Oprávnění",
 "Před zapnutím Active povolte čtení obrazovky (pouze čtení, žádná automatická klepnutí) a plovoucí widget. Přístup k oznámením je volitelný.",
 "Podporované aplikace",
 "Oblíbené aplikace pro rozvoz a taxi jsou již zabudované. Pokud vaše chybí, přidejte ji tady ručně.",
 "Hotovo!",
 "Povolte oprávnění v Nastavení a poté zapněte Active na domovské obrazovce. Tuto nápovědu můžete kdykoli zopakovat v Nastavení.",
 "Nápověda",
 "Je dostupná aktualizace",
 "Je dostupná nová verze WayArs s vylepšeními a opravami.",
 "Aktualizovat","Později",
]
L["values-da"] = [
 "Spring over","Tilbage","Næste","Forstået",
 "Velkommen til WayArs",
 "En kort rundtur viser, hvordan WayArs vurderer dine ordrer i realtid. Den tager omkring et minut, og du kan gentage den når som helst i Indstillinger.",
 "Kontakten Active",
 "Slå den til, når du starter arbejdet. WayArs læser så indkommende ordrer i dine leverings- og taxa-apps og vurderer hver enkelt.",
 "Dagens oversigt",
 "Ordrer, indtjening, aktiv tid, afstand og gennemsnitlig takst pr. km for i dag. Den opdateres efter hver ordre.",
 "Vurdering af ordre",
 "Hver ordre får en dom: god, middel eller dårlig. Den samme dom vises også i en lille svævende widget over din leverings-app.",
 "Statistik",
 "Listen over dagens vurderede ordrer med betaling, afstand, tid og dom.",
 "Forudindstillinger",
 "Vælg, hvor streng vurderingen skal være: økonomisk, afbalanceret eller kun lønsomme ordrer.",
 "Dine egne grænser",
 "Valgfrit. Indstil dine egne grænser for takst pr. km for dårlige, middel og gode ordrer i stedet for en forudindstilling.",
 "Køretøj og brændstof",
 "Angiv køretøj, brændstoftype, forbrug og brændstofpris, så nettoindtjeningen for hver ordre beregnes efter brændstofudgifter.",
 "Tilladelser",
 "Før du slår Active til, skal du aktivere skærmlæsning (kun læsning, ingen automatiske tryk) og den svævende widget. Adgang til notifikationer er valgfri.",
 "Understøttede apps",
 "Populære leverings- og taxa-apps er indbygget. Mangler din, kan du tilføje den her manuelt.",
 "Alt er klar!",
 "Aktivér tilladelserne i Indstillinger, og slå derefter Active til på startskærmen. Du kan gentage denne vejledning når som helst i Indstillinger.",
 "Vejledning",
 "Opdatering tilgængelig",
 "En ny version af WayArs er tilgængelig med forbedringer og rettelser.",
 "Opdater","Senere",
]
L["values-nb"] = [
 "Hopp over","Tilbake","Neste","Forstått",
 "Velkommen til WayArs",
 "En kort omvisning viser hvordan WayArs vurderer bestillingene dine i sanntid. Den tar omtrent et minutt, og du kan gjenta den når som helst i Innstillinger.",
 "Bryteren Active",
 "Slå den på når du begynner å jobbe. WayArs leser da innkommende bestillinger i leverings- og drosjeappene dine og vurderer hver enkelt.",
 "Dagens oversikt",
 "Bestillinger, inntjening, aktiv tid, distanse og gjennomsnittlig sats per km for i dag. Den oppdateres etter hver bestilling.",
 "Vurdering av bestilling",
 "Hver bestilling får en dom: god, middels eller dårlig. Den samme dommen vises også i en liten flytende widget over leveringsappen din.",
 "Statistikk",
 "Listen over dagens vurderte bestillinger med betaling, distanse, tid og dom.",
 "Forhåndsinnstillinger",
 "Velg hvor streng vurderingen skal være: økonomisk, balansert eller bare lønnsomme bestillinger.",
 "Dine egne grenser",
 "Valgfritt. Angi dine egne grenser for sats per km for dårlige, middels og gode bestillinger i stedet for en forhåndsinnstilling.",
 "Kjøretøy og drivstoff",
 "Oppgi kjøretøy, drivstofftype, forbruk og drivstoffpris, slik at nettofortjenesten for hver bestilling beregnes etter drivstoffkostnader.",
 "Tillatelser",
 "Før du slår på Active, må du aktivere skjermlesing (bare lesing, ingen automatiske trykk) og den flytende widgeten. Tilgang til varsler er valgfritt.",
 "Støttede apper",
 "Populære leverings- og drosjeapper er innebygd. Mangler din, kan du legge den til her manuelt.",
 "Alt er klart!",
 "Aktiver tillatelsene i Innstillinger, og slå deretter på Active på startskjermen. Du kan gjenta denne veiledningen når som helst i Innstillinger.",
 "Veiledning",
 "Oppdatering tilgjengelig",
 "En ny versjon av WayArs er tilgjengelig med forbedringer og rettelser.",
 "Oppdater","Senere",
]
L["values-sv"] = [
 "Hoppa över","Tillbaka","Nästa","Uppfattat",
 "Välkommen till WayArs",
 "En kort rundtur visar hur WayArs bedömer dina beställningar i realtid. Den tar ungefär en minut och du kan göra om den när som helst i Inställningar.",
 "Reglaget Active",
 "Slå på det när du börjar jobba. WayArs läser då inkommande beställningar i dina leverans- och taxiappar och bedömer varje enskild.",
 "Dagens sammanfattning",
 "Beställningar, intäkter, aktiv tid, sträcka och genomsnittligt pris per km för i dag. Den uppdateras efter varje beställning.",
 "Bedömning av beställning",
 "Varje beställning får ett utlåtande: bra, medel eller dålig. Samma utlåtande visas också i en liten flytande widget ovanpå din leveransapp.",
 "Statistik",
 "Listan över dagens bedömda beställningar med betalning, sträcka, tid och utlåtande.",
 "Förval",
 "Välj hur strikt bedömningen ska vara: ekonomisk, balanserad eller bara lönsamma beställningar.",
 "Egna gränser",
 "Valfritt. Ange egna gränser för pris per km för dåliga, medelgoda och bra beställningar i stället för ett förval.",
 "Fordon och bränsle",
 "Ange fordon, bränsletyp, förbrukning och bränslepris så att nettovinsten för varje beställning räknas efter bränslekostnader.",
 "Behörigheter",
 "Innan du slår på Active ska du aktivera skärmläsning (endast läsning, inga automatiska tryck) och den flytande widgeten. Åtkomst till aviseringar är valfri.",
 "Appar som stöds",
 "Populära leverans- och taxiappar finns redan inbyggda. Saknas din kan du lägga till den här manuellt.",
 "Allt är klart!",
 "Aktivera behörigheterna i Inställningar och slå sedan på Active på startskärmen. Du kan göra om den här genomgången när som helst i Inställningar.",
 "Genomgång",
 "Uppdatering tillgänglig",
 "En ny version av WayArs finns tillgänglig med förbättringar och rättelser.",
 "Uppdatera","Senare",
]
L["values-ro"] = [
 "Omite","Înapoi","Înainte","Am înțeles",
 "Bun venit în WayArs",
 "Un scurt tur îți arată cum evaluează WayArs comenzile tale în timp real. Durează aproximativ un minut și îl poți relua oricând în Setări.",
 "Comutatorul Active",
 "Activează-l când începi lucrul. WayArs citește apoi comenzile primite în aplicațiile tale de livrare și taxi și le evaluează pe fiecare.",
 "Rezumatul de azi",
 "Comenzi, câștiguri, timp activ, distanță și tarif mediu pe km pentru azi. Se actualizează după fiecare comandă.",
 "Evaluarea comenzii",
 "Fiecare comandă primește un verdict: bună, medie sau slabă. Același verdict apare și într-un mic widget plutitor peste aplicația ta de livrare.",
 "Statistici",
 "Lista comenzilor evaluate azi, cu plata, distanța, timpul și verdictul.",
 "Presetări",
 "Alege cât de strictă să fie evaluarea: economă, echilibrată sau doar comenzi profitabile.",
 "Pragurile tale",
 "Opțional. Setează-ți propriile limite de tarif pe km pentru comenzile slabe, medii și bune, în loc de o presetare.",
 "Vehicul și combustibil",
 "Introdu vehiculul, tipul de combustibil, consumul și prețul combustibilului, astfel încât profitul net al fiecărei comenzi să fie calculat după costurile cu combustibilul.",
 "Permisiuni",
 "Înainte să pornești Active, activează citirea ecranului (doar citire, fără atingeri automate) și widgetul plutitor. Accesul la notificări este opțional.",
 "Aplicații acceptate",
 "Aplicațiile populare de livrare și taxi sunt deja incluse. Dacă a ta lipsește, adaug-o aici manual.",
 "Totul este gata!",
 "Activează permisiunile în Setări, apoi pornește Active pe ecranul principal. Poți relua acest tutorial oricând în Setări.",
 "Tutorial",
 "Actualizare disponibilă",
 "Este disponibilă o nouă versiune WayArs cu îmbunătățiri și remedieri.",
 "Actualizează","Mai târziu",
]
L["values-hu"] = [
 "Kihagyás","Vissza","Tovább","Rendben",
 "Üdvözöljük a WayArs alkalmazásban",
 "Egy rövid bemutató megmutatja, hogyan értékeli a WayArs a rendeléseidet valós időben. Körülbelül egy percig tart, és a Beállításokban bármikor megismételheted.",
 "Az Active kapcsoló",
 "Kapcsold be, amikor elkezdesz dolgozni. A WayArs ilyenkor beolvassa a beérkező rendeléseket a futár- és taxialkalmazásaidban, és mindegyiket értékeli.",
 "Mai összegzés",
 "Mai rendelések, kereset, aktív idő, távolság és átlagos km-díj. Minden rendelés után frissül.",
 "A rendelés értékelése",
 "Minden rendelés kap egy értékelést: jó, közepes vagy rossz. Ugyanez az értékelés egy kis lebegő widgetben is megjelenik a futáralkalmazásod felett.",
 "Statisztika",
 "A mai értékelt rendelések listája fizetéssel, távolsággal, idővel és értékeléssel.",
 "Előbeállítások",
 "Válaszd ki, mennyire legyen szigorú az értékelés: takarékos, kiegyensúlyozott vagy csak a kifizetődő rendelések.",
 "Saját küszöbértékek",
 "Nem kötelező. Állítsd be saját km-díj-határaidat a rossz, közepes és jó rendelésekhez az előbeállítás helyett.",
 "Jármű és üzemanyag",
 "Add meg a járművet, az üzemanyag típusát, a fogyasztást és az üzemanyag árát, hogy minden rendelés nettó nyeresége az üzemanyagköltség levonása után legyen kiszámítva.",
 "Engedélyek",
 "Az Active bekapcsolása előtt engedélyezd a képernyő olvasását (csak olvasás, automatikus koppintások nélkül) és a lebegő widgetet. Az értesítésekhez való hozzáférés nem kötelező.",
 "Támogatott alkalmazások",
 "A népszerű futár- és taxialkalmazások már be vannak építve. Ha a tiéd hiányzik, add hozzá itt kézzel.",
 "Minden kész!",
 "Engedélyezd a jogosultságokat a Beállításokban, majd kapcsold be az Active-ot a főképernyőn. Ezt a bemutatót bármikor megismételheted a Beállításokban.",
 "Bemutató",
 "Frissítés érhető el",
 "A WayArs új verziója érhető el fejlesztésekkel és javításokkal.",
 "Frissítés","Később",
]
L["values-bg"] = [
 "Пропусни","Назад","Напред","Разбрах",
 "Добре дошли във WayArs",
 "Кратка обиколка показва как WayArs оценява поръчките ви в реално време. Отнема около минута и можете да я повторите по всяко време в Настройки.",
 "Превключвателят Active",
 "Включвайте го, когато започвате работа. Тогава WayArs чете входящите поръчки във вашите приложения за доставка и такси и оценява всяка от тях.",
 "Обобщение за днес",
 "Поръчки, приходи, активно време, разстояние и средна ставка за км за днес. Обновява се след всяка поръчка.",
 "Оценка на поръчката",
 "Всяка поръчка получава оценка: добра, средна или лоша. Същата оценка се показва и в малък плаващ уиджет над приложението за доставка.",
 "Статистика",
 "Списъкът с оценените днес поръчки с плащане, разстояние, време и оценка.",
 "Предварителни настройки",
 "Изберете колко строга да е оценката: икономична, балансирана или само изгодни поръчки.",
 "Собствени прагове",
 "По избор. Задайте собствени граници на ставката за км за лоши, средни и добри поръчки вместо готова настройка.",
 "Превозно средство и гориво",
 "Въведете превозното средство, вида гориво, разхода и цената на горивото, за да се изчислява нетната печалба от всяка поръчка след разходите за гориво.",
 "Разрешения",
 "Преди да включите Active, разрешете четенето на екрана (само четене, без автоматични докосвания) и плаващия уиджет. Достъпът до известия е по избор.",
 "Поддържани приложения",
 "Популярните приложения за доставка и такси са вградени. Ако вашето липсва, добавете го тук ръчно.",
 "Всичко е готово!",
 "Разрешете достъпа в Настройки, след това включете Active на началния екран. Можете да повторите това обучение по всяко време в Настройки.",
 "Обучение",
 "Налична е актуализация",
 "Налична е нова версия на WayArs с подобрения и поправки.",
 "Актуализирай","По-късно",
]
L["values-az"] = [
 "Keç","Geri","Növbəti","Aydındır",
 "WayArs-a xoş gəlmisiniz",
 "Qısa tur WayArs-ın sifarişlərinizi real vaxtda necə qiymətləndirdiyini göstərir. Təxminən bir dəqiqə çəkir, istənilən vaxt Ayarlarda yenidən keçə bilərsiniz.",
 "Active açarı",
 "İşə başlayanda onu aktiv edin. Onda WayArs çatdırılma və taksi tətbiqlərinizdə gələn sifarişləri oxuyur və hər birini qiymətləndirir.",
 "Bugünkü yekun",
 "Bu günün sifarişləri, qazancı, aktiv vaxtı, məsafəsi və km üzrə orta tarifi. Hər sifarişdən sonra yenilənir.",
 "Sifarişin qiymətləndirilməsi",
 "Hər sifariş qiymət alır: yaxşı, orta və ya pis. Eyni qiymət çatdırılma tətbiqinizin üzərində kiçik üzən vidcetdə də göstərilir.",
 "Statistika",
 "Bu gün qiymətləndirilmiş sifarişlərin siyahısı: ödəniş, məsafə, vaxt və qiymət.",
 "Hazır rejimlər",
 "Qiymətləndirmənin nə qədər sərt olacağını seçin: qənaətcil, balanslı və ya yalnız sərfəli sifarişlər.",
 "Öz hədləriniz",
 "İstəyə bağlı. Hazır rejim əvəzinə pis, orta və yaxşı sifarişlər üçün km üzrə öz tarif hədlərinizi təyin edin.",
 "Nəqliyyat və yanacaq",
 "Nəqliyyat vasitəsini, yanacaq növünü, sərfiyyatı və yanacaq qiymətini daxil edin ki, hər sifarişin xalis mənfəəti yanacaq xərcləri çıxılmaqla hesablansın.",
 "İcazələr",
 "Active-i yandırmazdan əvvəl ekranın oxunmasına (yalnız oxuma, avtomatik toxunuşlar yoxdur) və üzən vidcetə icazə verin. Bildirişlərə giriş istəyə bağlıdır.",
 "Dəstəklənən tətbiqlər",
 "Məşhur çatdırılma və taksi tətbiqləri artıq daxildir. Sizinki yoxdursa, onu burada əl ilə əlavə edin.",
 "Hər şey hazırdır!",
 "İcazələri Ayarlarda aktiv edin, sonra əsas ekranda Active-i yandırın. Bu təlimatı istənilən vaxt Ayarlarda təkrar keçə bilərsiniz.",
 "Təlimat",
 "Yeniləmə mövcuddur",
 "WayArs-ın təkmilləşdirmələr və düzəlişlərlə yeni versiyası mövcuddur.",
 "Yenilə","Sonra",
]
L["values-uz"] = [
 "O‘tkazib yuborish","Orqaga","Keyingi","Tushunarli",
 "WayArs ilovasiga xush kelibsiz",
 "Qisqa tur WayArs buyurtmalaringizni real vaqtda qanday baholashini ko‘rsatadi. U taxminan bir daqiqa davom etadi, uni istalgan vaqtda Sozlamalarda qayta ko‘rishingiz mumkin.",
 "Active tugmasi",
 "Ishni boshlaganda uni yoqing. Shunda WayArs yetkazib berish va taksi ilovalaringizdagi kelayotgan buyurtmalarni o‘qiydi va har birini baholaydi.",
 "Bugungi xulosa",
 "Bugungi buyurtmalar, daromad, faol vaqt, masofa va har km uchun o‘rtacha narx. Har bir buyurtmadan keyin yangilanadi.",
 "Buyurtmani baholash",
 "Har bir buyurtma baho oladi: yaxshi, o‘rtacha yoki yomon. Xuddi shu baho yetkazib berish ilovangiz ustidagi kichik suzuvchi vidjetda ham ko‘rsatiladi.",
 "Statistika",
 "Bugun baholangan buyurtmalar ro‘yxati: to‘lov, masofa, vaqt va baho.",
 "Tayyor rejimlar",
 "Baholash qanchalik qat’iy bo‘lishini tanlang: tejamkor, muvozanatli yoki faqat foydali buyurtmalar.",
 "O‘z chegaralaringiz",
 "Ixtiyoriy. Tayyor rejim o‘rniga yomon, o‘rtacha va yaxshi buyurtmalar uchun har km narxining o‘z chegaralarini belgilang.",
 "Transport va yoqilg‘i",
 "Transport vositasi, yoqilg‘i turi, sarfi va narxini kiriting, shunda har bir buyurtmaning sof foydasi yoqilg‘i xarajatlaridan keyin hisoblanadi.",
 "Ruxsatlar",
 "Active’ni yoqishdan oldin ekranni o‘qishga (faqat o‘qish, avtomatik bosishlarsiz) va suzuvchi vidjetga ruxsat bering. Bildirishnomalarga kirish ixtiyoriy.",
 "Qo‘llab-quvvatlanadigan ilovalar",
 "Mashhur yetkazib berish va taksi ilovalari allaqachon qo‘shilgan. Sizniki yo‘q bo‘lsa, uni shu yerda qo‘lda qo‘shing.",
 "Hammasi tayyor!",
 "Ruxsatlarni Sozlamalarda yoqing, so‘ng bosh ekranda Active’ni yoqing. Bu qo‘llanmani istalgan vaqtda Sozlamalarda qayta ko‘rishingiz mumkin.",
 "Qo‘llanma",
 "Yangilanish mavjud",
 "WayArs’ning yaxshilanishlar va tuzatishlar bilan yangi versiyasi mavjud.",
 "Yangilash","Keyinroq",
]
L["values-ka"] = [
 "გამოტოვება","უკან","შემდეგი","გასაგებია",
 "კეთილი იყოს თქვენი მობრძანება WayArs-ში",
 "მოკლე ტური გაჩვენებთ, როგორ აფასებს WayArs თქვენს შეკვეთებს რეალურ დროში. ის დაახლოებით ერთ წუთს გრძელდება და მის გამეორებას ნებისმიერ დროს შეძლებთ პარამეტრებში.",
 "Active-ის გადამრთველი",
 "ჩართეთ, როცა მუშაობას იწყებთ. მაშინ WayArs კითხულობს შემომავალ შეკვეთებს თქვენს მიწოდებისა და ტაქსის აპებში და თითოეულს აფასებს.",
 "დღევანდელი შეჯამება",
 "დღევანდელი შეკვეთები, შემოსავალი, აქტიური დრო, მანძილი და საშუალო ტარიფი კმ-ზე. ახლდება ყოველი შეკვეთის შემდეგ.",
 "შეკვეთის შეფასება",
 "თითოეული შეკვეთა იღებს შეფასებას: კარგი, საშუალო ან ცუდი. იგივე შეფასება გამოჩნდება პატარა მოტივტივე ვიჯეტშიც მიწოდების აპზე.",
 "სტატისტიკა",
 "დღეს შეფასებული შეკვეთების სია: ანაზღაურება, მანძილი, დრო და შეფასება.",
 "წინასწარი რეჟიმები",
 "აირჩიეთ, რამდენად მკაცრი იყოს შეფასება: ეკონომიური, დაბალანსებული ან მხოლოდ მომგებიანი შეკვეთები.",
 "თქვენი საკუთარი ზღვრები",
 "არასავალდებულო. წინასწარი რეჟიმის ნაცვლად დააყენეთ საკუთარი ტარიფის ზღვრები კმ-ზე ცუდი, საშუალო და კარგი შეკვეთებისთვის.",
 "ტრანსპორტი და საწვავი",
 "მიუთითეთ ტრანსპორტი, საწვავის ტიპი, ხარჯი და საწვავის ფასი, რათა თითოეული შეკვეთის წმინდა მოგება საწვავის ხარჯის გათვალისწინებით დაითვალოს.",
 "ნებართვები",
 "Active-ის ჩართვამდე ჩართეთ ეკრანის წაკითხვა (მხოლოდ წაკითხვა, ავტომატური შეხებების გარეშე) და მოტივტივე ვიჯეტი. შეტყობინებებზე წვდომა არასავალდებულოა.",
 "მხარდაჭერილი აპები",
 "პოპულარული მიწოდებისა და ტაქსის აპები უკვე ჩაშენებულია. თუ თქვენი აკლია, დაამატეთ აქ ხელით.",
 "ყველაფერი მზადაა!",
 "ჩართეთ ნებართვები პარამეტრებში, შემდეგ ჩართეთ Active მთავარ ეკრანზე. ამ სახელმძღვანელოს ნებისმიერ დროს გაიმეორებთ პარამეტრებში.",
 "სახელმძღვანელო",
 "ხელმისაწვდომია განახლება",
 "ხელმისაწვდომია WayArs-ის ახალი ვერსია გაუმჯობესებებით და შესწორებებით.",
 "განახლება","მოგვიანებით",
]
L["values-hy"] = [
 "Բաց թողնել","Հետ","Հաջորդը","Հասկանալի է",
 "Բարի գալուստ WayArs",
 "Կարճ շրջայցը ցույց կտա, թե ինչպես է WayArs-ը իրական ժամանակում գնահատում ձեր պատվերները։ Այն տևում է մոտ մեկ րոպե, և այն կարող եք ցանկացած ժամանակ կրկնել Կարգավորումներում։",
 "Active անջատիչը",
 "Միացրեք այն, երբ սկսում եք աշխատել։ Այդ ժամանակ WayArs-ը կարդում է ձեր առաքման և տաքսիի հավելվածներում մուտք եղած պատվերները և գնահատում յուրաքանչյուրը։",
 "Այսօրվա ամփոփումը",
 "Այսօրվա պատվերները, եկամուտը, ակտիվ ժամանակը, հեռավորությունը և միջին սակագինը մեկ կմ-ի համար։ Թարմացվում է յուրաքանչյուր պատվերից հետո։",
 "Պատվերի գնահատումը",
 "Յուրաքանչյուր պատվեր ստանում է գնահատական՝ լավ, միջին կամ վատ։ Նույն գնահատականը երևում է նաև առաքման հավելվածի վրա լողացող փոքր վիջեթում։",
 "Վիճակագրություն",
 "Այսօր գնահատված պատվերների ցանկը՝ վճարով, հեռավորությամբ, ժամանակով և գնահատականով։",
 "Պատրաստի ռեժիմներ",
 "Ընտրեք՝ որքան խիստ լինի գնահատումը՝ խնայող, հավասարակշռված կամ միայն շահութաբեր պատվերներ։",
 "Ձեր սեփական շեմերը",
 "Ըստ ցանկության։ Պատրաստի ռեժիմի փոխարեն սահմանեք ձեր սեփական սակագնի սահմանները մեկ կմ-ի համար վատ, միջին և լավ պատվերների համար։",
 "Տրանսպորտ և վառելիք",
 "Նշեք տրանսպորտը, վառելիքի տեսակը, ծախսը և վառելիքի գինը, որպեսզի յուրաքանչյուր պատվերի զուտ շահույթը հաշվարկվի վառելիքի ծախսերը հանելուց հետո։",
 "Թույլտվություններ",
 "Active-ը միացնելուց առաջ թույլատրեք էկրանի ընթերցումը (միայն ընթերցում, առանց ավտոմատ հպումների) և լողացող վիջեթը։ Ծանուցումներին հասանելիությունը պարտադիր չէ։",
 "Աջակցվող հավելվածներ",
 "Առաքման և տաքսիի հանրաճանաչ հավելվածներն արդեն ներառված են։ Եթե ձերը չկա, ավելացրեք այն այստեղ ձեռքով։",
 "Ամեն ինչ պատրաստ է։",
 "Թույլատրեք հասանելիությունը Կարգավորումներում, ապա միացրեք Active-ը գլխավոր էկրանին։ Այս ուղեցույցը կարող եք ցանկացած ժամանակ կրկնել Կարգավորումներում։",
 "Ուղեցույց",
 "Հասանելի է թարմացում",
 "Հասանելի է WayArs-ի նոր տարբերակը՝ բարելավումներով և շտկումներով։",
 "Թարմացնել","Ավելի ուշ",
]
for k, v in L.items():
    assert len(v) == len(KEYS), (k, len(v), len(KEYS))

for d, vals in L.items():
    p = M + "/res/" + d + "/strings.xml"
    s = read(p)
    if "tour_welcome_title" in s:
        print(d + ": строки уже добавлены"); continue
    lines = ["", "    <!-- ===== Update 07: first-run tour + update prompt ===== -->"]
    if d == "values":
        lines.append('    <string name="tour_counter" translatable="false">%1$d / %2$d</string>')
    for k, v in zip(KEYS, vals):
        assert "'" not in v and "&" not in v and "<" not in v and not v.startswith("@") and not v.startswith("?"), (d, k)
        lines.append('    <string name="%s">%s</string>' % (k, v))
    idx = s.rfind("</resources>")
    assert idx > 0, d + ": нет </resources>"
    s = s[:idx].rstrip("\n") + "\n" + "\n".join(lines) + "\n</resources>\n"
    write(p, s)

PY

echo
echo "Готово (WayArs). Бэкапы: $BK"
echo "Дальше: git add app .gitignore update_project-07-onboarding-tour-and-update-prompt.sh && git commit && git pull --rebase && git push"
