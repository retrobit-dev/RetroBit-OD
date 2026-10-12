package dev.retrobit.od.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import org.json.JSONObject
import java.lang.ref.SoftReference
import java.util.HashMap

/**
 * Data kartu ASN yang ditampilkan di AOD. Disimpan sebagai JSON di SharedPreferences
 * aplikasi — tidak pernah dikirim ke mana-mana.
 */
data class IdCard(
    val name: String = "",
    val nip: String = "",
    val rank: String = "",
    val position: String = "",
    val unit: String = "",
    val institution: String = "",
    val note: String = "",
    val qr: String = "",
    val photoUri: String = "",
    val imageUri: String = ""
) {

    fun toJson(): String {
        val o = JSONObject()
        o.put("name", name)
        o.put("nip", nip)
        o.put("rank", rank)
        o.put("position", position)
        o.put("unit", unit)
        o.put("institution", institution)
        o.put("note", note)
        o.put("qr", qr)
        o.put("photoUri", photoUri)
        o.put("imageUri", imageUri)
        return o.toString()
    }

    fun hasAnything(): Boolean =
        name.isNotBlank() || nip.isNotBlank() || rank.isNotBlank() || position.isNotBlank() ||
            unit.isNotBlank() || institution.isNotBlank() || note.isNotBlank() ||
            qr.isNotBlank() || photoUri.isNotBlank() || imageUri.isNotBlank()

    companion object {
        fun fromJson(raw: String): IdCard {
            if (raw.isBlank()) return IdCard()
            return try {
                val o = JSONObject(raw)
                IdCard(
                    name = o.optString("name"),
                    nip = o.optString("nip"),
                    rank = o.optString("rank"),
                    position = o.optString("position"),
                    unit = o.optString("unit"),
                    institution = o.optString("institution"),
                    note = o.optString("note"),
                    qr = o.optString("qr"),
                    photoUri = o.optString("photoUri"),
                    imageUri = o.optString("imageUri")
                )
            } catch (t: Throwable) {
                Log.w(TAG, "kartu rusak, pakai default", t)
                IdCard()
            }
        }

        private const val TAG = "RetroBitCard"
    }
}

/** Cache bitmap foto/kartu & QR supaya renderer tidak decode ulang tiap tick. */
object CardStore {

    private var photoCache: Pair<String, SoftReference<Bitmap>>? = null
    private var imageCache: Pair<String, SoftReference<Bitmap>>? = null
    private var qrCache: Pair<String, SoftReference<Bitmap>>? = null

    fun loadPhoto(context: Context, card: IdCard): Bitmap? =
        cached(context, photoCache, card.photoUri, 640) { photoCache = it }

    fun loadImage(context: Context, card: IdCard): Bitmap? =
        cached(context, imageCache, card.imageUri, 1080) { imageCache = it }

    private inline fun cached(
        context: Context,
        current: Pair<String, SoftReference<Bitmap>>?,
        uriString: String,
        maxDim: Int,
        save: (Pair<String, SoftReference<Bitmap>>) -> Unit
    ): Bitmap? {
        if (uriString.isBlank()) return null
        if (current != null && current.first == uriString) {
            current.second.get()?.let { return it }
        }
        val bmp = decodeDownsampled(context, uriString, maxDim) ?: return null
        save(uriString to SoftReference(bmp))
        return bmp
    }

    fun decodeDownsampled(context: Context, uriString: String, maxDim: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            val biggest = maxOf(bounds.outWidth, bounds.outHeight)
            while (biggest / sample > maxDim * 2) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "gagal decode $uriString", t)
            null
        }
    }

    /** Render QR (monokrom, piksel keras) dari teks arbitrer. */
    fun qrBitmap(text: String): Bitmap? {
        if (text.isBlank()) return null
        val cached = qrCache?.second?.get()
        if (cached != null && qrCache?.first == text) return cached
        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.MARGIN] = 1
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            val requested = 440
            val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, requested, requested, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val offset = y * w
                for (x in 0 until w) {
                    // modul QR = putih penuh, latar transparan (ramah AMOLED, bisa di-tint)
                    pixels[offset + x] = if (matrix.get(x, y)) 0xFFFFFFFF.toInt() else 0x00000000
                }
            }
            val bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
            qrCache = text to SoftReference(bmp)
            bmp
        } catch (t: Throwable) {
            Log.w(TAG, "QR gagal dibuat", t)
            null
        }
    }

    fun invalidate() {
        photoCache = null
        imageCache = null
        qrCache = null
    }

    private const val TAG = "RetroBitCard"
}
