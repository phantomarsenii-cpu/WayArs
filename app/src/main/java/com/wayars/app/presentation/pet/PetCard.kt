package com.wayars.app.presentation.pet

import android.provider.Settings
import androidx.annotation.StringRes
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wayars.app.R
import com.wayars.app.pet.PetState
import com.wayars.app.presentation.ui.component.MainTab
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.presentation.ui.tour.TourStep
import com.wayars.app.presentation.ui.tour.TourTarget
import com.wayars.app.service.accessibility.ActiveSessionTracker
import com.wayars.app.util.AccessibilityUtils
import kotlinx.coroutines.delay

/** One question the pet can answer; [step] (if any) is what "Show me" spotlights. */
class PetHelpTopic(@StringRes val question: Int, @StringRes val answer: Int, val step: TourStep?)

private fun topic(q: Int, a: Int, target: TourTarget?, tab: MainTab = MainTab.SETTINGS, item: Int? = null) =
    PetHelpTopic(q, a, target?.let { TourStep(it, tab, item, q, a) })

// Settings list items: 0 title, 1 subscription, 2 currency, 3 thresholds, 4 vehicle, 5 tax,
// 6 permissions, 7 supported apps, 8 info.
val PET_HELP_TOPICS: List<PetHelpTopic> = listOf(
    topic(R.string.pet_q_active, R.string.pet_a_active, TourTarget.ACTIVE_SWITCH, MainTab.HOME),
    topic(R.string.pet_q_overlay, R.string.pet_a_overlay, TourTarget.SETTINGS_PERMISSIONS, item = 6),
    topic(R.string.pet_q_access, R.string.pet_a_access, TourTarget.SETTINGS_PERMISSIONS, item = 6),
    topic(R.string.pet_q_add_app, R.string.pet_a_add_app, TourTarget.SETTINGS_APPS, item = 7),
    topic(R.string.pet_q_calibrate, R.string.pet_a_calibrate, TourTarget.SETTINGS_APPS, item = 7),
    topic(R.string.pet_q_currency, R.string.pet_a_currency, TourTarget.SETTINGS_CURRENCY, item = 2),
    topic(R.string.pet_q_tax, R.string.pet_a_tax, TourTarget.SETTINGS_TAX, item = 5),
    topic(R.string.pet_q_vehicle, R.string.pet_a_vehicle, TourTarget.SETTINGS_VEHICLE, item = 4),
    topic(R.string.pet_q_thresholds, R.string.pet_a_thresholds, TourTarget.SETTINGS_THRESHOLDS, item = 3),
    topic(R.string.pet_q_presets, R.string.pet_a_presets, TourTarget.NAV_PRESETS, MainTab.PRESETS),
    topic(R.string.pet_q_stats, R.string.pet_a_stats, TourTarget.NAV_STATS, MainTab.STATS),
    topic(R.string.pet_q_verdict, R.string.pet_a_verdict, TourTarget.VERDICT_CARD, MainTab.HOME),
    topic(R.string.pet_q_subscription, R.string.pet_a_subscription, TourTarget.SETTINGS_SUBSCRIPTION, item = 1),
    topic(R.string.pet_q_contact, R.string.pet_a_contact, TourTarget.SETTINGS_INFO, item = 8),
    topic(R.string.pet_q_pet, R.string.pet_a_pet, null)
)

private const val REACTION_MS = 6_000L
private const val SLEEP_AFTER_MS = 24L * 60 * 60 * 1000
private const val BREAK_AFTER_MS = 5L * 60 * 60 * 1000

/**
 * The pet on the Home screen: picture, level, energy bar and what it "says" right now.
 * Tapping the pet or "Ask" opens the helper.
 */
@Composable
fun PetCard(isActive: Boolean, onShowTopic: (TourStep) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pet by PetState.state.collectAsState()
    var helpOpen by remember { mutableStateOf(false) }

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

    val reactionFresh = pet.reaction != PetState.Reaction.NONE && now - pet.reactionAt < REACTION_MS
    val mood = when {
        helpOpen -> PetMood.HELPER
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
    val says = when (mood) {
        PetMood.HELLO -> stringResource(R.string.pet_say_hello, name)
        PetMood.WORKING -> stringResource(R.string.pet_say_working)
        PetMood.HAPPY -> if (pet.reaction == PetState.Reaction.FULL) stringResource(R.string.pet_say_full)
            else stringResource(R.string.pet_say_happy, pet.lastGain)
        PetMood.COOL -> stringResource(R.string.pet_say_cool, pet.lastGain)
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
        PetArt(
            stage = pet.stage,
            mood = mood,
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { helpOpen = true }
        )
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
            Text(
                says,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(WaSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
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
        PetHelpDialog(
            name = name,
            onDismiss = { helpOpen = false },
            onShow = { step ->
                helpOpen = false
                onShowTopic(step)
            }
        )
    }
}

/** The pet as a helper: type a question (or pick one) — it answers and can point at the setting. */
@Composable
private fun PetHelpDialog(name: String, onDismiss: () -> Unit, onShow: (TourStep) -> Unit) {
    var query by remember { mutableStateOf("") }
    var openIndex by remember { mutableStateOf<Int?>(null) }
    val resolved = PET_HELP_TOPICS.map { Triple(it, stringResource(it.question), stringResource(it.answer)) }
    val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }
    val shown = if (words.isEmpty()) resolved else resolved.filter { (_, q, a) ->
        val hay = (q + " " + a).lowercase()
        words.any { w -> hay.contains(w.take(5)) }
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
