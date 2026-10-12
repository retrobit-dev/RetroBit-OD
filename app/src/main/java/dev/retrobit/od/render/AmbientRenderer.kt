package dev.retrobit.od.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import dev.retrobit.od.R
import dev.retrobit.od.data.CardStore
import dev.retrobit.od.data.IdCard
import dev.retrobit.od.data.Prefs

/**
 * Mesin gambar AOD. Semua layout digambar manual di Canvas (hitam pekat + piksel
 * berwarna) supaya:
 *  - piksel AMOLED yang tidak dipakai benar-benar mati,
 *  - tidak perlu ImageView/TextView → hemat & tidak bisa "berkedip",
 *  - hasil identik di overlay, activity, screensaver, dan pratinjau in-app.
 */
class AmbientRenderer(private val context: Context, private val prefs: Prefs) {

    private val density = context.resources.displayMetrics.density
    private val ink = Paint()
    private val glow = Paint()
    private val hair = Paint()
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val photo = Paint().apply { isFilterBitmap = true }
    private val qrPaint = Paint().apply {
        isFilterBitmap = false
        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
    }
    private val dst = Rect()
    private val cardBox = RectF()

    private var cardKey: String? = null
    private var cardData: IdCard = IdCard()

    private fun clockUnit(w: Int): Int {
        val scale = Prefs.CLOCK_SCALES[prefs.clockScale.coerceIn(0, Prefs.CLOCK_SCALES.lastIndex)]
        return (w * 0.027f * scale).toInt().coerceIn(2, 90)
    }

    private fun bodyUnit(w: Int): Int = (w * 0.0062f).toInt().coerceIn(2, 24)

    private fun smallUnit(w: Int): Int = (w * 0.0042f).toInt().coerceIn(1, 16)

    private fun card(): IdCard {
        val raw = prefs.cardJson
        if (raw != cardKey) {
            cardKey = raw
            cardData = IdCard.fromJson(raw)
        }
        return cardData
    }

    fun draw(
        canvas: Canvas,
        w: Int,
        h: Int,
        state: RenderState,
        insetTop: Int,
        insetBottom: Int
    ) {
        canvas.drawColor(Color.BLACK)
        val top = insetTop + 18f * density
        val bottom = (h - insetBottom - 18f * density).coerceAtLeast(top + 10f)
        when (prefs.layoutPreset) {
            Prefs.LAYOUT_CARD_FULL -> drawCardLayout(canvas, w, top, bottom, state)
            Prefs.LAYOUT_CLOCK_CENTER -> drawClockLayout(canvas, w, top, bottom, state, true)
            else -> drawClockLayout(canvas, w, top, bottom, state, false)
        }
        if (prefs.scanlines) drawScanlines(canvas, w, h)
    }

    // ── layout: jam ──────────────────────────────────────────────────────────

    private fun drawClockLayout(
        canvas: Canvas,
        w: Int,
        top: Float,
        bottom: Float,
        state: RenderState,
        center: Boolean
    ) {
        val cu = clockUnit(w)
        val bu = bodyUnit(w)
        val gap = bu * 1.7f
        val time = if (state.colonVisible) state.timeText else state.timeText.replace(':', ' ')

        var block = PixelFont.height(cu).toFloat()
        if (state.dateText.isNotEmpty()) block += PixelFont.height(bu) + gap
        if (state.batteryText.isNotEmpty()) block += PixelFont.height(bu) + gap
        if (state.notifText.isNotEmpty()) block += PixelFont.height(bu) + gap
        val withCard = prefs.cardEnabled && prefs.showCardLine
        if (withCard) block += cardCompactHeight(w, bu) + gap * 2f

        val cx = w / 2f + state.shiftX
        var y = if (center) {
            top + ((bottom - top - block) / 2f).coerceAtLeast(0f)
        } else {
            top + (bottom - top - block) * 0.12f
        }
        y += state.shiftY

        y += line(canvas, time, cx, y, cu, prefs.fontFace, 255, ALIGN_CENTER)
        if (state.dateText.isNotEmpty()) {
            y += gap
            y += line(canvas, state.dateText, cx, y, bu, PixelFont.FACE_PIXEL, 185, ALIGN_CENTER)
        }
        if (state.batteryText.isNotEmpty()) {
            y += gap
            y += line(canvas, state.batteryText, cx, y, bu, PixelFont.FACE_PIXEL, 150, ALIGN_CENTER)
        }
        if (state.notifText.isNotEmpty()) {
            y += gap
            y += line(canvas, state.notifText, cx, y, bu, PixelFont.FACE_PIXEL, 150, ALIGN_CENTER)
        }
        if (withCard) {
            y += gap * 1.6f
            divider(canvas, cx, y, w * 0.42f)
            y += bu
            drawCardCompact(canvas, w, cx, y, bottom, bu, state)
        }
    }

