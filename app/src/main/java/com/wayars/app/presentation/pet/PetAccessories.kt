package com.wayars.app.presentation.pet

import android.content.Context
import com.wayars.app.R

/** Where an accessory is worn; one accessory per slot. */
enum class AccessorySlot { HEAD, EYES, NECK, BADGE }

/** Accessories Wayo earns by growing (smart decisions only). Images are in drawable-nodpi. */
enum class Accessory(val minLevel: Int, val slot: AccessorySlot, val image: Int, val nameRes: Int) {
    CAP(3, AccessorySlot.HEAD, R.drawable.pet_acc_cap, R.string.acc_cap),
    CROWN(6, AccessorySlot.HEAD, R.drawable.pet_acc_crown, R.string.acc_crown),
    SUNGLASSES(8, AccessorySlot.EYES, R.drawable.pet_acc_sunglasses, R.string.acc_sunglasses),
    HEADPHONES(10, AccessorySlot.NECK, R.drawable.pet_acc_headphones, R.string.acc_headphones),
    CHAIN(13, AccessorySlot.NECK, R.drawable.pet_acc_chain, R.string.acc_chain),
    CARKEY(16, AccessorySlot.BADGE, R.drawable.pet_acc_carkey, R.string.acc_carkey);

    fun unlocked(level: Int) = level >= minLevel

    companion object {
        fun unlockedAt(level: Int): Accessory? = values().firstOrNull { it.minLevel == level }
        fun parse(s: String?): Set<Accessory> =
            (s ?: "").split(',').mapNotNull { n -> values().firstOrNull { it.name == n } }.toSet()
    }
}

/**
 * Points on each Wayo picture (512×512 canvas): head centre x, top of the crest, eye line, width
 * of the eyes/glasses, neck line, head width, which slots fit that pose, and a size factor for
 * neck items (the baby's head is as wide as its body). Hats don't go on the Pro helmet or the
 * Legend crown, glasses not on the "cool" pose (it has its own) or while sleeping.
 */
class PetAnchor(
    val hx: Float, val crest: Float, val eyes: Float, val eyesW: Float,
    val neck: Float, val headW: Float, val slots: Set<AccessorySlot>, val neckScale: Float = 1f
)

object PetAnchors {
    private val H = AccessorySlot.HEAD; private val E = AccessorySlot.EYES
    private val N = AccessorySlot.NECK; private val B = AccessorySlot.BADGE
    private val map = mapOf(
        "pet_baby_neutral" to PetAnchor(256f, 26f, 200f, 246f, 292f, 246f, setOf(H, E, N, B), 0.7f),
        "pet_teen_neutral" to PetAnchor(271f, 26f, 121f, 138f, 205f, 133f, setOf(H, E, N, B)),
        "pet_pro_neutral" to PetAnchor(215f, 40f, 121f, 92f, 205f, 105f, setOf(E, N, B)),
        "pet_legend_neutral" to PetAnchor(241f, 40f, 128f, 138f, 210f, 133f, setOf(E, N, B)),
        "pet_teen_happy" to PetAnchor(241f, 26f, 113f, 133f, 174f, 125f, setOf(H, E, N, B)),
        "pet_teen_eating" to PetAnchor(261f, 41f, 108f, 128f, 179f, 130f, setOf(H, E, N, B)),
        "pet_teen_sad" to PetAnchor(287f, 113f, 215f, 118f, 287f, 120f, setOf(H, E, N, B)),
        "pet_teen_sleeping" to PetAnchor(246f, 120f, 180f, 120f, 261f, 125f, setOf(N, B)),
        "pet_teen_helper" to PetAnchor(225f, 31f, 121f, 143f, 179f, 135f, setOf(H, E, N, B)),
        "pet_teen_cool" to PetAnchor(251f, 102f, 185f, 148f, 246f, 130f, setOf(H, N, B)),
        "pet_teen_level_up" to PetAnchor(266f, 97f, 169f, 148f, 241f, 140f, setOf(H, E, N, B)),
        "pet_teen_hello" to PetAnchor(225f, 31f, 113f, 143f, 179f, 135f, setOf(H, E, N, B)),
        "pet_teen_working" to PetAnchor(236f, 31f, 118f, 154f, 184f, 140f, setOf(H, E, N, B)),
        "pet_teen_break" to PetAnchor(261f, 31f, 154f, 143f, 236f, 140f, setOf(H, E, N, B))
    )

    fun of(context: Context, resId: Int): PetAnchor? =
        if (resId == 0) null else runCatching { map[context.resources.getResourceEntryName(resId)] }.getOrNull()

    /** left, top, width, height on the 512 canvas for [a] (image [w]×[h]) on [p]. */
    fun rect(a: Accessory, p: PetAnchor, w: Int, h: Int): FloatArray {
        val aspect = w.toFloat() / h
        return when (a) {
            Accessory.CAP -> {
                val bottom = p.eyes - 0.12f * p.headW; val top = p.crest - 0.05f * p.headW
                val hh = bottom - top; val ww = hh * aspect
                floatArrayOf(p.hx - ww / 2, top, ww, hh)
            }
            Accessory.CROWN -> {
                val ww = 0.75f * p.headW; val hh = ww / aspect
                val bottom = (p.crest + p.eyes) / 2 - 0.05f * p.headW
                floatArrayOf(p.hx - ww / 2, bottom - hh, ww, hh)
            }
            Accessory.SUNGLASSES -> {
                val ww = 1.08f * p.eyesW; val hh = ww / aspect
                floatArrayOf(p.hx - ww / 2, p.eyes - hh / 2, ww, hh)
            }
            Accessory.HEADPHONES -> {
                val ww = 0.95f * p.headW * p.neckScale; val hh = ww / aspect
                floatArrayOf(p.hx - ww / 2, p.neck - 0.06f * p.headW, ww, hh)
            }
            Accessory.CHAIN -> {
                val ww = 0.80f * p.headW * p.neckScale; val hh = ww / aspect
                floatArrayOf(p.hx - ww / 2, p.neck - 0.10f * p.headW, ww, hh)
            }
            Accessory.CARKEY -> {
                val ww = 62f; val hh = ww / aspect
                floatArrayOf(452f - ww / 2, 470f - hh, ww, hh)
            }
        }
    }
}
