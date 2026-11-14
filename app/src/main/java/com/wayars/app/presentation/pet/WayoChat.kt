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
import com.wayars.app.appContainer
import com.wayars.app.data.prefs.DriverPrefs
import com.wayars.app.domain.model.Currency
import com.wayars.app.features.AppNames
import com.wayars.app.features.Earnings
import com.wayars.app.service.accessibility.ActiveSessionTracker
import com.wayars.app.util.CurrencyFormatter
import kotlinx.coroutines.flow.first
import com.wayars.app.pet.PetState
import com.wayars.app.presentation.pet.brain.WayoBrain
import com.wayars.app.presentation.pet.brain.WayoDocs
import com.wayars.app.presentation.pet.brain.WayoNames
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
            // "How much did I earn today / this week / this month" — answered from the driver's own data.
            val personal = PERSONAL_KEYS.map { WayoBrain.Doc(it, emptyList(), emptyList()) }
            return WayoBrain(docs + personal).also { brain = it }
        }
    }

    /** Everything written in the app, as passages Wayo can quote (see [WayoDocs]). */
    private val DOC_RES = listOf(
        "settings_privacy_content", "settings_terms_content", "settings_about_content", "accessibility_consent_body",
        "accessibility_service_description", "settings_accessibility_hint", "settings_overlay_hint", "settings_notifications_hint",
        "settings_permissions_hint", "settings_subscription_hint", "active_requires_permissions", "active_requires_subscription",
        "settings_calibration_no_data", "settings_calibration_instructions", "settings_calibration_auto_ok",
        "settings_calibration_auto_missing", "settings_calibration_check_fail", "settings_calibration_no_numbers",
        "settings_contacts_subtitle", "terms_gate_subtitle", "paywall_subtitle", "paywall_section_subtitle", "paywall_trial_notice",
        "paywall_footer_google_subtitle", "paywall_compat_label", "paywall_no_charge_notice", "update_available_text",
        "vehicle_prompt_body", "settings_tax_desc", "settings_partner_desc", "settings_summaries_desc", "settings_watch_desc",
        "settings_widget_desc", "settings_invite_desc", "settings_theme_desc", "stats_real_hour_hint", "stats_services_empty",
        "tour_welcome_text", "tour_active_text", "tour_summary_text", "tour_verdict_text", "tour_stats_text", "tour_presets_text",
        "tour_thresholds_text", "tour_vehicle_text", "tour_permissions_text", "tour_apps_text", "tour_currency_text",
        "tour_tax_text", "tour_partner_text", "tour_pet_text", "tour_extras_text", "tour_petset_text", "tour_final_text"
    )
    /** Documents opened from Settings → bottom of the list ("show me" goes there). */
    private val INFO_DOCS = setOf("settings_privacy_content", "settings_terms_content", "settings_about_content")
    private val DOC_LANGS = listOf("en", "ru", "pl", "uk")
    @Volatile private var docs: Pair<WayoBrain, List<WayoDocs.Chunk>>? = null

    @android.annotation.SuppressLint("DiscouragedApi")
    private fun docs(context: Context): Pair<WayoBrain, List<WayoDocs.Chunk>> {
        docs?.let { return it }
        synchronized(this) {
            docs?.let { return it }
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
            // The app's own language first, then the languages most drivers write in.
            val langs = (listOf(context.resources.configuration.locales[0].language) + DOC_LANGS).distinct()
            val chunks = ArrayList<WayoDocs.Chunk>()
            for (lang in langs) {
                val cfg = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
                val r = context.createConfigurationContext(cfg).resources
                for (name in DOC_RES) {
                    val id = r.getIdentifier(name, "string", context.packageName).takeIf { it != 0 } ?: continue
                    val text = runCatching { if (name == "settings_about_content") r.getString(id, version) else r.getString(id) }.getOrNull() ?: continue
                    val titleName = Regex("_(content|body|text|desc|hint|subtitle|description)$").replace(name, "_title")
                    val label = r.getIdentifier(titleName, "string", context.packageName).takeIf { it != 0 && titleName != name }
                        ?.let { runCatching { r.getString(it) }.getOrNull() }
                    chunks += WayoDocs.split(label, text, if (name in INFO_DOCS) 1 else 0)
                }
            }
            val uniq = chunks.distinctBy { it.text }
            val b = WayoBrain(uniq.mapIndexed { i, c -> c.asDoc("doc:$i") }, lengthNorm = true, answerWeight = 1.0)
            return (b to uniq).also { docs = it }
        }
    }

    /** The passage that answers [message] best, or null when nothing in the documents really fits. */
    fun searchDocs(context: Context, message: String): Pair<WayoDocs.Chunk, Double>? {
        val (b, chunks) = docs(context)
        val ranked = b.rank(message)
        val top = ranked.firstOrNull() ?: return null
        val cov = b.coverage(message, top.first)
        if (top.second < DOC_MIN_SCORE || cov < DOC_MIN_COVERAGE) return null
        // Several passages equally good (e.g. "where's the privacy policy?") — that's not a specific question.
        val second = ranked.getOrNull(1)?.second ?: 0.0
        if (top.second < second * 1.04) return null
        return chunks[top.first.removePrefix("doc:").toInt()] to cov
    }

    private const val DOC_MIN_SCORE = 7.5
    private const val DOC_MIN_COVERAGE = 0.52
}

