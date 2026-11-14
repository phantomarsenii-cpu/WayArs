package com.wayars.app.presentation.pet.brain

import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Wayo's offline "understanding": no internet, no AI model. Every message is matched against
 * what Wayo knows — each help topic's question and answer in ALL app languages (so a driver can
 * write in Russian while the app is in Polish) plus hand-written synonyms and slang
 * ([WayoKeywords]). Matching tolerates word endings, missing diacritics and typos. When unsure,
 * Wayo asks which topic was meant instead of guessing; small talk and follow-ups
 * ("show me", "didn't help") are understood too.
 */
class WayoBrain(
    docs: List<Doc>,
    /** For long passages (documents): a long text shouldn't win just because it has more words. */
    private val lengthNorm: Boolean = false,
    /** How much a word found only in the answer text counts (questions count 1.4). */
    private val answerWeight: Double = 0.45
) {

    /** One topic; [texts] are its question/answer texts in every language. */
    class Doc(val key: String, val questions: List<String>, val answers: List<String>)

    sealed class Reply {
        data class Answer(val key: String) : Reply()
        data class Clarify(val keys: List<String>) : Reply()
        data class Small(val kind: SmallTalk) : Reply()
        /** Chit-chat in character; [kind] is a key of [WayoKeywords.chit]. */
        data class Chit(val kind: String) : Reply()
        data class ShowLast(val key: String) : Reply()
        data class NotHelped(val lastKey: String?) : Reply()
        data class Unknown(val suggestions: List<String>) : Reply()
    }

    enum class SmallTalk { HELLO, THANKS, WHO, BYE, HOW_ARE_YOU }

    private class Indexed(
        val key: String,
        val q: List<String>,
        val a: List<String>,
        val kw: List<List<String>>
    )

    private val index: List<Indexed>
    private val idf: Map<String, Double>
    private val avgLen: Double

    init {
        index = docs.map { d ->
            Indexed(
                key = d.key,
                q = d.questions.flatMap { tokens(it) }.distinct(),
                a = d.answers.flatMap { tokens(it) }.distinct(),
                kw = (WayoKeywords.topics[d.key] ?: emptyList()).map { tokens(it) }.filter { it.isNotEmpty() }
            )
        }
        val df = HashMap<String, Int>()
        for (t in index) for (s in (t.q + t.a + t.kw.flatten()).map { stem(it) }.toSet()) df[s] = (df[s] ?: 0) + 1
        val n = index.size.toDouble()
        idf = df.mapValues { (_, c) -> ln(1.0 + n / c) }
        avgLen = if (index.isEmpty()) 1.0 else index.sumOf { it.q.size + it.a.size }.toDouble() / index.size
    }

    private fun idfOf(token: String) = idf[stem(token)] ?: ln(1.0 + index.size)

    /** Scores of every topic for [message], best first. Exposed for tests. */
    fun rank(message: String): List<Pair<String, Double>> {
        val msg = tokens(message)
        if (msg.isEmpty()) return emptyList()
        return index.map { t -> t.key to score(msg, t) }.sortedByDescending { it.second }
    }

    /**
     * How much of the message (by word weight) is actually found in [key]'s texts — 1.0 when every
     * meaningful word is there. Separates a real match from one carried by a couple of common words.
     */
    fun coverage(message: String, key: String): Double {
        val msg = tokens(message)
        val t = index.firstOrNull { it.key == key } ?: return 0.0
        if (msg.isEmpty()) return 0.0
        var total = 0.0
        var found = 0.0
        for (m in msg) {
            val w = idfOf(m)
            total += w
            val best = max(t.q.maxOfOrNull { similarity(m, it) } ?: 0.0, t.a.maxOfOrNull { similarity(m, it) } ?: 0.0)
            if (best >= 0.75) found += w
        }
        return if (total == 0.0) 0.0 else found / total
    }

    private fun score(msg: List<String>, t: Indexed): Double {
        var s = 0.0
        // Synonym / phrase hits: every word of the phrase must be in the message.
        for (phrase in t.kw) {
            var sum = 0.0
            var ok = true
            for (w in phrase) {
                val best = msg.maxOf { similarity(it, w) }
                if (best < 0.7) { ok = false; break }
                sum += best
            }
            if (ok) s += 3.0 * (sum / phrase.size) * (1.0 + 0.6 * (phrase.size - 1))
        }
        for (m in msg) {
            val w = idfOf(m)
            val inQ = t.q.maxOfOrNull { similarity(m, it) } ?: 0.0
            val inA = t.a.maxOfOrNull { similarity(m, it) } ?: 0.0
            s += w * max(1.4 * inQ, answerWeight * inA)
        }
        if (lengthNorm) s /= 0.75 + 0.25 * kotlin.math.sqrt((t.q.size + t.a.size).coerceAtLeast(1) / avgLen)
        return s
    }

    fun reply(message: String, lastKey: String?): Reply {
        // Only emoji (no letters or digits): a reaction, answered in kind.
        if (message.isNotBlank() && message.none { it.isLetterOrDigit() }) {
            WayoKeywords.emoji.firstOrNull { (_, list) -> list.any { message.contains(it) } }?.let { return Reply.Chit(it.first) }
        }
        val norm = normalize(message)
        val msg = tokens(message)
        val ranked = rank(message)
        val top = ranked.firstOrNull()
        val strong = top != null && top.second >= HIGH
        val notHelped = WayoKeywords.notHelped.any { containsPhrase(norm, it) }
        // Follow-ups to the previous answer ("didn't help", "show me / where?") — unless the
        // message clearly asks about something else.
        if (!strong) {
            if (notHelped && msg.size <= 7) return Reply.NotHelped(lastKey)
            if (lastKey != null && msg.size <= 3 && WayoKeywords.showMe.any { containsPhrase(norm, it) }) {
                return Reply.ShowLast(lastKey)
            }
        }
        // Small talk only when the message isn't really about a topic.
        val small = if (notHelped) null else smallTalk(norm)
        // Short chit-chat ("who are you?", "how are you?") stays chit-chat unless a topic matches very strongly.
        if (small != null && (!strong || (msg.size <= 3 && top!!.second < VERY_HIGH))) return Reply.Small(small)
        val chit = if (notHelped) null else chitTalk(norm, msg.size)
        if (chit != null && (!strong || (msg.size <= 4 && top!!.second < VERY_HIGH))) return Reply.Chit(chit)
        if (top == null || top.second < LOW) {
            return Reply.Unknown(ranked.filter { it.second > 0.6 }.take(3).map { it.first })
        }
        val second = ranked.getOrNull(1)?.second ?: 0.0
        if (top.second >= HIGH && top.second >= second * MARGIN) return Reply.Answer(top.first)
        if (top.second >= VERY_HIGH && top.second >= second * 1.08) return Reply.Answer(top.first)
        val close = ranked.filter { it.second >= top.second * 0.6 }.take(3).map { it.first }
        return if (close.size == 1) Reply.Answer(close[0]) else Reply.Clarify(close)
    }

    private fun chitTalk(norm: String, size: Int): String? {
        // "not funny" must win over "funny" (laugh), so it is checked first.
        val ordered = WayoKeywords.chit.entries.sortedBy { if (it.key == "notfunny") 0 else 1 }
        for ((kind, phrases) in ordered) {
            // "ok" / "haha" only when that's (almost) the whole message, not "ok, and how do I…".
            if (kind in SHORT_ONLY && size > 2) continue
            if (phrases.any { containsPhrase(norm, it) }) return kind
        }
        return null
    }

    private fun smallTalk(norm: String): SmallTalk? {
        fun any(list: List<String>) = list.any { containsPhrase(norm, it) }
        return when {
            any(WayoKeywords.who) -> SmallTalk.WHO
            any(WayoKeywords.howAreYou) -> SmallTalk.HOW_ARE_YOU
            any(WayoKeywords.thanks) -> SmallTalk.THANKS
            any(WayoKeywords.bye) -> SmallTalk.BYE
            any(WayoKeywords.hello) -> SmallTalk.HELLO
            else -> null
        }
    }

    companion object {
        /** Chit-chat that only counts when it's (almost) the whole message: "yes", "ok", "more"… */
        private val SHORT_ONLY = setOf("ok", "laugh", "agree", "disagree", "more", "cool", "glad")
        const val LOW = 2.2
        const val HIGH = 4.2
        const val VERY_HIGH = 8.0
        const val MARGIN = 1.25

        private val combining = Regex("\\p{Mn}+")
        private val splitter = Regex("[^\\p{L}\\p{N}]+")

        /** Lowercase, no diacritics (ą→a, é→e, й→и), ё→е, ł→l, so typing without them still matches. */
        fun normalize(s: String): String {
            val lower = s.lowercase().replace('ё', 'е').replace('ł', 'l').replace('ß', 's').replace('’', '\'')
            return combining.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        }

        fun tokens(s: String): List<String> =
            normalize(s).split(splitter).filter { it.length >= 2 && it !in WayoKeywords.stopWords }

        fun stem(t: String): String = if (t.length > 5) t.take(5) else t

        /** True if every word of [phrase] appears (fuzzily) in [norm] in the same order-free way. */
        fun containsPhrase(norm: String, phrase: String): Boolean {
            val words = normalize(phrase).split(splitter).filter { it.isNotEmpty() }
            val have = norm.split(splitter).filter { it.isNotEmpty() }
            return words.isNotEmpty() && words.all { w -> have.any { h -> h == w || (w.length >= 4 && similarity(h, w) >= 0.85) } }
        }

        /** 1.0 same word · 0.9 same root · 0.7 shared 4-letter start · 0.8 one/two typos · else 0. */
        fun similarity(a: String, b: String): Double {
            if (a == b) return 1.0
            val p = commonPrefix(a, b)
            val minLen = min(a.length, b.length)
            if (minLen >= 5 && p >= 5) return 0.9
            if (minLen >= 4 && p >= 4 && max(a.length, b.length) >= 5) return 0.75
            // A typo almost never hits the first letter: "вернуть" (return) is not "свернуть" (minimize).
            if (minLen >= 4 && a[0] == b[0]) {
                val limit = if (minLen >= 7) 2 else 1
                if (kotlin.math.abs(a.length - b.length) <= limit && distance(a, b, limit) <= limit) return 0.8
            }
            return 0.0
        }

        private fun commonPrefix(a: String, b: String): Int {
            var i = 0
            while (i < a.length && i < b.length && a[i] == b[i]) i++
            return i
        }

        /** Damerau-Levenshtein distance, early exit above [limit]. */
        private fun distance(a: String, b: String, limit: Int): Int {
            val n = a.length; val m = b.length
            var prev2 = IntArray(m + 1)
            var prev = IntArray(m + 1) { it }
            var cur = IntArray(m + 1)
            for (i in 1..n) {
                cur[0] = i
                var rowMin = cur[0]
                for (j in 1..m) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var v = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, prev2[j - 2] + 1)
                    cur[j] = v
                    rowMin = min(rowMin, v)
                }
                if (rowMin > limit) return limit + 1
                val t = prev2; prev2 = prev; prev = cur; cur = t
            }
            return prev[m]
        }
    }
}
