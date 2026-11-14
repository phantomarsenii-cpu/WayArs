package com.wayars.app.presentation.pet.brain

/**
 * Everything written in the app (privacy policy, terms, "about", the accessibility consent,
 * tutorial steps, descriptions in Settings) cut into small passages Wayo can quote: one bullet
 * or paragraph each, remembering the section it belongs to. Pure Kotlin, so it can be tested
 * off the device.
 */
object WayoDocs {

    /** One quotable passage: [source] is the document's title, [section] its heading (if any). */
    class Chunk(val source: String?, val section: String?, val text: String, val step: Int) {
        /** The passage's own name: "Order history" in "Order history: for the statistics…". */
        val title: String? = text.indexOf(':').takeIf { it in 3..60 }?.let { text.substring(0, it) }
            ?.takeIf { t -> t.split(' ').size <= 6 }

        /** What the passage is about (matched strongly), and its full text (matched lightly). */
        fun asDoc(key: String) = WayoBrain.Doc(key, listOfNotNull(title, section, source), listOf(text))
    }

    private val heading = Regex("^\\*\\*(.+?)\\*\\*:?$")

    /** Splits [raw] into passages; short texts stay whole. [step] is passed through for "show me". */
    fun split(source: String?, raw: String, step: Int = 0): List<Chunk> {
        val text = raw.trim().removePrefix("\"").removeSuffix("\"")
        if (text.length < 300) return listOf(Chunk(source, null, clean(text), step))
        val out = ArrayList<Chunk>()
        var section: String? = null
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotBlank()) out += Chunk(source, section, buf.toString().trim(), step)
            buf.clear()
        }
        for (line in text.lines()) {
            val l = line.trim()
            if (l.isEmpty()) { flush(); continue }
            val h = heading.find(l)
            if (h != null) { flush(); section = h.groupValues[1].trim(); continue }
            if (l.startsWith("•") || l.startsWith("- ")) {
                flush()
                out += Chunk(source, section, clean(l.removePrefix("•").removePrefix("- ").trim()), step)
                continue
            }
            buf.append(clean(l)).append(' ')
        }
        flush()
        // A heading on its own (e.g. the document title) isn't worth quoting.
        return out.filter { it.text.length >= 25 }
    }

    private fun clean(s: String) = s.replace("**", "").trim()

    /**
     * Words that mean "what does the policy / terms / about page say": then a quote from the
     * documents beats a ready-made topic answer.
     */
    val docHints: List<String> = listOf(
        "политик", "конфиденц", "регламент", "услови", "правила", "соглаш", "о приложени", "согласи",
        "privacy", "policy", "terms", "conditions", "agreement", "consent", "about the app",
        "polityk", "prywatn", "regulamin", "warunk", "zgod", "o aplikacji",
        "політик", "конфіденц", "умови", "згод", "datenschutz", "nutzungsbed", "confidential", "conditions",
        "confidențial", "termeni", "zásady", "podmínk", "поверителн", "условия", "adatvédelm", "feltétel",
        "privatliv", "vilkår", "integritet", "villkor", "məxfilik", "şərtlər", "գաղտնիության", "պայմաններ",
        "კონფიდენციალ", "პირობები", "maxfiylik", "shartlar"
    ).map { WayoBrain.normalize(it) }

    fun mentionsDocs(message: String): Boolean {
        val n = WayoBrain.normalize(message)
        return docHints.any { n.contains(it) }
    }
}