private class ChatMsg(
    val fromUser: Boolean,
    val text: String,
    val step: TourStep? = null,
    val chips: List<PetHelpTopic> = emptyList(),
    val phoneCheck: Boolean = false,
    val telegram: Boolean = false,
    /** Chips are "people also ask" suggestions (shown with a small heading). */
    val related: Boolean = false
)

/** Topics whose problems are worth an automatic phone check under the answer. */
private val PROBLEM_KEYS = setOf("diagnostics", "overlay", "turns_off", "access", "active", "background")
private val POPULAR_KEYS = listOf("overlay", "diagnostics", "wrong_numbers", "hourly", "invite")
internal val PERSONAL_KEYS = listOf("my_today", "my_week", "my_month")
/** Questions about something that went wrong: Wayo answers with empathy, not "Easy!". */
private val FIX_KEYS = setOf("overlay", "diagnostics", "turns_off", "access", "wrong_numbers", "stats_count", "restore", "no_time", "move_card", "battery")
/** What drivers usually ask next — offered under an answer so the chat flows like a conversation. */
private val RELATED: Map<String, List<String>> = mapOf(
    "overlay" to listOf("diagnostics", "battery"), "diagnostics" to listOf("overlay", "turns_off"),
    "wrong_numbers" to listOf("calibrate", "reset_calibration"), "calibrate" to listOf("wrong_numbers", "add_app"),
    "add_app" to listOf("calibrate", "apps"), "apps" to listOf("add_app", "several"), "several" to listOf("apps", "compare"),
    "language" to listOf("currency", "country"), "currency" to listOf("country", "presets"), "country" to listOf("currency", "language"),
    "presets" to listOf("thresholds", "verdict"), "thresholds" to listOf("presets", "verdict"), "verdict" to listOf("net", "buttons"),
    "net" to listOf("vehicle", "tax"), "vehicle" to listOf("net", "tax"), "tax" to listOf("net", "partner"),
    "partner" to listOf("weekly_fee", "hourly"), "weekly_fee" to listOf("partner", "hourly"),
    "stats" to listOf("hourly", "compare"), "hourly" to listOf("compare", "stats_count"), "compare" to listOf("hourly", "achievements"),
    "stats_count" to listOf("buttons", "stats"), "buttons" to listOf("stats_count", "verdict"),
    "subscription" to listOf("trial", "cancel"), "trial" to listOf("subscription", "invite"), "restore" to listOf("subscription", "data"),
    "cancel" to listOf("subscription", "restore"), "invite" to listOf("subscription", "trial"),
    "pet" to listOf("stages", "rewards"), "stages" to listOf("rewards", "pet"), "rewards" to listOf("theme", "accessories"),
    "theme" to listOf("accessories", "rewards"), "accessories" to listOf("theme", "stages"), "petting" to listOf("pet", "share"),
    "share" to listOf("achievements", "stages"), "achievements" to listOf("share", "hourly"),
    "privacy" to listOf("data", "internet"), "data" to listOf("privacy", "restore"), "internet" to listOf("privacy", "iphone"),
    "iphone" to listOf("update", "internet"), "update" to listOf("about", "iphone"), "what_is" to listOf("verdict", "apps"),
    "ban" to listOf("clicks", "privacy"), "clicks" to listOf("ban", "buttons"),
    "summaries" to listOf("widget", "watch"), "widget" to listOf("summaries", "watch"), "watch" to listOf("widget", "summaries"),
    "about" to listOf("contact", "update"), "contact" to listOf("about", "diagnostics"),
    "turns_off" to listOf("battery", "access"), "battery" to listOf("turns_off", "background"), "background" to listOf("battery", "several"),
    "access" to listOf("turns_off", "overlay"), "pickup" to listOf("net", "no_time"), "no_time" to listOf("pickup", "verdict"),
    "move_card" to listOf("buttons", "verdict"), "tutorial" to listOf("what_is", "contact"), "documents" to listOf("privacy", "about")
)

