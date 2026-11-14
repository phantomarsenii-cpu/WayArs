package com.wayars.app.presentation.pet

import android.annotation.SuppressLint
import android.content.Context
import com.wayars.app.R
import com.wayars.app.pet.PetState

/**
 * Accessories Wayo earns by growing (smart decisions only). Each one is drawn ON Wayo for every
 * stage that can have it (pet_<stage>_acc_<key>, eyes-closed frame pet_<stage>_acc_<key>_blink),
 * so it sits naturally; that also means Wayo wears one accessory at a time.
 * [icon] is the small picture in the Settings picker.
 */
enum class Accessory(val minLevel: Int, val key: String, val icon: Int, val nameRes: Int) {
    CAP(3, "cap", R.drawable.pet_acc_cap, R.string.acc_cap),
    CROWN(6, "crown", R.drawable.pet_acc_crown, R.string.acc_crown),
    SUNGLASSES(8, "sunglasses", R.drawable.pet_acc_sunglasses, R.string.acc_sunglasses),
    HEADPHONES(10, "headphones", R.drawable.pet_acc_headphones, R.string.acc_headphones),
    CHAIN(13, "chain", R.drawable.pet_acc_chain, R.string.acc_chain),
    CARKEY(16, "carkey", R.drawable.pet_acc_carkey, R.string.acc_carkey);

    fun unlocked(level: Int) = level >= minLevel

    /** Wayo of [stage] wearing this accessory, or 0 when there's no such picture (egg). */
    @SuppressLint("DiscouragedApi")
    fun picture(context: Context, stage: PetState.Stage): Int =
        context.resources.getIdentifier("pet_${stage.name.lowercase()}_acc_$key", "drawable", context.packageName)

    /** Its eyes-closed frame, or 0 (sunglasses hide the eyes, so they don't need one). */
    @SuppressLint("DiscouragedApi")
    fun blink(context: Context, stage: PetState.Stage): Int =
        context.resources.getIdentifier("pet_${stage.name.lowercase()}_acc_${key}_blink", "drawable", context.packageName)

    companion object {
        fun unlockedAt(level: Int): Accessory? = values().firstOrNull { it.minLevel == level }

        /** The accessory saved in prefs (older builds stored a comma list: the first known one wins). */
        fun parse(s: String?): Accessory? =
            (s ?: "").split(',').firstNotNullOfOrNull { n -> values().firstOrNull { it.name == n.trim() } }

        /** The accessory Wayo actually wears at [level] on [stage] (unlocked and drawn for that stage). */
        fun worn(context: Context, pref: String?, level: Int, stage: PetState.Stage): Accessory? =
            parse(pref)?.takeIf { it.unlocked(level) && it.picture(context, stage) != 0 }
    }
}
