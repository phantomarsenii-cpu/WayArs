package com.wayars.app.features

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.wayars.app.R
import com.wayars.app.appContainer
import com.wayars.app.data.prefs.DriverPrefs
import com.wayars.app.domain.model.Currency
import com.wayars.app.pet.PetState
import com.wayars.app.service.accessibility.ActiveSessionTracker
import com.wayars.app.util.CurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * "Share" card: a 1080×1350 picture of Wayo with the driver's level and records (for TikTok,
 * Instagram, chats), drawn on the phone in the driver's card theme, plus a Google Play link.
 */
object ShareCard {

    /** [achievements]: the "Achievements" picture (records and badges) instead of Wayo's card. */
    suspend fun share(context: Context, achievements: Boolean = false) {
        val file = withContext(Dispatchers.Default) {
            runCatching { if (achievements) renderAchievements(context) else render(context) }.getOrNull()
        } ?: return
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val level = PetState.state.value.level
        val link = "https://play.google.com/store/apps/details?id=" + context.packageName
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(
                Intent.EXTRA_TEXT,
                if (achievements) context.getString(R.string.share_ach_text, link) else context.getString(R.string.share_text, level, link)
            )
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.share_button)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * The driver's achievements as a picture: smart-days streak, records, best day and order,
     * and every badge (earned ones lit up, the rest with progress). Wayo peeks from a corner.
     */
    @SuppressLint("DiscouragedApi")
    private suspend fun renderAchievements(context: Context): File {
        val pet = PetState.state.value
        val c = context.appContainer()
        val cur = runCatching { c.settingsRepository.currency.first() }.getOrDefault(Currency.default)
        val now = System.currentTimeMillis()
        val a = withContext(Dispatchers.IO) {
            Earnings.achievements(c.orderRepository.acceptedSince(0L), ActiveSessionTracker.intervals(0L, now))
        }
        val theme = CardTheme.of(DriverPrefs.cardTheme.value).takeIf { it.unlocked(pet.level) } ?: CardTheme.CLASSIC
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val accent = theme.accent.toInt()
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), theme.bgTop.toInt(), theme.bgBottom.toInt(), Shader.TileMode.CLAMP)
        })
        cv.drawRoundRect(RectF(36f, 36f, w - 36f, h - 36f), 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f; color = theme.frame.toInt()
        })
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        fun text(s: String, x: Float, y: Float, width: Int, size: Float, color: Int, typeface: Typeface = bold, center: Boolean = true): Float {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; this.typeface = typeface }
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, width)
                .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).build()
            cv.save(); cv.translate(x, y); layout.draw(cv); cv.restore()
            return y + layout.height
        }
        var y = text("WayArs", 80f, 80f, w - 160, 54f, accent)
        y = text("🏆 " + context.getString(R.string.stats_achievements_title), 80f, y + 10f, w - 160, 84f, 0xFFFFFFFF.toInt())
        // Records
        val lines = buildList {
            add(context.getString(R.string.ach_streak, a.currentSmartStreak, a.bestSmartStreak))
            a.recordPerHour?.let { add(context.getString(R.string.ach_record_hour, context.getString(R.string.summary_part_hour, CurrencyFormatter.format(it, cur, 0)))) }
            a.bestDay?.let { add(context.getString(R.string.ach_best_day, CurrencyFormatter.format(it, cur))) }
            a.bestOrder?.let { add(context.getString(R.string.ach_best_order, CurrencyFormatter.format(it, cur))) }
        }
        y += 30f
        for (l in lines) y = text(l, 100f, y + 14f, w - 200, 42f, 0xFFE6E6E6.toInt(), Typeface.DEFAULT)
        // Badges, two per row
        class B(val icon: String, val label: Int, val progress: Double, val target: Double)
        val badges = listOf(
            B("🔥", R.string.badge_smart3, a.bestSmartStreak.toDouble(), 3.0),
            B("⭐", R.string.badge_smart7, a.bestSmartStreak.toDouble(), 7.0),
            B("🏆", R.string.badge_smart30, a.bestSmartStreak.toDouble(), 30.0),
            B("💯", R.string.badge_orders100, a.totalOrders.toDouble(), 100.0),
            B("🚀", R.string.badge_orders1000, a.totalOrders.toDouble(), 1000.0),
            B("⏱", R.string.badge_hours100, a.totalActiveHours, 100.0)
        )
        y += 50f
        val colW = (w - 200f - 30f) / 2f
        val chipH = 110f
        badges.forEachIndexed { i, b ->
            val col = i % 2; val row = i / 2
            val left = 100f + col * (colW + 30f); val top = y + row * (chipH + 24f)
            val done = b.progress >= b.target
            cv.drawRoundRect(RectF(left, top, left + colW, top + chipH), 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (done) accent else 0x33FFFFFF
            })
            val label = "${b.icon} " + context.getString(b.label) +
                if (done) " ✓" else "  ${b.progress.toInt().coerceAtMost(b.target.toInt())}/${b.target.toInt()}"
            text(label, left + 16f, top + 30f, colW.toInt() - 32, 36f, if (done) 0xFF06210F.toInt() else 0xFFE0E0E0.toInt())
        }
        // Wayo peeking from the bottom corner
        val s = pet.stage.name.lowercase()
        fun lookup(n: String) = context.resources.getIdentifier(n, "drawable", context.packageName)
        val res = listOf("pet_${s}_happy", "pet_${s}_neutral", "pet_teen_happy").map { lookup(it) }.firstOrNull { it != 0 } ?: 0
        if (res != 0) BitmapFactory.decodeResource(context.resources, res)?.let { img ->
            val size = 300f
            cv.drawBitmap(img, null, RectF(w - 60f - size, h - 120f - size, w - 60f, h - 120f), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        text(context.getString(R.string.share_footer), 80f, h - 150f, w - 460, 32f, accent, Typeface.DEFAULT, center = false)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "wayars_achievements.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    @SuppressLint("DiscouragedApi")
    private suspend fun render(context: Context): File {
        val pet = PetState.state.value
        val c = context.appContainer()
        val cur = runCatching { c.settingsRepository.currency.first() }.getOrDefault(Currency.default)
        val now = System.currentTimeMillis()
        val a = withContext(Dispatchers.IO) {
            Earnings.achievements(c.orderRepository.acceptedSince(0L), ActiveSessionTracker.intervals(0L, now))
        }
        val theme = CardTheme.of(DriverPrefs.cardTheme.value).takeIf { it.unlocked(pet.level) } ?: CardTheme.CLASSIC
        val stageName = context.getString(
            when (pet.stage) {
                PetState.Stage.EGG -> R.string.pet_stage_egg
                PetState.Stage.BABY -> R.string.pet_stage_baby
                PetState.Stage.TEEN -> R.string.pet_stage_teen
                PetState.Stage.PRO -> R.string.pet_stage_pro
                PetState.Stage.LEGEND -> R.string.pet_stage_legend
            }
        )
        val lines = buildList {
            a.recordPerHour?.let { add(context.getString(R.string.share_record, context.getString(R.string.summary_part_hour, CurrencyFormatter.format(it, cur, 0)))) }
            if (a.bestSmartStreak > 0) add(context.getString(R.string.share_streak, a.bestSmartStreak))
            a.bestDay?.let { add(context.getString(R.string.share_best_day, CurrencyFormatter.format(it, cur))) }
        }

        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val accent = theme.accent.toInt()
        // Background in the driver's theme, with a soft frame.
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), theme.bgTop.toInt(), theme.bgBottom.toInt(), Shader.TileMode.CLAMP)
        })
        cv.drawRoundRect(RectF(36f, 36f, w - 36f, h - 36f), 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f; color = theme.frame.toInt()
        })
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        fun text(s: String, y: Float, size: Float, color: Int, typeface: Typeface = bold): Float {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; this.typeface = typeface }
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, w - 160).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
            cv.save(); cv.translate(80f, y); layout.draw(cv); cv.restore()
            return y + layout.height
        }
        var y = text("WayArs", 90f, 64f, accent)
        // Wayo picture for the current stage.
        val s = pet.stage.name.lowercase()
        fun lookup(n: String) = context.resources.getIdentifier(n, "drawable", context.packageName)
        // Wearing the chosen accessory (drawn on Wayo) when there is one, else the happiest pose.
        val accRes = com.wayars.app.presentation.pet.Accessory.worn(context, DriverPrefs.accessories.value, pet.level, pet.stage)
            ?.picture(context, pet.stage) ?: 0
        val res = if (accRes != 0) accRes
            else listOf("pet_${s}_level_up", "pet_${s}_happy", "pet_${s}_neutral", "pet_teen_happy").map { lookup(it) }.firstOrNull { it != 0 } ?: 0
        if (res != 0) {
            BitmapFactory.decodeResource(context.resources, res)?.let { img ->
                val size = 620f
                val left = (w - size) / 2f
                cv.drawBitmap(img, null, RectF(left, y + 30f, left + size, y + 30f + size), Paint(Paint.FILTER_BITMAP_FLAG))
                y += 30f + size
            }
        }
        y = text(context.getString(R.string.share_title, pet.level, stageName), y + 20f, 62f, 0xFFFFFFFF.toInt())
        y += 16f
        for (l in lines) y = text(l, y + 8f, 42f, 0xFFE0E0E0.toInt(), Typeface.DEFAULT) 
        text(context.getString(R.string.share_footer), h - 170f, 36f, accent, Typeface.DEFAULT)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "wayo_share.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }
}