    private fun cardCompactHeight(w: Int, bu: Int): Float {
        val rows = if (prefs.cardUseImage) 8f else 3f
        return PixelFont.height(bu) * 2f * rows + bu * 2f
    }

    private fun drawCardCompact(
        canvas: Canvas,
        w: Int,
        cx: Float,
        yTop: Float,
        bottom: Float,
        bu: Int,
        state: RenderState
    ) {
        if (state.cardMasked) {
            drawMask(canvas, w, cx, yTop, bottom, bu)
            return
        }
        val c = card()
        if (prefs.cardUseImage) {
            val bmp = CardStore.loadImage(context, c)
            if (bmp != null) {
                val maxH = (bottom - yTop).coerceAtLeast(bu * 6f)
                val maxW = w * 0.74f
                val scale = minOf(maxW / bmp.width, maxH / bmp.height)
                val dw = (bmp.width * scale).toInt().coerceAtLeast(1)
                val dh = (bmp.height * scale).toInt().coerceAtLeast(1)
                dst.set((cx - dw / 2f).toInt(), yTop.toInt(), (cx + dw / 2f).toInt(), yTop.toInt() + dh)
                canvas.drawBitmap(bmp, null, dst, photo)
                return
            }
        }
        var y = yTop
        if (c.name.isNotBlank()) {
            y += line(canvas, clip(c.name, w * 0.86f, bu), cx, y, bu, PixelFont.FACE_PIXEL, 235, ALIGN_CENTER)
            y += bu * 0.6f
        }
        if (c.nip.isNotBlank()) {
            val unit = fit("NIP " + c.nip, w * 0.86f, (bu * 0.8f).toInt().coerceAtLeast(1))
            y += line(canvas, clip("NIP " + c.nip, w * 0.86f, unit), cx, y, unit, PixelFont.FACE_PIXEL, 150, ALIGN_CENTER)
            y += bu * 0.6f
        }
        if (c.position.isNotBlank()) {
            val unit = (bu * 0.75f).toInt().coerceAtLeast(1)
            y += line(canvas, clip(c.position, w * 0.86f, unit), cx, y, unit, PixelFont.FACE_PIXEL, 120, ALIGN_CENTER)
        }
    }

    // ── layout: kartu penuh ─────────────────────────────────────────────────

    private fun drawCardLayout(
        canvas: Canvas,
        w: Int,
        top: Float,
        bottom: Float,
        state: RenderState
    ) {
        val su = smallUnit(w)
        val bu = bodyUnit(w)
        val cu = (w * 0.0125f).toInt().coerceIn(3, 40)
        val cx = w / 2f + state.shiftX
        var y = top + state.shiftY
        val time = if (state.colonVisible) state.timeText else state.timeText.replace(':', ' ')
        y += line(canvas, time, cx, y, cu, prefs.fontFace, 255, ALIGN_CENTER)
        if (state.dateText.isNotEmpty()) {
            y += su * 2f
            y += line(canvas, state.dateText, cx, y, su, PixelFont.FACE_PIXEL, 150, ALIGN_CENTER)
        }
        y += bu * 2.2f

        val margin = w * 0.055f
        cardBox.set(margin, y, w - margin, bottom)
        if (cardBox.height() < 80f * density) return
        frame.color = withAlpha(prefs.accentColor, 190)
        frame.strokeWidth = (2f * density).coerceAtLeast(2f)
        val radius = 12f * density
        canvas.drawRoundRect(cardBox, radius, radius, frame)

        if (state.cardMasked) {
            drawMask(canvas, w, cx, cardBox.top + 24f * density, cardBox.bottom, bu)
            return
        }
        val c = card()
        val bmp = if (prefs.cardUseImage) CardStore.loadImage(context, c) else null
        if (bmp != null) {
            drawFittedBitmap(canvas, bmp, cardBox, 8f * density)
        } else {
            drawCardData(canvas, c, cardBox, 10f * density)
        }
    }

