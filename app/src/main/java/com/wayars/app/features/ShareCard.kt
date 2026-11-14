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

    suspend fun share(context: Context) {
        val file = withContext(Dispatchers.Default) { runCatching { render(context) }.getOrNull() } ?: return
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val level = PetState.state.value.level
        val link = "https://play.google.com/store/apps/details?id=" + context.packageName
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, level, link))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.share_button)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
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
        val res = listOf("pet_${s}_level_up", "pet_${s}_happy", "pet_${s}_neutral", "pet_teen_happy").map { lookup(it) }.firstOrNull { it != 0 } ?: 0
        if (res != 0) {
            BitmapFactory.decodeResource(context.resources, res)?.let { img ->
                val size = 620f
                val left = (w - size) / 2f
                cv.drawBitmap(img, null, RectF(left, y + 30f, left + size, y + 30f + size), Paint(Paint.FILTER_BITMAP_FLAG))
                // Wayo's accessories on the share picture too.
                val anchor = com.wayars.app.presentation.pet.PetAnchors.of(context, res)
                if (anchor != null) {
                    val k = size / 512f
                    com.wayars.app.presentation.pet.Accessory.parse(DriverPrefs.accessories.value)
                        .filter { it.unlocked(pet.level) && it.slot in anchor.slots }
                        .sortedBy { it.slot.ordinal }
                        .forEach { a ->
                            BitmapFactory.decodeResource(context.resources, a.image)?.let { ab ->
                                val r = com.wayars.app.presentation.pet.PetAnchors.rect(a, anchor, ab.width, ab.height)
                                val l = left + r[0] * k; val t = y + 30f + r[1] * k
                                cv.drawBitmap(ab, null, RectF(l, t, l + r[2] * k, t + r[3] * k), Paint(Paint.FILTER_BITMAP_FLAG))
                            }
                        }
                }
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
