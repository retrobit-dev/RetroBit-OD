package dev.retrobit.od.render

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Font bitmap buatan sendiri (5×7) + mode LED 7 segmen.
 * Tidak ada file TTF/aset: setiap glyph digambar sebagai kotak piksel sehingga
 * tajam di semua density, ringan, dan karakternya selalu konsisten di layar AMOLED.
 *
 * Baris tiap glyph disimpan sebagai 5 bit (bit paling kiri = piksel paling kiri).
 */
object PixelFont {

    const val FACE_PIXEL = 0
    const val FACE_LED = 1

    private const val W = 5
    private const val H = 7

    // segmen LED
    private const val SA = 1 shl 0 // atas
    private const val SB = 1 shl 1 // kanan atas
    private const val SC = 1 shl 2 // kanan bawah
    private const val SD = 1 shl 3 // bawah
    private const val SE = 1 shl 4 // kiri bawah
    private const val SF = 1 shl 5 // kiri atas
    private const val SG = 1 shl 6 // tengah

    private val GLYPHS: Map<Char, IntArray> = mapOf(
        '0' to intArrayOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110),
        '1' to intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
        '2' to intArrayOf(0b01110, 0b10001, 0b00001, 0b00110, 0b01000, 0b10001, 0b11111),
        '3' to intArrayOf(0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110),
        '4' to intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010),
        '5' to intArrayOf(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110),
        '6' to intArrayOf(0b01110, 0b10000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110),
        '7' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000),
        '8' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110),
        '9' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100),
        'A' to intArrayOf(0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001),
        'B' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110),
        'C' to intArrayOf(0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110),
        'D' to intArrayOf(0b11100, 0b10010, 0b10001, 0b10001, 0b10001, 0b10010, 0b11100),
        'E' to intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111),
        'F' to intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000),
        'G' to intArrayOf(0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01111),
        'H' to intArrayOf(0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001),
        'I' to intArrayOf(0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
        'J' to intArrayOf(0b00111, 0b00010, 0b00010, 0b00010, 0b00010, 0b10010, 0b01100),
        'K' to intArrayOf(0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001),
        'L' to intArrayOf(0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111),
        'M' to intArrayOf(0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001),
        'N' to intArrayOf(0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001, 0b10001),
        'O' to intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        'P' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000),
        'Q' to intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101),
        'R' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001),
        'S' to intArrayOf(0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110),
        'T' to intArrayOf(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100),
        'U' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        'V' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100),
        'W' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b11011, 0b10001),
        'X' to intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001),
        'Y' to intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100),
        'Z' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111),
        ' ' to intArrayOf(0, 0, 0, 0, 0, 0, 0),
        '.' to intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b01100, 0b01100),
        ',' to intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00110, 0b00110, 0b01100),
        ':' to intArrayOf(0b00000, 0b01100, 0b01100, 0b00000, 0b01100, 0b01100, 0b00000),
        ';' to intArrayOf(0b00000, 0b01100, 0b01100, 0b00000, 0b00110, 0b00110, 0b01100),
        '-' to intArrayOf(0b00000, 0b00000, 0b00000, 0b11111, 0b00000, 0b00000, 0b00000),
        '_' to intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b11111),
        '+' to intArrayOf(0b00000, 0b00100, 0b00100, 0b11111, 0b00100, 0b00100, 0b00000),
        '=' to intArrayOf(0b00000, 0b00000, 0b11111, 0b00000, 0b11111, 0b00000, 0b00000),
        '/' to intArrayOf(0b00001, 0b00010, 0b00010, 0b00100, 0b01000, 0b01000, 0b10000),
        '\\' to intArrayOf(0b10000, 0b01000, 0b01000, 0b00100, 0b00010, 0b00010, 0b00001),
        '%' to intArrayOf(0b11001, 0b11010, 0b00010, 0b00100, 0b01000, 0b01011, 0b10011),
        '°' to intArrayOf(0b01100, 0b10010, 0b01100, 0b00000, 0b00000, 0b00000, 0b00000),
        '!' to intArrayOf(0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00000, 0b00100),
        '?' to intArrayOf(0b01110, 0b10001, 0b00001, 0b00110, 0b00100, 0b00000, 0b00100),
        '\'' to intArrayOf(0b00100, 0b00100, 0b01000, 0b00000, 0b00000, 0b00000, 0b00000),
        '"' to intArrayOf(0b01010, 0b01010, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000),
        '(' to intArrayOf(0b00010, 0b00100, 0b01000, 0b01000, 0b01000, 0b00100, 0b00010),
        ')' to intArrayOf(0b01000, 0b00100, 0b00010, 0b00010, 0b00010, 0b00100, 0b01000),
        '[' to intArrayOf(0b01110, 0b01000, 0b01000, 0b01000, 0b01000, 0b01000, 0b01110),
        ']' to intArrayOf(0b01110, 0b00010, 0b00010, 0b00010, 0b00010, 0b00010, 0b01110),
        '{' to intArrayOf(0b00110, 0b01000, 0b01000, 0b10000, 0b01000, 0b01000, 0b00110),
        '}' to intArrayOf(0b01100, 0b00010, 0b00010, 0b00001, 0b00010, 0b00010, 0b01100),
        '|' to intArrayOf(0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100),
        '<' to intArrayOf(0b00010, 0b00100, 0b01000, 0b10000, 0b01000, 0b00100, 0b00010),
        '>' to intArrayOf(0b01000, 0b00100, 0b00010, 0b00001, 0b00010, 0b00100, 0b01000),
        '*' to intArrayOf(0b00000, 0b00100, 0b10101, 0b01110, 0b10101, 0b00100, 0b00000),
        '#' to intArrayOf(0b01010, 0b01010, 0b11111, 0b01010, 0b11111, 0b01010, 0b01010),
        '&' to intArrayOf(0b01100, 0b10010, 0b10100, 0b01000, 0b10101, 0b10010, 0b01101),
        '@' to intArrayOf(0b01110, 0b10001, 0b10111, 0b10101, 0b10111, 0b10000, 0b01110),
        '→' to intArrayOf(0b00000, 0b00100, 0b00010, 0b11111, 0b00010, 0b00100, 0b00000),
        '•' to intArrayOf(0b00000, 0b00000, 0b01110, 0b01110, 0b01110, 0b00000, 0b00000)
    )

    /** LED: karakter yang punya bentuk 7 segmen; sisanya fallback ke font pixel. */
    private val LED: Map<Char, Int> = mapOf(
        '0' to (SA or SB or SC or SD or SE or SF),
        '1' to (SB or SC),
        '2' to (SA or SB or SG or SE or SD),
        '3' to (SA or SB or SG or SC or SD),
        '4' to (SF or SG or SB or SC),
        '5' to (SA or SF or SG or SC or SD),
        '6' to (SA or SF or SG or SE or SC or SD),
        '7' to (SA or SB or SC),
        '8' to (SA or SB or SC or SD or SE or SF or SG),
        '9' to (SA or SB or SC or SD or SF or SG),
        'A' to (SA or SB or SC or SE or SF or SG),
        'B' to (SC or SD or SE or SF or SG),
        'C' to (SA or SF or SE or SD),
        'D' to (SB or SC or SD or SE),
        'E' to (SA or SF or SG or SE or SD),
        'F' to (SA or SF or SG or SE),
        'G' to (SA or SC or SD or SF or SG),
        'H' to (SB or SC or SE or SF or SG),
        'I' to (SB or SC),
        'J' to (SB or SC or SD or SE),
        'L' to (SF or SE or SD),
        'N' to (SC or SE or SG),
        'O' to (SB or SC or SD or SE),
        'P' to (SA or SB or SE or SF or SG),
        'R' to (SE or SG),
        'S' to (SA or SF or SG or SC or SD),
        'T' to (SF or SG or SE or SD),
        'U' to (SB or SC or SD or SE),
        'Y' to (SB or SC or SD or SE),
        '-' to SG,
        '_' to SD
    )

    fun gapFor(unit: Int): Int = if (unit >= 3) unit / 2 else 1

    fun advance(unit: Int): Int = W * unit + gapFor(unit)

    fun width(text: String, unit: Int): Int {
        if (text.isEmpty()) return 0
        return text.length * advance(unit) - gapFor(unit)
    }

    fun height(unit: Int): Int = H * unit

    /**
     * @param x posisi kiri, @param y posisi atas. [inflate] memperbesar tiap kotak
     * (dipakai untuk melukis lapisan glow di belakang piksel inti).
     */
    fun draw(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        unit: Int,
        face: Int,
        paint: Paint,
        inflate: Float = 0f
    ) {
        if (text.isEmpty()) return
        val step = advance(unit)
        var cx = x
        val up = text.uppercase()
        for (element in up) {
            if (element != ' ') drawChar(canvas, element, cx, y, unit, face, paint, inflate)
            cx += step
        }
    }

    private fun drawChar(
        canvas: Canvas,
        ch: Char,
        cx: Float,
        cy: Float,
        unit: Int,
        face: Int,
        paint: Paint,
        inflate: Float
    ) {
        if (face == FACE_LED) {
            val seg = LED[ch]
            if (seg != null) {
                drawSegments(canvas, seg, cx, cy, unit, paint, inflate)
                return
            }
        }
        val rows = GLYPHS[ch] ?: GLYPHS['?'] ?: return
        for (row in 0 until H) {
            val bits = rows[row]
            if (bits == 0) continue
            val top = cy + row * unit
            for (col in 0 until W) {
                if ((bits shr (W - 1 - col)) and 1 == 1) {
                    val left = cx + col * unit
                    canvas.drawRect(
                        left - inflate,
                        top - inflate,
                        left + unit + inflate,
                        top + unit + inflate,
                        paint
                    )
                }
            }
        }
    }

    private fun drawSegments(
        canvas: Canvas,
        seg: Int,
        cx: Float,
        cy: Float,
        unit: Int,
        paint: Paint,
        inflate: Float
    ) {
        fun rect(l: Int, t: Int, w: Int, h: Int) {
            canvas.drawRect(
                cx + l * unit - inflate,
                cy + t * unit - inflate,
                cx + (l + w) * unit + inflate,
                cy + (t + h) * unit + inflate,
                paint
            )
        }
        if (seg and SA != 0) rect(1, 0, 3, 1)
        if (seg and SF != 0) rect(0, 1, 1, 2)
        if (seg and SB != 0) rect(4, 1, 1, 2)
        if (seg and SG != 0) rect(1, 3, 3, 1)
        if (seg and SE != 0) rect(0, 4, 1, 2)
        if (seg and SC != 0) rect(4, 4, 1, 2)
        if (seg and SD != 0) rect(1, 6, 3, 1)
    }
}