    private fun drawCardData(canvas: Canvas, c: IdCard, r: RectF, pad: Float) {
        val inner = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        val w = inner.width()
        val labelUnit = (w * 0.011f).toInt().coerceIn(1, 10)
        val valueUnit = (w * 0.019f).toInt().coerceIn(2, 18)
        var y = inner.top

        val header = context.getString(R.string.card_header)
        val headerUnit = fit(header, w, labelUnit + 1)
        y += line(canvas, header, inner.centerX(), y, headerUnit, PixelFont.FACE_PIXEL, 170, ALIGN_CENTER)
        y += headerUnit * 2f

        val qrSize = minOf(inner.height() * 0.34f, w * 0.3f)
        val bottomReserve = qrSize + valueUnit * 2f

        // pas foto di kiri
        val fields = buildList {
            if (c.name.isNotBlank()) add(Pair(context.getString(R.string.card_label_name), c.name))
            if (c.nip.isNotBlank()) add(Pair(context.getString(R.string.card_label_nip), c.nip))
            if (c.rank.isNotBlank()) add(Pair(context.getString(R.string.card_label_rank), c.rank))
            if (c.position.isNotBlank()) add(Pair(context.getString(R.string.card_label_position), c.position))
            if (c.unit.isNotBlank()) add(Pair(context.getString(R.string.card_label_unit), c.unit))
            if (c.institution.isNotBlank()) add(Pair(context.getString(R.string.card_label_inst), c.institution))
        }
        val bmp = CardStore.loadPhoto(context, c)
        val textLeft: Float
        if (bmp != null) {
            val photoH = minOf(w * 0.3f, inner.bottom - y - valueUnit * 3f).coerceAtLeast(1f)
            val photoW = photoH * 0.78f
            dst.set(
                inner.left.toInt(),
                y.toInt(),
                (inner.left + photoW).toInt(),
                (y + photoH).toInt()
            )
            canvas.drawBitmap(bmp, null, dst, photo)
            textLeft = inner.left + photoW + pad
        } else {
            textLeft = inner.left
        }

        val colW = inner.right - textLeft
        var ty = y
        for (row in fields) {
            val rowH = labelUnit + valueUnit * 2 + labelUnit
            if (ty + rowH > inner.bottom - bottomReserve * 0.35f) break
            ty += line(canvas, row.first, textLeft, ty, labelUnit, PixelFont.FACE_PIXEL, 120, ALIGN_LEFT)
            ty += labelUnit * 0.5f
            val vu = fit(row.second, colW, valueUnit)
            ty += line(
                canvas,
                clip(row.second, colW, vu),
                textLeft,
                ty,
                vu,
                PixelFont.FACE_PIXEL,
                240,
                ALIGN_LEFT
            )
            ty += valueUnit * 1.2f
        }

        // footer: catatan + QR
        if (c.note.isNotBlank()) {
            val nu = (w * 0.012f).toInt().coerceAtLeast(1)
            line(canvas, clip(c.note, w * 0.6f, nu), inner.left, inner.bottom - nu * 7f, nu, PixelFont.FACE_PIXEL, 130, ALIGN_LEFT)
        }
        val qr = CardStore.qrBitmap(c.qr)
        if (qr != null) {
            val size = minOf(qrSize, inner.height() * 0.4f)
            qrPaint.colorFilter = PorterDuffColorFilter(
                withAlpha(prefs.accentColor, 255),
                PorterDuff.Mode.SRC_ATOP
            )
            dst.set(
                (inner.right - size).toInt(),
                (inner.bottom - size).toInt(),
                inner.right.toInt(),
                inner.bottom.toInt()
            )
            canvas.drawBitmap(qr, null, dst, qrPaint)
        }
    }

    private fun drawFittedBitmap(canvas: Canvas, bmp: Bitmap, r: RectF, pad: Float) {
        val box = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        val scale = minOf(box.width() / bmp.width, box.height() / bmp.height)
        val dw = (bmp.width * scale).toInt().coerceAtLeast(1)
        val dh = (bmp.height * scale).toInt().coerceAtLeast(1)
        val left = (box.centerX() - dw / 2f).toInt()
        val top = (box.centerY() - dh / 2f).toInt()
        dst.set(left, top, left + dw, top + dh)
        canvas.drawBitmap(bmp, null, dst, photo)
    }

