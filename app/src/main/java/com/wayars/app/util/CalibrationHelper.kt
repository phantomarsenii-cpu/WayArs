package com.wayars.app.util

/**
 * Pure helpers behind the step-by-step calibration dialog (Settings ->
 * Supported apps -> calibrate). Kept out of the Compose code so they can be
 * tested on their own.
 *
 * The dialog no longer asks "which LINE is the price" and then dumps that
 * whole line (minus digits) into a token field — on real Bolt text like
 * "2.6 км, 29 мин, PLN 10.33" that produced the token "км мин PLN" for all
 * three fields, which matched nothing and confused everyone. Now the user
 * taps the exact NUMBER, and the unit word right next to that number is
 * taken as the token ("PLN", "км", "мин").
 */
object CalibrationHelper {

    /** One tappable number inside one scanned line. */
    data class NumberPick(val lineIndex: Int, val range: IntRange, val text: String) {
        val value: Double? get() = text.replace(',', '.').toDoubleOrNull()
    }

    private val numberRegex = Regex("""\d+(?:[.,]\d+)?""")
    private val digitRegex = Regex("""\d""")
    private val separators = """\s   \d.,;:()\[\]|·•~≈/\-–—+*"""
    private val wordAfterRegex = Regex("""^[\s   ]*([^$separators]+)""")
    private val wordBeforeRegex = Regex("""([^$separators]+)[\s   ]*$""")

    /**
     * Lines worth showing: only ones that contain a digit (a line without a
     * number can't be the price, distance or time), without duplicates, and
     * without icon-font glyphs that render as random CJK characters.
     */
    fun candidateLines(rawTexts: List<String>): List<String> =
        rawTexts.asSequence()
            .flatMap { it.split('\n').asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() && digitRegex.containsMatchIn(it) }
            .filter { line -> line.none { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } }
            .distinct()
            .toList()

    fun numbersIn(lineIndex: Int, line: String): List<NumberPick> =
        numberRegex.findAll(line).map { NumberPick(lineIndex, it.range, it.value) }.toList()

    /**
     * The unit word glued to [pick]: the word right AFTER the number if there
     * is one ("2.6 км" -> "км", "10,33 zł" -> "zł"), otherwise the word right
     * BEFORE it ("PLN 10.33" -> "PLN", "€12" -> "€"). Empty when neither exists.
     */
    fun unitTokenFor(line: String, pick: NumberPick): String {
        val after = line.substring((pick.range.last + 1).coerceAtMost(line.length))
        wordAfterRegex.find(after)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val before = line.substring(0, pick.range.first.coerceAtLeast(0))
        return wordBeforeRegex.find(before)?.groupValues?.get(1)?.trim().orEmpty()
    }

    /** "10.33" / "10,33" -> 10.33 for comparing a pick with what the parser read. */
    fun sameNumber(a: Double?, b: Double?): Boolean =
        a != null && b != null && kotlin.math.abs(a - b) < 0.005
}