/**
 * The driver's own numbers for a topic (earnings, per hour, best app, records, invite code,
 * Wayo's level, trial / subscription), or null when the topic has none. Read on the phone only.
 */
private suspend fun personalLine(context: Context, key: String, isSubscribed: Boolean): String? = withContext(Dispatchers.IO) {
    runCatching {
        val c = context.appContainer()
        val cur = runCatching { c.settingsRepository.currency.first() }.getOrDefault(Currency.default)
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        fun money(v: Double) = CurrencyFormatter.format(v, cur)
        fun perHour(v: Double) = context.getString(R.string.summary_part_hour, CurrencyFormatter.format(v, cur, 0))
        fun duration(ms: Long): String { val m = ms / 60_000L; return context.getString(R.string.stats_duration, (m / 60).toInt(), (m % 60).toInt()) }
        when (key) {
            "my_today", "my_week", "my_month", "hourly", "stats" -> {
                val today = Earnings.startOfDay(now)
                val from = when (key) { "my_week" -> today - 6 * day; "my_month" -> today - 29 * day; else -> today }
                val periodName = context.getString(
                    when (key) { "my_week" -> R.string.chat_period_week; "my_month" -> R.string.chat_period_month; else -> R.string.chat_period_today }
                )
                val orders = c.orderRepository.acceptedSince(from)
                if (orders.isEmpty()) return@runCatching context.getString(R.string.chat_my_none, periodName)
                val e = Earnings.period(
                    orders, ActiveSessionTracker.intervals(from, now), from, now + 1,
                    DriverPrefs.weeklyFee.value, DriverPrefs.commissionPercent.value
                )
                val lines = mutableListOf(context.getString(R.string.chat_my_period, periodName, e.orders, money(e.net), money(e.gross)))
                e.perHour?.let { lines.add(context.getString(R.string.chat_my_hour, perHour(it), duration(e.activeMillis))) }
                if (e.partnerCommission + e.partnerFee > 0.0) {
                    lines.add(context.getString(R.string.chat_my_partner, money(e.partnerCommission + e.partnerFee), money(e.afterPartner)))
                }
                lines.joinToString("\n")
            }
            "compare" -> {
                val from = now - 60 * day
                val slots = Earnings.slotRates(c.orderRepository.acceptedSince(from), ActiveSessionTracker.intervals(from, now))
                val rates = Earnings.serviceRates(slots)
                if (rates.isEmpty()) context.getString(R.string.chat_my_compare_none)
                else context.getString(
                    R.string.chat_my_compare,
                    rates.joinToString(" · ") { "${AppNames.label(context, it.sourcePackage)} ${perHour(it.perHour)}" }
                ) + (Earnings.bestSlots(slots, 1).firstOrNull()?.let { b ->
                    "\n" + context.getString(R.string.chat_my_best_time, AppNames.label(context, b.sourcePackage), perHour(b.perHour))
                } ?: "")
            }
            "achievements" -> {
                val a = Earnings.achievements(c.orderRepository.acceptedSince(0L), ActiveSessionTracker.intervals(0L, now))
                if (a.totalOrders == 0) null
                else context.getString(
                    R.string.chat_my_record, a.currentSmartStreak, a.bestSmartStreak,
                    a.recordPerHour?.let { perHour(it) } ?: "—", a.bestDay?.let { money(it) } ?: "—"
                )
            }
            "invite" -> context.getString(R.string.chat_my_code, DriverPrefs.inviteCode(context))
            "pet" -> PetState.state.value.let { context.getString(R.string.chat_my_pet, it.level, it.xp, it.nextLevelXp) }
            "subscription", "trial", "restore" -> when {
                isSubscribed -> context.getString(R.string.chat_my_subscribed)
                FreeTrial.hasTimeLeft() -> context.getString(R.string.chat_my_trial, FreeTrial.minutesLeft())
                else -> context.getString(R.string.chat_my_trial_over)
            }
            else -> null
        }
    }.getOrNull()
}

