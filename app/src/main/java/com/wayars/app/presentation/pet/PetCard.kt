package com.wayars.app.presentation.pet

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import com.wayars.app.R
import com.wayars.app.pet.PetState
import com.wayars.app.presentation.ui.component.MainTab
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.presentation.ui.tour.TourStep
import com.wayars.app.presentation.ui.tour.TourTarget
import com.wayars.app.presentation.ui.tour.SettingsItems
import com.wayars.app.service.accessibility.ActiveSessionTracker
import com.wayars.app.util.AccessibilityUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One question the pet can answer; [step] (if any) is what "Show me" spotlights. */
class PetHelpTopic(@StringRes val question: Int, @StringRes val answer: Int, val step: TourStep?)

private fun topic(q: Int, a: Int, target: TourTarget?, tab: MainTab = MainTab.SETTINGS, item: Int? = null) =
    PetHelpTopic(q, a, target?.let { TourStep(it, tab, item, q, a) })

// Settings list item positions: see SettingsItems (TourController.kt).
val PET_HELP_TOPICS: List<PetHelpTopic> = listOf(
    topic(R.string.pet_q_active, R.string.pet_a_active, TourTarget.ACTIVE_SWITCH, MainTab.HOME),
    topic(R.string.pet_q_overlay, R.string.pet_a_overlay, TourTarget.SETTINGS_PERMISSIONS, item = SettingsItems.PERMISSIONS),
    topic(R.string.pet_q_access, R.string.pet_a_access, TourTarget.SETTINGS_PERMISSIONS, item = SettingsItems.PERMISSIONS),
    topic(R.string.pet_q_add_app, R.string.pet_a_add_app, TourTarget.SETTINGS_APPS, item = SettingsItems.APPS),
    topic(R.string.pet_q_calibrate, R.string.pet_a_calibrate, TourTarget.SETTINGS_APPS, item = SettingsItems.APPS),
    topic(R.string.pet_q_currency, R.string.pet_a_currency, TourTarget.SETTINGS_CURRENCY, item = SettingsItems.GENERAL),
    topic(R.string.pet_q_tax, R.string.pet_a_tax, TourTarget.SETTINGS_TAX, item = SettingsItems.TAX),
    topic(R.string.pet_q_vehicle, R.string.pet_a_vehicle, TourTarget.SETTINGS_VEHICLE, item = SettingsItems.VEHICLE),
    topic(R.string.pet_q_thresholds, R.string.pet_a_thresholds, TourTarget.SETTINGS_THRESHOLDS, item = SettingsItems.THRESHOLDS),
    topic(R.string.pet_q_presets, R.string.pet_a_presets, TourTarget.NAV_PRESETS, MainTab.PRESETS),
    topic(R.string.pet_q_stats, R.string.pet_a_stats, TourTarget.NAV_STATS, MainTab.STATS),
    topic(R.string.pet_q_verdict, R.string.pet_a_verdict, TourTarget.VERDICT_CARD, MainTab.HOME),
    topic(R.string.pet_q_subscription, R.string.pet_a_subscription, TourTarget.SETTINGS_SUBSCRIPTION, item = SettingsItems.SUBSCRIPTION),
    topic(R.string.pet_q_contact, R.string.pet_a_contact, TourTarget.SETTINGS_INFO, item = SettingsItems.INFO),
    topic(R.string.pet_q_pet, R.string.pet_a_pet, null),
    topic(R.string.pet_q_petting, R.string.pet_a_petting, null),
    topic(R.string.pet_q_diagnostics, R.string.pet_a_diagnostics, null),
    topic(R.string.pet_q_apps, R.string.pet_a_apps, TourTarget.SETTINGS_APPS, item = SettingsItems.APPS),
    topic(R.string.pet_q_clicks, R.string.pet_a_clicks, null),
    topic(R.string.pet_q_privacy, R.string.pet_a_privacy, TourTarget.SETTINGS_INFO, item = SettingsItems.INFO),
    topic(R.string.pet_q_wrong_numbers, R.string.pet_a_wrong_numbers, TourTarget.SETTINGS_APPS, item = SettingsItems.APPS),
    topic(R.string.pet_q_move_card, R.string.pet_a_move_card, null),
    topic(R.string.pet_q_buttons, R.string.pet_a_buttons, null),
    topic(R.string.pet_q_net, R.string.pet_a_net, TourTarget.SETTINGS_VEHICLE, item = SettingsItems.VEHICLE),
    topic(R.string.pet_q_no_time, R.string.pet_a_no_time, null),
    topic(R.string.pet_q_pickup, R.string.pet_a_pickup, null),
    topic(R.string.pet_q_trial, R.string.pet_a_trial, TourTarget.SETTINGS_SUBSCRIPTION, item = SettingsItems.SUBSCRIPTION),
    topic(R.string.pet_q_restore, R.string.pet_a_restore, TourTarget.SETTINGS_SUBSCRIPTION, item = SettingsItems.SUBSCRIPTION),
    topic(R.string.pet_q_cancel, R.string.pet_a_cancel, null),
    topic(R.string.pet_q_language, R.string.pet_a_language, TourTarget.SETTINGS_CURRENCY, item = SettingsItems.GENERAL),
    topic(R.string.pet_q_tutorial, R.string.pet_a_tutorial, TourTarget.SETTINGS_INFO, item = SettingsItems.INFO),
    topic(R.string.pet_q_turns_off, R.string.pet_a_turns_off, TourTarget.ACTIVE_SWITCH, MainTab.HOME),
    topic(R.string.pet_q_background, R.string.pet_a_background, null),
    topic(R.string.pet_q_several, R.string.pet_a_several, TourTarget.SETTINGS_APPS, item = SettingsItems.APPS),
    topic(R.string.pet_q_battery, R.string.pet_a_battery, TourTarget.SETTINGS_PERMISSIONS, item = SettingsItems.PERMISSIONS),
    topic(R.string.pet_q_stats_count, R.string.pet_a_stats_count, TourTarget.NAV_STATS, MainTab.STATS),
    topic(R.string.pet_q_notifications, R.string.pet_a_notifications, TourTarget.SETTINGS_PERMISSIONS, item = SettingsItems.PERMISSIONS),
    topic(R.string.pet_q_country, R.string.pet_a_country, TourTarget.SETTINGS_CURRENCY, item = SettingsItems.GENERAL),
    topic(R.string.pet_q_reset_calibration, R.string.pet_a_reset_calibration, TourTarget.SETTINGS_APPS, item = SettingsItems.APPS),
    topic(R.string.pet_q_documents, R.string.pet_a_documents, TourTarget.SETTINGS_INFO, item = SettingsItems.INFO),
    topic(R.string.pet_q_hourly, R.string.pet_a_hourly, TourTarget.STATS_HOURLY, MainTab.STATS),
    topic(R.string.pet_q_compare, R.string.pet_a_compare, TourTarget.NAV_STATS, MainTab.STATS),
    topic(R.string.pet_q_partner, R.string.pet_a_partner, TourTarget.SETTINGS_PARTNER, item = SettingsItems.PARTNER),
    topic(R.string.pet_q_weekly_fee, R.string.pet_a_weekly_fee, TourTarget.SETTINGS_PARTNER, item = SettingsItems.PARTNER),
    topic(R.string.pet_q_summaries, R.string.pet_a_summaries, TourTarget.SETTINGS_EXTRAS, item = SettingsItems.EXTRAS),
    topic(R.string.pet_q_widget, R.string.pet_a_widget, TourTarget.SETTINGS_EXTRAS, item = SettingsItems.EXTRAS),
    topic(R.string.pet_q_achievements, R.string.pet_a_achievements, TourTarget.NAV_STATS, MainTab.STATS),
    topic(R.string.pet_q_invite, R.string.pet_a_invite, TourTarget.SETTINGS_EXTRAS, item = SettingsItems.EXTRAS),
    topic(R.string.pet_q_watch, R.string.pet_a_watch, TourTarget.SETTINGS_EXTRAS, item = SettingsItems.EXTRAS)
)

