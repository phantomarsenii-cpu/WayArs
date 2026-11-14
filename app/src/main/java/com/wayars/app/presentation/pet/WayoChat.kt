package com.wayars.app.presentation.pet

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wayars.app.R
import com.wayars.app.pet.PetState
import com.wayars.app.presentation.pet.brain.WayoBrain
import com.wayars.app.presentation.ui.component.MainTab
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.presentation.ui.tour.SettingsItems
import com.wayars.app.presentation.ui.tour.TourStep
import com.wayars.app.presentation.ui.tour.TourTarget
import com.wayars.app.service.accessibility.FreeTrial
import com.wayars.app.util.AccessibilityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Builds Wayo's knowledge once: every help topic in all 17 app languages. */
object WayoBrainHolder {
    private val LANGS = listOf("en", "ru", "pl", "uk", "de", "fr", "ro", "cs", "bg", "hu", "da", "nb", "sv", "az", "hy", "ka", "uz")
    @Volatile private var brain: WayoBrain? = null

    fun keyOf(context: Context, topic: PetHelpTopic): String =
        context.resources.getResourceEntryName(topic.question).removePrefix("pet_q_")

    fun get(context: Context): WayoBrain {
        brain?.let { return it }
        synchronized(this) {
            brain?.let { return it }
            val res = LANGS.map { lang ->
                val cfg = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
                context.createConfigurationContext(cfg).resources
            }
            val docs = PET_HELP_TOPICS.map { t ->
                WayoBrain.Doc(
                    key = keyOf(context, t),
                    questions = res.map { it.getString(t.question) }.distinct(),
                    answers = res.map { it.getString(t.answer) }.distinct()
                )
            }
            return WayoBrain(docs).also { brain = it }
        }
    }
}

private class ChatMsg(
    val fromUser: Boolean,
    val text: String,
    val step: TourStep? = null,
    val chips: List<PetHelpTopic> = emptyList(),
    val phoneCheck: Boolean = false,
    val telegram: Boolean = false
)

/** Topics whose problems are worth an automatic phone check under the answer. */
private val PROBLEM_KEYS = setOf("diagnostics", "overlay", "turns_off", "access", "active", "background")
private val POPULAR_KEYS = listOf("overlay", "diagnostics", "wrong_numbers", "hourly", "invite")