/**
 * Chat with Wayo: the driver writes in their own words, Wayo answers from what it knows about the
 * app (offline), can point at the right setting, checks the phone right in the chat and asks
 * back when it isn't sure. "📋" switches to the full list of questions.
 */
@android.annotation.SuppressLint("DiscouragedApi")
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

    // Wayo asks the driver's name a few times at most, then lets it be.
    var awaitingName by remember { mutableStateOf(false) }
    val messages = remember {
        val name = DriverPrefs.driverName.value
        val list = mutableStateListOf(
            ChatMsg(false, WayoNames.withName(context.getString(R.string.chat_welcome), name), chips = POPULAR_KEYS.mapNotNull { topicsByKey[it] })
        )
        if (name.isBlank() && DriverPrefs.nameAskedCount(context) < 3) {
            DriverPrefs.markNameAsked(context)
            list.add(ChatMsg(false, context.getString(R.string.chat_ask_name)))
            awaitingName = true
        }
        list
    }
    var input by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var lastKey by remember { mutableStateOf<String?>(null) }
    // The last bit of chit-chat ("joke"…), so "😂" or "more" right after a joke gets the right answer.
    var lastChit by remember { mutableStateOf<String?>(null) }
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

    // A different phrasing every time (never the same one twice in a row), so Wayo doesn't sound canned.
    val lastPick = remember { HashMap<Int, Int>() }
    fun pick(arr: Int): String {
        val items = context.resources.getStringArray(arr)
        if (items.size < 2) return items.firstOrNull() ?: ""
        var i = kotlin.random.Random.nextInt(items.size)
        if (i == lastPick[arr]) i = (i + 1) % items.size
        lastPick[arr] = i
        return items[i]
    }

    suspend fun answer(t: PetHelpTopic?, key: String) {
        // The driver's own numbers first (that's usually what they asked), then the explanation.
        personalLine(context, key, isSubscribed)?.let { messages.add(ChatMsg(false, "📊 $it")) }
        lastChit = null
        if (t == null) { lastKey = "stats"; return }
        val again = key == lastKey
        lastKey = key
        val needsCheck = key in PROBLEM_KEYS
        val intro = pick(
            when {
                again -> R.array.chat_v_again
                key in FIX_KEYS || needsCheck -> R.array.chat_v_intro_problem
                else -> R.array.chat_v_intro
            }
        ).let { if (!again && kotlin.random.Random.nextInt(3) == 0) WayoNames.withName(it, DriverPrefs.driverName.value) else it }
        val outro = when {
            t.step != null -> pick(R.array.chat_v_outro_show)
            kotlin.random.Random.nextInt(3) == 0 -> pick(R.array.chat_v_outro)
            else -> null
        }
        val text = intro + " " + context.getString(t.answer) + (outro?.let { "\n\n" + it } ?: "")
        val next = if (again) emptyList() else topics(RELATED[key].orEmpty())
        messages.add(ChatMsg(false, text, step = t.step, phoneCheck = needsCheck, chips = next, related = next.isNotEmpty()))
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
            // The driver's name: "forget it", "what's my name?", "my name is…" or a bare name right after Wayo asked.
            val wasAwaiting = awaitingName
            awaitingName = false
            if (WayoNames.isForget(clean)) {
                DriverPrefs.setDriverName(context, "")
                messages.add(ChatMsg(false, context.getString(R.string.chat_name_forgot)))
                return@launch
            }
            if (WayoNames.isAskOwn(clean)) {
                val known = DriverPrefs.driverName.value
                if (known.isNotBlank()) messages.add(ChatMsg(false, context.getString(R.string.chat_name_known, known)))
                else { messages.add(ChatMsg(false, context.getString(R.string.chat_name_unknown))); awaitingName = true }
                return@launch
            }
            WayoNames.extract(clean, wasAwaiting)?.let { (found, explicit) ->
                // A bare word only counts as a name if it isn't a question Wayo understands.
                if (explicit || b.reply(clean, null) is WayoBrain.Reply.Unknown) {
                    DriverPrefs.setDriverName(context, found)
                    messages.add(ChatMsg(false, String.format(pick(R.array.chat_v_name_saved), found)))
                    return@launch
                }
            }
            val r = b.reply(clean, lastKey)
            // Anything written in the app (privacy policy, terms, settings texts…): quote the exact
            // passage when no ready answer fits, or when the driver asks what a document says.
            val askDocs = WayoDocs.mentionsDocs(clean)
            val hit = when (r) {
                is WayoBrain.Reply.Unknown, is WayoBrain.Reply.Clarify -> true
                is WayoBrain.Reply.Answer -> askDocs
                else -> false
            }.let { if (it) withContext(Dispatchers.Default) { WayoBrainHolder.searchDocs(context.applicationContext, clean) } else null }
                ?.takeIf { (_, cov) ->
                    // When Wayo would only ask "which of these?", a well-matching passage is the better answer.
                    r !is WayoBrain.Reply.Clarify || askDocs || cov >= 0.7 ||
                        (b.rank(clean).firstOrNull()?.second ?: 0.0) < WayoBrain.HIGH
                }
            if (hit != null) {
                val c = hit.first
                val head = listOfNotNull(c.source, c.section).joinToString(" → ").ifEmpty { "WayArs" }
                lastKey = "documents"
                messages.add(
                    ChatMsg(
                        false, pick(R.array.chat_v_doc) + "\n\n📄 " + head + "\n" + c.text,
                        step = if (c.step == 1) topicsByKey["documents"]?.step else null
                    )
                )
                return@launch
            }
            when (r) {
                is WayoBrain.Reply.Answer -> answer(topicsByKey[r.key], r.key)
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
                            WayoBrain.SmallTalk.HELLO -> WayoNames.withName(pick(R.array.chat_v_hello), DriverPrefs.driverName.value)
                            WayoBrain.SmallTalk.THANKS -> WayoNames.withName(pick(R.array.chat_v_thanks), DriverPrefs.driverName.value)
                            WayoBrain.SmallTalk.WHO -> context.getString(R.string.chat_who, name)
                            WayoBrain.SmallTalk.BYE -> context.getString(R.string.chat_bye)
                            WayoBrain.SmallTalk.HOW_ARE_YOU -> pick(R.array.chat_v_how)
                        }
                    )
                )
                is WayoBrain.Reply.Chit -> {
                    val afterJoke = lastChit == "joke"
                    val kind = when {
                        afterJoke && r.kind == "more" -> "joke"               // "another one!" → another joke
                        afterJoke && (r.kind == "laugh" || r.kind == "like") -> "after_joke"
                        else -> r.kind
                    }
                    lastChit = if (kind == "after_joke") "joke" else kind
                    val arr = context.resources.getIdentifier("chat_v_$kind", "array", context.packageName)
                    messages.add(ChatMsg(false, if (arr != 0) pick(arr) else pick(R.array.chat_v_hello)))
                    // "What's your name?" — and yours?
                    if (r.kind == "name" && DriverPrefs.driverName.value.isBlank()) {
                        messages.add(ChatMsg(false, context.getString(R.string.chat_ask_name)))
                        awaitingName = true
                    }
                }
                // A short message Wayo didn't get ("я рад", "ну и?") — answer like a friend would,
                // not with a support form. Longer ones get the popular questions and Telegram.
                is WayoBrain.Reply.Unknown -> messages.add(
                    if (clean.split(Regex("\\s+")).size <= 3) ChatMsg(false, pick(R.array.chat_v_shrug))
                    else ChatMsg(false, pick(R.array.chat_v_unknown), chips = topics(POPULAR_KEYS.take(4)), telegram = true)
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
                            if (m.related) Text(
                                stringResource(R.string.chat_related), color = WaTextSecondary, fontSize = 11.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
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
