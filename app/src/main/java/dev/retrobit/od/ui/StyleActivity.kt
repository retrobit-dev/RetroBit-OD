package dev.retrobit.od.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import dev.retrobit.od.R
import dev.retrobit.od.ambient.AmbientController
import dev.retrobit.od.ambient.AmbientView
import dev.retrobit.od.data.Prefs

class StyleActivity : AppCompatActivity() {

    private val prefs by lazy { Prefs.get(this) }
    private var preview: AmbientView? = null
    private val swatches = ArrayList<Pair<View, Int>>()

    private val palette = intArrayOf(
        0xFF7CFFA0.toInt(), // phosphor hijau
        0xFFFFB000.toInt(), // amber CRT
        0xFF54E0FF.toInt(), // cyan
        0xFFFF5FD0.toInt(), // magenta
        0xFFFFFFFF.toInt(), // putih
        0xFFFF5A5A.toInt()  // merah
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_style)

        setupPreview()
        bindMode()
        bindGroups()
        bindColors()
        bindSwitches()
        updateModeHint()

        findViewById<MaterialButton>(R.id.btnDreamSettings).setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }
                .onFailure { openDisplaySettings() }
        }
    }

    private fun setupPreview() {
        val host = findViewById<FrameLayout>(R.id.previewHost)
        val view = AmbientView(this, null, true)
        preview = view
        host.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        host.post {
            val lp = host.layoutParams
            lp.height = (host.width * 2720f / 1224f).toInt()
            host.layoutParams = lp
        }
    }

    private fun bindMode() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.groupMode)
        group.check(
            when (prefs.renderMode) {
                Prefs.MODE_ACTIVITY -> R.id.btnModeActivity
                Prefs.MODE_DREAM -> R.id.btnModeDream
                else -> R.id.btnModeOverlay
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                prefs.renderMode = when (checkedId) {
                    R.id.btnModeActivity -> Prefs.MODE_ACTIVITY
                    R.id.btnModeDream -> Prefs.MODE_DREAM
                    else -> Prefs.MODE_OVERLAY
                }
                updateModeHint()
                AmbientController.sync(this)
            }
        }
    }

    private fun bindGroups() {
        val layout = findViewById<MaterialButtonToggleGroup>(R.id.groupLayout)
        layout.check(
            when (prefs.layoutPreset) {
                Prefs.LAYOUT_CLOCK_CENTER -> R.id.btnLayoutCenter
                Prefs.LAYOUT_CARD_FULL -> R.id.btnLayoutCard
                else -> R.id.btnLayoutTop
            }
        )
        layout.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                prefs.layoutPreset = when (id) {
                    R.id.btnLayoutCenter -> Prefs.LAYOUT_CLOCK_CENTER
                    R.id.btnLayoutCard -> Prefs.LAYOUT_CARD_FULL
                    else -> Prefs.LAYOUT_CLOCK_TOP
                }
                preview?.refreshNow()
            }
        }

        val font = findViewById<MaterialButtonToggleGroup>(R.id.groupFont)
        font.check(if (prefs.fontFace == Prefs.FONT_LED) R.id.btnFontLed else R.id.btnFontPixel)
        font.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                prefs.fontFace = if (id == R.id.btnFontLed) Prefs.FONT_LED else Prefs.FONT_PIXEL
                preview?.refreshNow()
            }
        }

        val scale = findViewById<MaterialButtonToggleGroup>(R.id.groupScale)
        val scaleIds = intArrayOf(R.id.btnScaleS, R.id.btnScaleM, R.id.btnScaleL, R.id.btnScaleXL)
        scale.check(scaleIds[prefs.clockScale.coerceIn(0, scaleIds.size - 1)])
        scale.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                val index = scaleIds.indexOf(id)
                if (index >= 0) {
                    prefs.clockScale = index
                    preview?.refreshNow()
                }
            }
        }
    }

    private fun bindColors() {
        val row = findViewById<LinearLayout>(R.id.colorRow)
        row.removeAllViews()
        swatches.clear()
        for (color in palette) {
            val swatch = View(this)
            val params = LinearLayout.LayoutParams(dp(44), dp(44))
            params.marginEnd = dp(8)
            swatch.layoutParams = params
            swatch.setOnClickListener {
                prefs.accentColor = color
                paintSwatches()
                preview?.refreshNow()
            }
            row.addView(swatch)
            swatches.add(swatch to color)
        }
        paintSwatches()
    }

    private fun paintSwatches() {
        for ((view, color) in swatches) {
            val selected = color == prefs.accentColor
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(color)
                setStroke(if (selected) dp(3) else dp(1), if (selected) 0xFFFFFFFF.toInt() else 0xFF2A3240.toInt())
            }
        }
    }

    private fun bindSwitches() {
        bind(R.id.switchGlow, { prefs.glow }, { prefs.glow = it })
        bind(R.id.switchScan, { prefs.scanlines }, { prefs.scanlines = it })
        bind(R.id.switchSeconds, { prefs.showSeconds }, { prefs.showSeconds = it })
        bind(R.id.switchDate, { prefs.showDate }, { prefs.showDate = it })
        bind(R.id.switchBattery, { prefs.showBattery }, { prefs.showBattery = it })
        bind(R.id.switchNotif, { prefs.showNotifications }, { prefs.showNotifications = it })
        bind(R.id.switchCardLine, { prefs.showCardLine }, { prefs.showCardLine = it })
    }

    private fun bind(id: Int, getter: () -> Boolean, setter: (Boolean) -> Unit) {
        val switch = findViewById<SwitchCompat>(id)
        switch.isChecked = getter()
        switch.setOnCheckedChangeListener { _, checked ->
            setter(checked)
            if (id == R.id.switchNotif && checked && !notificationAccessGranted()) {
                runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            preview?.refreshNow()
        }
    }

    private fun notificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.split(":")?.any { it.contains(packageName) } == true
    }

    private fun updateModeHint() {
        val hint = findViewById<TextView>(R.id.txtModeHint)
        val dream = findViewById<MaterialButton>(R.id.btnDreamSettings)
        when (prefs.renderMode) {
            Prefs.MODE_ACTIVITY -> {
                hint.setText(R.string.mode_activity_hint)
                dream.visibility = View.GONE
            }
            Prefs.MODE_DREAM -> {
                hint.setText(R.string.mode_dream_hint)
                dream.visibility = View.VISIBLE
            }
            else -> {
                hint.setText(R.string.mode_overlay_hint)
                dream.visibility = View.GONE
            }
        }
    }

    private fun openDisplaySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        preview?.let { view -> (view.parent as? android.view.ViewGroup)?.removeView(view) }
        preview = null
        super.onDestroy()
    }
}