    // ── privasi / mask ───────────────────────────────────────────────────────

    private fun drawMask(canvas: Canvas, w: Int, cx: Float, yTop: Float, bottom: Float, bu: Int) {
        val title = context.getString(R.string.card_hidden)
        val hint = context.getString(R.string.card_hidden_hint)
        val h = (bottom - yTop).coerceAtLeast(bu * 10f)
        frame.color = withAlpha(Color.parseColor("#8A93A5"), 150)
        frame.strokeWidth = 2f * density
        cardBox.set(w * 0.08f, yTop, w * 0.92f, yTop + h)
        if (cardBox.height() > 4f) {
            canvas.drawRoundRect(cardBox, 10f * density, 10f * density, frame)
        }
        // ikon gembok pixel
        val lockU = (bu * 0.9f).toInt().coerceAtLeast(2)
        val lockW = PixelFont.width("°°", lockU).toFloat()
        val ly = yTop + h / 2f - lockU * 5f
        hair.color = withAlpha(prefs.accentColor, 150)
        canvas.drawRect(cx - lockW / 2f, ly + lockU * 3f, cx + lockW / 2f, ly + lockU * 7f, hair)
        canvas.drawRect(cx - lockW / 2f, ly, cx - lockW / 6f, ly + lockU * 3f, hair)
        canvas.drawRect(cx + lockW / 6f, ly, cx + lockW / 2f, ly + lockU * 3f, hair)
        line(canvas, title, cx, yTop + h / 2f + lockU * 9f, bu, PixelFont.FACE_PIXEL, 210, ALIGN_CENTER)
        val hu = (bu * 0.7f).toInt().coerceAtLeast(1)
        line(canvas, clip(hint, w * 0.84f, hu), cx, yTop + h / 2f + lockU * 9f + bu * 2.2f, hu, PixelFont.FACE_PIXEL, 130, ALIGN_CENTER)
    }

    private fun drawScanlines(canvas: Canvas, w: Int, h: Int) {
        hair.color = Color.argb(70, 0, 0, 0)
        val step = (3f * density).toInt().coerceAtLeast(2)
        var y = 0f
        while (y < h) {
            canvas.drawRect(0f, y, w.toFloat(), y + 1f, hair)
            y += step
        }
    }

    private fun divider(canvas: Canvas, cx: Float, y: Float, halfW: Float) {
        hair.color = withAlpha(prefs.accentColor, 90)
        canvas.drawRect(cx - halfW, y, cx + halfW, y + 1f, hair)
    }

    // ── helper teks ──────────────────────────────────────────────────────────

    private fun line(
        canvas: Canvas,
        text: String,
        anchorX: Float,
        top: Float,
        unit: Int,
        face: Int,
        alpha: Int,
        align: Int
    ): Int {
        if (text.isEmpty()) return PixelFont.height(unit)
        val width = PixelFont.width(text, unit).toFloat()
        val left = when (align) {
            ALIGN_LEFT -> anchorX
            ALIGN_RIGHT -> anchorX - width
            else -> anchorX - width / 2f
        }
        val accent = prefs.accentColor
        if (prefs.glow) {
            glow.color = accent
            glow.alpha = (alpha * 0.28f).toInt().coerceIn(0, 255)
            PixelFont.draw(canvas, text, left, top, unit, face, glow, unit * 0.5f)
        }
        ink.color = accent
        ink.alpha = alpha.coerceIn(0, 255)
        PixelFont.draw(canvas, text, left, top, unit, face, ink)
        return PixelFont.height(unit)
    }

    private fun fit(text: String, maxWidth: Float, startUnit: Int): Int {
        var u = startUnit.coerceAtLeast(1)
        while (u > 1 && PixelFont.width(text, u) > maxWidth) u--
        return u
    }

    private fun clip(text: String, maxWidth: Float, unit: Int): String {
        if (PixelFont.width(text, unit) <= maxWidth) return text
        var n = text.length
        while (n > 1 && PixelFont.width(text.substring(0, n) + "..", unit) > maxWidth) n--
        return text.substring(0, n) + ".."
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2
    }
}