/**
 * Chat with Wayo: the driver writes in their own words, Wayo answers from what it knows about the
 * app (offline), can point at the right setting, checks the phone right in the chat and asks
 * back when it isn't sure. "📋" switches to the full list of questions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WayoChatDialog(
    name: String,
    isActive: Boolean,
    isSubscribed: Boolean,
    onDismiss: () -> Unit,
    onShow: (TourStep) -> Unit,
    onOpenList: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pet by PetState.state.collectAsState()
    var brain by remember { mutableStateOf<WayoBrain?>(null) }
    LaunchedEffect(Unit) { brain = withContext(Dispatchers.Default) { WayoBrainHolder.get(context.applicationContext) } }
    val topicsByKey = remember { PET_HELP_TOPICS.associateBy { WayoBrainHolder.keyOf(context, it) } }
    fun topics(keys: List<String>) = keys.mapNotNull { topicsByKey[it] }

    val messages = remember {
        mutableStateListOf(ChatMsg(false, context.getString(R.string.chat_welcome), chips = POPULAR_KEYS.mapNotNull { topicsByKey[it] }))
    }
    var input by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var lastKey by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, typing) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1 + if (typing) 1 else 0) }

    // Live phone check (re-run when coming back from a system settings screen).
    var tick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val checks = remember(tick, isActive, isSubscribed) { phoneChecks(context, isActive, isSubscribed, onShow) }

    fun answer(t: PetHelpTopic, key: String) {
        lastKey = key
        val needsCheck = key in PROBLEM_KEYS
        messages.add(ChatMsg(false, context.getString(t.answer), step = t.step, phoneCheck = needsCheck))
    }

    fun respond(text: String, asTopic: PetHelpTopic? = null) {
        val clean = text.trim()
        if (clean.isEmpty() || typing) return
        messages.add(ChatMsg(true, clean))
        typing = true
        scope.launch {
            delay(450L + (clean.length * 6L).coerceAtMost(500L))
            typing = false
            if (asTopic != null) { answer(asTopic, WayoBrainHolder.keyOf(context, asTopic)); return@launch }
            val b = brain ?: withContext(Dispatchers.Default) { WayoBrainHolder.get(context.applicationContext) }.also { brain = it }
            when (val r = b.reply(clean, lastKey)) {
                is WayoBrain.Reply.Answer -> topicsByKey[r.key]?.let { answer(it, r.key) }
                is WayoBrain.Reply.Clarify -> messages.add(ChatMsg(false, context.getString(R.string.chat_clarify), chips = topics(r.keys)))
                is WayoBrain.Reply.ShowLast -> {
                    val step = topicsByKey[r.key]?.step
                    if (step != null) onShow(step)
                    else messages.add(ChatMsg(false, context.getString(R.string.chat_no_place)))
                }
                is WayoBrain.Reply.NotHelped -> {
                    val allOk = checks.all { it.ok }
                    messages.add(ChatMsg(false, context.getString(R.string.chat_not_helped), phoneCheck = true))
                    if (allOk) messages.add(ChatMsg(false, context.getString(R.string.chat_not_helped_ok), telegram = true))
                }
                is WayoBrain.Reply.Small -> messages.add(
                    ChatMsg(
                        false,
                        when (r.kind) {
                            WayoBrain.SmallTalk.HELLO -> context.getString(R.string.chat_hello)
                            WayoBrain.SmallTalk.THANKS -> context.getString(R.string.chat_thanks)
                            WayoBrain.SmallTalk.WHO -> context.getString(R.string.chat_who, name)
                            WayoBrain.SmallTalk.BYE -> context.getString(R.string.chat_bye)
                            WayoBrain.SmallTalk.HOW_ARE_YOU -> context.getString(R.string.chat_how)
                        }
                    )
                )
                is WayoBrain.Reply.Unknown -> messages.add(
                    ChatMsg(false, context.getString(R.string.chat_unknown), chips = topics(POPULAR_KEYS.take(4)), telegram = true)
                )
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 24.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(WaSurface)
                .imePadding()
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(WaSurfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                PetArt(pet.stage, if (typing) PetMood.HELPER else PetMood.HAPPY, Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        stringResource(if (typing) R.string.chat_typing else R.string.chat_offline),
                        color = if (typing) WaNeonGreen else WaTextSecondary, fontSize = 12.sp
                    )
                }
                Text(
                    "📋", fontSize = 20.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onOpenList() }.padding(8.dp)
                )
                Text(
                    "✕", fontSize = 18.sp, color = WaTextSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onDismiss() }.padding(8.dp)
                )
            }
            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(min = 240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                itemsIndexed(messages) { _, m ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
                        Text(
                            m.text,
                            color = if (m.fromUser) Color.Black else Color.White,
                            fontSize = 14.sp, lineHeight = 19.sp,
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (m.fromUser) WaNeonGreen else WaSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                        if (m.phoneCheck) PhoneCheckBlock(checks)
                        val step = m.step
                        if (step != null) ChatButton(stringResource(R.string.pet_help_show)) { onShow(step) }
                        if (m.telegram) ChatButton("Telegram · @wayars_app") {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/wayars_app")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        if (m.chips.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                m.chips.forEach { t ->
                                    val q = stringResource(t.question)
                                    Text(
                                        q, color = WaNeonGreen, fontSize = 12.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(WaNeonGreen.copy(alpha = 0.12f))
                                            .clickable { respond(q, asTopic = t) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (typing) {
                    item {
                        Text(
                            "• • •", color = WaTextSecondary, fontSize = 16.sp,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(WaSurfaceVariant).padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            // Input
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.chat_placeholder), fontSize = 13.sp) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { respond(input); input = "" }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaNeonGreen,
                        unfocusedBorderColor = WaTextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(if (input.isBlank()) WaSurfaceVariant else WaNeonGreen)
                        .clickable(enabled = input.isNotBlank()) { respond(input); input = "" }
                ) { Text("➤", color = if (input.isBlank()) WaTextSecondary else Color.Black, fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun ChatButton(label: String, onClick: () -> Unit) {
    Text(
        label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(WaNeonGreen)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun PhoneCheckBlock(checks: List<DiagItem>) {
    Column(
        Modifier
            .padding(top = 6.dp)
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurfaceVariant.copy(alpha = 0.6f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.chat_check_title), color = WaTextSecondary, fontSize = 12.sp)
        checks.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (item.ok) "✅" else "❌", fontSize = 13.sp)
                Text(stringResource(item.label), color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                if (!item.ok && item.fix != null) {
                    Text(
                        stringResource(R.string.pet_diag_fix), color = WaNeonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { item.fix.invoke() }.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Text(
            stringResource(if (checks.all { it.ok }) R.string.pet_diag_all_ok else R.string.pet_diag_problems),
            color = if (checks.all { it.ok }) WaNeonGreen else WaTextSecondary, fontSize = 12.sp, lineHeight = 16.sp
        )
    }
}

/** The same checks as "Check my phone": permissions, plan, Active, battery, notifications. */
internal fun phoneChecks(context: Context, isActive: Boolean, isSubscribed: Boolean, onShow: (TourStep) -> Unit): List<DiagItem> {
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val notifOk = android.os.Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    val step = { target: TourTarget, tab: MainTab, item: Int? -> TourStep(target, tab, item, R.string.pet_diag_title, R.string.pet_diag_problems) }
    val pkg = Uri.parse("package:" + context.packageName)
    return listOf(
        DiagItem(R.string.pet_diag_accessibility, AccessibilityUtils.isServiceEnabled(context)) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        DiagItem(R.string.pet_diag_overlay, Settings.canDrawOverlays(context)) { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)) },
        DiagItem(R.string.pet_diag_plan, isSubscribed || FreeTrial.hasTimeLeft()) { onShow(step(TourTarget.SETTINGS_SUBSCRIPTION, MainTab.SETTINGS, SettingsItems.SUBSCRIPTION)) },
        DiagItem(R.string.pet_diag_active, isActive) { onShow(step(TourTarget.ACTIVE_SWITCH, MainTab.HOME, null)) },
        DiagItem(R.string.pet_diag_battery, pm.isIgnoringBatteryOptimizations(context.packageName)) { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        DiagItem(R.string.pet_diag_notifications, notifOk) {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    )
}
