package dev.retrobit.od.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import dev.retrobit.od.R
import dev.retrobit.od.ambient.AmbientController
import dev.retrobit.od.data.CardStore
import dev.retrobit.od.data.IdCard
import dev.retrobit.od.data.Prefs

/**
 * Editor kartu ASN: field teks, pas foto, gambar kartu utuh, dan isi QR.
 * Semuanya disimpan lokal (SharedPreferences + URI persistable), tanpa jaringan.
 */
class CardActivity : AppCompatActivity() {

    private val prefs by lazy { Prefs.get(this) }
    private var photoUri = ""
    private var imageUri = ""

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { onPicked(it, true) }
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { onPicked(it, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card)

        val card = IdCard.fromJson(prefs.cardJson)
        photoUri = card.photoUri
        imageUri = card.imageUri
        set(R.id.edtName, card.name)
        set(R.id.edtNip, card.nip)
        set(R.id.edtRank, card.rank)
        set(R.id.edtPosition, card.position)
        set(R.id.edtUnit, card.unit)
        set(R.id.edtInstitution, card.institution)
        set(R.id.edtQr, card.qr)
        set(R.id.edtNote, card.note)

        val switch = findViewById<SwitchCompat>(R.id.switchCard)
        switch.isChecked = prefs.cardEnabled
        switch.setOnCheckedChangeListener { _, checked -> prefs.cardEnabled = checked }

        val modeGroup = findViewById<MaterialButtonToggleGroup>(R.id.groupCardMode)
        modeGroup.check(if (prefs.cardUseImage) R.id.btnModeImage else R.id.btnModeData)
        modeGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) prefs.cardUseImage = id == R.id.btnModeImage
        }

        findViewById<MaterialButton>(R.id.btnPickPhoto).setOnClickListener {
            pickPhoto.launch(arrayOf("image/*"))
        }
        findViewById<MaterialButton>(R.id.btnClearPhoto).setOnClickListener {
            photoUri = ""
            renderThumbs()
        }
        findViewById<MaterialButton>(R.id.btnPickCard).setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }
        findViewById<MaterialButton>(R.id.btnClearCard).setOnClickListener {
            imageUri = ""
            renderThumbs()
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save(close = true) }
        findViewById<MaterialButton>(R.id.btnSavePreview).setOnClickListener { save(close = false) }

        renderThumbs()
    }

    private fun onPicked(uri: Uri?, photo: Boolean) {
        if (uri == null) return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (photo) photoUri = uri.toString() else imageUri = uri.toString()
        renderThumbs()
    }

    private fun renderThumbs() {
        val photo = findViewById<ImageView>(R.id.imgPhoto)
        val card = findViewById<ImageView>(R.id.imgCard)
        photo.setImageBitmap(
            if (photoUri.isBlank()) null else CardStore.decodeDownsampled(this, photoUri, 320)
        )
        card.setImageBitmap(
            if (imageUri.isBlank()) null else CardStore.decodeDownsampled(this, imageUri, 480)
        )
    }

    private fun save(close: Boolean) {
        val card = IdCard(
            name = read(R.id.edtName),
            nip = read(R.id.edtNip),
            rank = read(R.id.edtRank),
            position = read(R.id.edtPosition),
            unit = read(R.id.edtUnit),
            institution = read(R.id.edtInstitution),
            note = read(R.id.edtNote),
            qr = read(R.id.edtQr),
            photoUri = photoUri,
            imageUri = imageUri
        )
        if (!card.hasAnything()) {
            Toast.makeText(this, R.string.card_hint, Toast.LENGTH_SHORT).show()
        }
        prefs.cardJson = card.toJson()
        CardStore.invalidate()
        prefs.cardEnabled = true
        if (!close) {
            prefs.ambientEnabled = true
            AmbientController.sync(this)
            AmbientController.revealCard(this, 18)
            AmbientController.previewNow(this, 15)
        } else {
            Toast.makeText(this, R.string.card_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun set(id: Int, value: String) {
        findViewById<TextInputEditText>(id).setText(value)
    }

    private fun read(id: Int): String =
        findViewById<TextInputEditText>(id).text?.toString()?.trim().orEmpty()
}