/** One live check of the phone/app state for "Check my phone". */
internal class DiagItem(@StringRes val label: Int, val ok: Boolean, val fix: (() -> Unit)?)

private const val REACTION_MS = 6_000L
private const val SLEEP_AFTER_MS = 24L * 60 * 60 * 1000
private const val BREAK_AFTER_MS = 5L * 60 * 60 * 1000

/**
 * The pet on the Home screen: picture, level, energy bar and what it "says" right now.
 * Tapping the pet or "Ask" opens the helper.
 */
@Composable
fun PetCard(
    isActive: Boolean,
    onShowTopic: (TourStep) -> Unit,
    modifier: Modifier = Modifier,
    isSubscribed: Boolean = false
) {
    val context = LocalContext.current
    val pet by PetState.state.collectAsState()
    var helpOpen by remember { mutableStateOf(false) }
    var listMode by remember { mutableStateOf(false) }
    var hopKey by remember { mutableStateOf(0) }
    // Stroking: while the finger moves over Wayo, and a short happy afterglow when it stops.
    var petting by remember { mutableStateOf(false) }
    var afterglow by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf(0) }
    var strokeDistance by remember { mutableStateOf(0f) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(afterglow) {
        if (afterglow) { delay(3_000); afterglow = false }
    }
    val scope = rememberCoroutineScope()
    val gainFloat = remember { Animatable(1f) }
    LaunchedEffect(pet.reactionAt) {
        if (pet.reactionAt > 0 && pet.lastGain > 0 && System.currentTimeMillis() - pet.reactionAt < 3_000) {
            gainFloat.snapTo(0f)
            gainFloat.animateTo(1f, tween(1400))
        }
    }

    // Ticks while a reaction is on screen, so the pet calms down by itself afterwards.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pet.reactionAt) {
        now = System.currentTimeMillis()
        while (now - pet.reactionAt < REACTION_MS + 500) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val permissionsOk by produceState(initialValue = true) {
        while (true) {
            value = AccessibilityUtils.isServiceEnabled(context) && Settings.canDrawOverlays(context)
            delay(5_000)
        }
    }

    // Greeting / praise each time the app is opened (at most every 20 min): time of day,
    // "I missed you" after a long break, or praise from today's real numbers.
    var greeting by remember { mutableStateOf<String?>(null) }
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(resumeTick) {
        if (resumeTick == 0) return@LaunchedEffect
        val text = PetGreeter.next(context, pet.lastActivityAt) ?: return@LaunchedEffect
        greeting = text
        delay(9_000)
        greeting = null
    }

    val reactionFresh = pet.reaction != PetState.Reaction.NONE && now - pet.reactionAt < REACTION_MS
    val mood = when {
        helpOpen -> PetMood.HELPER
        petting -> PetMood.NEUTRAL
        afterglow -> PetMood.HAPPY
        greeting != null && !reactionFresh && permissionsOk -> PetMood.HELLO
        reactionFresh -> when (pet.reaction) {
            PetState.Reaction.GOOD_ACCEPTED -> PetMood.HAPPY
            PetState.Reaction.SMART_REJECT -> PetMood.COOL
            PetState.Reaction.LEVEL_UP -> PetMood.LEVEL_UP
            PetState.Reaction.FULL -> PetMood.HAPPY
            else -> PetMood.EATING
        }
        !permissionsOk -> PetMood.SAD
        pet.xp == 0 -> PetMood.HELLO
        isActive && ActiveSessionTracker.todayMillis() > BREAK_AFTER_MS -> PetMood.BREAK
        isActive -> PetMood.WORKING
        pet.lastActivityAt > 0 && now - pet.lastActivityAt > SLEEP_AFTER_MS -> PetMood.SLEEPING
        else -> PetMood.NEUTRAL
    }
    val name = stringResource(R.string.pet_name)
    val pettedPhrases = listOf(
        R.string.pet_say_petted_1, R.string.pet_say_petted_2, R.string.pet_say_petted_3, R.string.pet_say_petted_4
    )
    val praiseGood = androidx.compose.ui.res.stringArrayResource(R.array.pet_praise_good)
    val praiseSmart = androidx.compose.ui.res.stringArrayResource(R.array.pet_praise_smart)
    val pick = (pet.reactionAt / 1000L).toInt().let { if (it < 0) -it else it }
    val says = if (petting || afterglow) stringResource(pettedPhrases[phrase % pettedPhrases.size])
    else if (mood == PetMood.HELLO && greeting != null) greeting!!
    else when (mood) {
        PetMood.HELLO -> stringResource(R.string.pet_say_hello, name)
        PetMood.WORKING -> stringResource(R.string.pet_say_working)
        PetMood.HAPPY -> if (pet.reaction == PetState.Reaction.FULL) stringResource(R.string.pet_say_full)
            else String.format(praiseGood[pick % praiseGood.size], pet.lastGain)
        PetMood.COOL -> String.format(praiseSmart[pick % praiseSmart.size], pet.lastGain)
        PetMood.EATING -> stringResource(R.string.pet_say_eating, pet.lastGain)
        PetMood.LEVEL_UP -> stringResource(R.string.pet_say_level_up, pet.level)
        PetMood.SLEEPING -> stringResource(R.string.pet_say_sleeping)
        PetMood.BREAK -> stringResource(R.string.pet_say_break)
        PetMood.SAD -> stringResource(R.string.pet_say_sad)
        PetMood.HELPER -> stringResource(R.string.pet_say_helper)
        PetMood.NEUTRAL -> stringResource(R.string.pet_say_neutral)
    }
    val stageName = stringResource(
        when (pet.stage) {
            PetState.Stage.EGG -> R.string.pet_stage_egg
            PetState.Stage.BABY -> R.string.pet_stage_baby
            PetState.Stage.TEEN -> R.string.pet_stage_teen
            PetState.Stage.PRO -> R.string.pet_stage_pro
            PetState.Stage.LEGEND -> R.string.pet_stage_legend
        }
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WaSurface)
            .border(1.dp, WaNeonGreen.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
            PetArt(
                stage = pet.stage,
                mood = mood,
                hopKey = hopKey,
                petting = petting,
                modifier = Modifier
                    .size(108.dp)
                    .clip(RoundedCornerShape(18.dp))
                    // Stroke with a finger to pet Wayo (doesn't give energy; only smart decisions do).
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                petting = true
                                afterglow = false
                                strokeDistance = 0f
                                phrase = (phrase + 1) % 4
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragEnd = { petting = false; afterglow = true },
                            onDragCancel = { petting = false; afterglow = true },
                            onDrag = { change, drag ->
                                change.consume()
                                strokeDistance += drag.getDistance()
                                // A soft tick for every ~stroke under the finger.
                                if (strokeDistance > 90.dp.toPx()) {
                                    strokeDistance = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    }
                    .clickable {
                        // Tap: Wayo hops, then opens the helper.
                        hopKey++
                        scope.launch {
                            delay(380)
                            helpOpen = true
                        }
                    }
            )
            // "+3 ⚡" floats up from the pet when it gets energy.
            if (gainFloat.value < 1f && pet.lastGain > 0) {
                Text(
                    "+${pet.lastGain} ⚡",
                    color = WaNeonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = (10f - 46f * gainFloat.value) * density
                            alpha = (1f - gainFloat.value).coerceIn(0f, 1f)
                        }
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    " · " + stringResource(R.string.pet_level, pet.level) + " · " + stageName,
                    color = WaTextSecondary, fontSize = 13.sp, maxLines = 1
                )
            }
            LinearProgressIndicator(
                progress = { pet.progress },
                color = WaNeonGreen,
                trackColor = WaSurfaceVariant,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
            Text(
                stringResource(R.string.pet_energy, pet.xp, pet.nextLevelXp),
                color = WaTextSecondary, fontSize = 11.sp
            )
            // Fixed room for three lines: the text changes while petting / on reactions, and the
            // card must not grow and shrink with it.
            val bubbleMin = with(androidx.compose.ui.platform.LocalDensity.current) { (17.sp * 3).toDp() } + 14.dp
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = bubbleMin)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WaSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(says, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text(
                "💬 " + stringResource(R.string.pet_ask, name),
                color = WaNeonGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { helpOpen = true }.padding(vertical = 2.dp)
            )
        }
    }

    if (helpOpen) {
        val show: (TourStep) -> Unit = { step ->
            helpOpen = false
            listMode = false
            onShowTopic(step)
        }
        // The chat stays open underneath, so closing the list returns to the same conversation.
        WayoChatDialog(
            name = name,
            isActive = isActive,
            isSubscribed = isSubscribed,
            onDismiss = { helpOpen = false },
            onShow = show,
            onOpenList = { listMode = true }
        )
        if (listMode) {
            // Full list of questions (the "📋" button in the chat).
            PetHelpDialog(
                name = name,
                isActive = isActive,
                isSubscribed = isSubscribed,
                onDismiss = { listMode = false },
                onShow = show
            )
        }
    }
}

