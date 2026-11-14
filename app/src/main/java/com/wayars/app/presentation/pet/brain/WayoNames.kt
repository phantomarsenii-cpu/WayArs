package com.wayars.app.presentation.pet.brain

/**
 * The driver's name in chat: "my name is…" in any app language, a bare name right after Wayo
 * asked for it, "what's my name?" and "forget my name". Pure Kotlin (testable off the device).
 */
object WayoNames {

    /** Phrases that introduce a name ("меня зовут Саша", "call me Alex"…). */
    private val intro = listOf(
        "меня зовут", "зови меня", "называй меня", "моё имя", "мое имя",
        "мене звати", "клич мене", "називай мене", "моє ім'я", "моє імя",
        "mam na imię", "mam na imie", "nazywam się", "nazywam sie", "mów mi", "mow mi", "moje imię to", "moje imie to",
        "my name is", "call me", "my name's",
        "ich heiße", "ich heisse", "mein name ist", "nenn mich",
        "je m'appelle", "je m’appelle", "appelle-moi", "appelle moi",
        "mă numesc", "ma numesc", "numele meu este", "spune-mi",
        "jmenuji se", "jmenuju se", "říkej mi", "rikej mi",
        "казвам се", "името ми е", "викай ми",
        "a nevem", "hívj", "hivj",
        "jeg hedder", "mit navn er", "kald mig", "jeg heter", "mitt navn er", "kall meg",
        "jag heter", "mitt namn är", "mitt namn ar", "kalla mig",
        "mənim adım", "menim adim", "adım", "adim",
        "իմ անունը", "ինձ ասա",
        "ჩემი სახელია", "მქვია",
        "mening ismim", "ismim", "meni chaqir"
    )

    /** Only after Wayo asked: "я Саша", "I'm Alex", "jestem Ola"… */
    private val introAfterAsk = listOf("я", "i'm", "i am", "im", "jestem", "это", "it's", "ich bin", "je suis", "sunt", "jsem", "аз съм", "jeg er", "jag är", "mən")

    private val forgetPhrases = listOf(
        "забудь моё имя", "забудь мое имя", "забудь как меня зовут", "забудь моє ім'я", "forget my name", "zapomnij moje imię",
        "zapomnij moje imie", "vergiss meinen namen", "oublie mon prénom", "uită-mi numele", "zapomeň moje jméno"
    )
    private val askOwnPhrases = listOf(
        "как меня зовут", "знаешь как меня зовут", "моё имя знаешь", "як мене звати", "what's my name", "what is my name",
        "do you know my name", "jak mam na imię", "jak mam na imie", "znasz moje imię", "wie heiße ich", "comment je m'appelle",
        "cum mă cheamă", "jak se jmenuju", "jak se jmenuji"
    )

    /** Words that are never a name even if they look like one. */
    private val notNames = setOf(
        "привет", "здравствуй", "спасибо", "пока", "да", "нет", "ок", "окей", "хорошо", "ладно", "никак", "неважно", "секрет",
        "hello", "hi", "hey", "thanks", "yes", "no", "ok", "okay", "nobody", "secret", "cześć", "czesc", "dzięki", "tak", "nie",
        "привіт", "дякую", "так", "ні", "hallo", "danke", "ja", "nein", "salut", "merci", "oui", "non", "wayo", "вайо"
    )

    private val letters = Regex("^[\\p{L}][\\p{L}'’-]{1,19}$")

    /** A name found in [message], and whether it came with an explicit "my name is". */
    fun extract(message: String, awaiting: Boolean): Pair<String, Boolean>? {
        val clean = message.replace(Regex("[\\p{So}\\p{Cs}]+"), " ").trim().trimEnd('.', '!', '?', ')', ' ')
        val low = clean.lowercase()
        for (p in intro) {
            val i = low.indexOf(p)
            if (i < 0) continue
            // The phrase must start a word ("адым" inside another word doesn't count).
            if (i > 0 && low[i - 1].isLetter()) continue
            val rest = clean.substring(i + p.length).trim().trimStart(',', ':', '-', '—', ' ')
            return candidate(rest)?.let { it to true }
        }
        if (!awaiting) return null
        var rest = clean
        for (p in introAfterAsk) {
            if (low.startsWith("$p ")) { rest = clean.substring(p.length).trim(); break }
        }
        return candidate(rest)?.let { it to false }
    }

    private fun candidate(text: String): String? {
        val words = text.split(Regex("[\\s,]+")).filter { it.isNotBlank() }.map { it.trim('.', '!', '?', ':', ';', '"', '«', '»') }
        if (words.isEmpty() || words.size > 2) return null
        if (words.any { !letters.matches(it) || it.lowercase() in notNames }) return null
        return words.joinToString(" ") { w -> w.lowercase().split('-').joinToString("-") { part -> part.replaceFirstChar { it.titlecase() } } }
    }

    fun isForget(message: String): Boolean = forgetPhrases.any { WayoBrain.normalize(message).contains(WayoBrain.normalize(it)) }
    fun isAskOwn(message: String): Boolean = askOwnPhrases.any { WayoBrain.normalize(message).contains(WayoBrain.normalize(it)) }

    /**
     * Puts the name into a greeting: "Доброе утро! ☀️" → "Доброе утро, Саша! ☀️". Only when the
     * first "!" is near the start (a short exclamation), otherwise the text is left as is.
     */
    fun withName(text: String, name: String): String {
        if (name.isBlank()) return text
        val i = text.indexOf('!')
        if (i <= 0 || i > 40 || text.substring(0, i).contains('?')) return text
        val before = text.substring(0, i).trimEnd()
        if (before.isEmpty() || !before.last().isLetter()) return text
        return "$before, $name" + text.substring(i)
    }
}