/** The pet as a helper: type a question (or pick one) — it answers and can point at the setting. */
@Composable
private fun PetHelpDialog(
    name: String,
    isActive: Boolean,
    isSubscribed: Boolean,
    onDismiss: () -> Unit,
    onShow: (TourStep) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var openIndex by remember { mutableStateOf<Int?>(null) }
    var diagOpen by remember { mutableStateOf(false) }
    var diagTick by remember { mutableStateOf(0) }
    val resolved = PET_HELP_TOPICS.map { Triple(it, stringResource(it.question), stringResource(it.answer)) }
    // Ranked search: every word of the question (4+ letters, compared by its first 5 letters so
    // word endings and small typos at the end don't matter) scores 3 in the question, 1 in the answer.
    val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.map { it.take(5) }
    val shown = if (words.isEmpty()) resolved else resolved
        .map { tr -> tr to words.sumOf { w -> (if (tr.second.lowercase().contains(w)) 3 else 0) + (if (tr.third.lowercase().contains(w)) 1 else 0) } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }

    fun open(intent: android.content.Intent) {
        runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val pkgUri = android.net.Uri.parse("package:" + context.packageName)
    val diag = remember(diagOpen, diagTick, isActive, isSubscribed) {
        if (!diagOpen) emptyList() else {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val notifOk = android.os.Build.VERSION.SDK_INT < 33 ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val accessOk = AccessibilityUtils.isServiceEnabled(context)
            val overlayOk = Settings.canDrawOverlays(context)
            val planOk = isSubscribed || com.wayars.app.service.accessibility.FreeTrial.hasTimeLeft()
            val stepFor = { target: TourTarget, tab: MainTab, item: Int? -> TourStep(target, tab, item, R.string.pet_diag_title, R.string.pet_diag_problems) }
            listOf(
                DiagItem(R.string.pet_diag_accessibility, accessOk) { open(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                DiagItem(R.string.pet_diag_overlay, overlayOk) { open(android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)) },
                DiagItem(R.string.pet_diag_plan, planOk) { onShow(stepFor(TourTarget.SETTINGS_SUBSCRIPTION, MainTab.SETTINGS, SettingsItems.SUBSCRIPTION)) },
                DiagItem(R.string.pet_diag_active, isActive) { onShow(stepFor(TourTarget.ACTIVE_SWITCH, MainTab.HOME, null)) },
                DiagItem(R.string.pet_diag_battery, pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    open(android.content.Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
                DiagItem(R.string.pet_diag_notifications, notifOk) {
                    open(android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            )
        }
    }
    // Re-check when the user comes back from a system settings screen.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) diagTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(WaSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PetArt(PetState.state.collectAsState().value.stage, PetMood.HELPER, Modifier.size(56.dp))
                Column {
                    Text(stringResource(R.string.pet_ask, name), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.pet_say_helper), color = WaTextSecondary, fontSize = 13.sp)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; openIndex = null },
                placeholder = { Text(stringResource(R.string.pet_help_search), fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WaNeonGreen,
                    unfocusedBorderColor = WaTextSecondary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.pet_diag_button),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WaNeonGreen)
                    .clickable { diagOpen = !diagOpen; diagTick++ }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            if (diagOpen) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WaSurfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    diag.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (item.ok) "✅" else "❌", fontSize = 14.sp)
                            Text(
                                stringResource(item.label),
                                color = Color.White, fontSize = 13.sp,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                            if (!item.ok && item.fix != null) {
                                Text(
                                    stringResource(R.string.pet_diag_fix),
                                    color = WaNeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { item.fix.invoke() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(if (diag.all { it.ok }) R.string.pet_diag_all_ok else R.string.pet_diag_problems),
                        color = if (diag.all { it.ok }) WaNeonGreen else WaTextSecondary,
                        fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
            }
            if (shown.isEmpty()) {
                Text(stringResource(R.string.pet_help_empty), color = WaTextSecondary, fontSize = 13.sp)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown.size) { i ->
                    val (t, q, a) = shown[i]
                    val open = openIndex == i
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WaSurfaceVariant)
                            .clickable { openIndex = if (open) null else i }
                            .padding(12.dp)
                    ) {
                        Text(q, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (open) {
                            Text(a, color = WaTextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                            if (t.step != null) {
                                Button(
                                    onClick = { onShow(t.step) },
                                    colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) { Text(stringResource(R.string.pet_help_show)) }
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_calibration_close), color = WaNeonGreen) }
            }
        }
    }
}
