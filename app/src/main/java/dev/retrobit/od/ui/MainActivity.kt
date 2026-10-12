package dev.retrobit.od.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import dev.retrobit.od.R
import dev.retrobit.od.ambient.AmbientController
import dev.retrobit.od.data.Prefs
import dev.retrobit.od.service.NotificationMonitor

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { Prefs.get(this) }

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    private val writeSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    private val batteryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val master = findViewById<SwitchCompat>(R.id.switchMaster)
        master.isChecked = prefs.ambientEnabled
        master.setOnCheckedChangeListener { _, checked ->
            prefs.ambientEnabled = checked
            if (checked) requestNotificationPermissionIfNeeded()
            AmbientController.sync(this)
            refresh()
        }

        findViewById<MaterialButton>(R.id.btnPermOverlay).setOnClickListener {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<MaterialButton>(R.id.btnPermBattery).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                batteryLauncher.launch(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        findViewById<MaterialButton>(R.id.btnPermNotif).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.btnPermWrite).setOnClickListener {
            writeSettingsLauncher.launch(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        findViewById<MaterialButton>(R.id.btnPermAutostart).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }

        findViewById<MaterialButton>(R.id.btnStyle).setOnClickListener {
            startActivity(Intent(this, StyleActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnCard).setOnClickListener {
            startActivity(Intent(this, CardActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnAutomation).setOnClickListener {
            startActivity(Intent(this, AutomationActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener {
            if (prefs.renderMode == Prefs.MODE_OVERLAY && !AmbientController.isOverlayAllowed(this)) {
                overlayLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                prefs.ambientEnabled = true
                master.isChecked = true
                AmbientController.sync(this)
                AmbientController.previewNow(this, 10)
            }
        }
        findViewById<MaterialButton>(R.id.btnReveal).setOnClickListener {
            AmbientController.revealCard(this, 20)
            AmbientController.previewNow(this, 25)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refresh() {
        val reason = AmbientController.blockReason(this, prefs)
        val status = findViewById<TextView>(R.id.txtStatus)
        status.text = when {
            !prefs.ambientEnabled -> getString(R.string.status_stopped)
            reason != null -> getString(reason)
            else -> getString(R.string.status_will_show)
        }
        status.setTextColor(
            if (prefs.ambientEnabled && reason == null) getColor(R.color.phosphor)
            else getColor(R.color.amber)
        )

        setPerm(R.id.txtPermOverlay, getString(
            if (AmbientController.isOverlayAllowed(this)) R.string.overlay_on else R.string.overlay_off
        ))

        val ignoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (getSystemService(POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(packageName) == true
        } else {
            true
        }
        setPerm(R.id.txtPermBattery, getString(
            if (ignoring) R.string.battery_opt_on else R.string.battery_opt_off
        ))

        val listenerOn = notificationAccessGranted()
        NotificationMonitor.granted = listenerOn
        setPerm(
            R.id.txtPermNotif,
            if (listenerOn) {
                getString(R.string.notif_on, NotificationMonitor.activeCount)
            } else {
                getString(R.string.notif_off)
            }
        )

        setPerm(R.id.txtPermWrite, getString(
            if (Settings.System.canWrite(this)) R.string.write_settings_on else R.string.write_settings_off
        ))
    }

    private fun setPerm(id: Int, text: CharSequence) {
        findViewById<TextView>(id).text = text
    }

    private fun notificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.split(":")?.any { it.contains(packageName) } == true
    }
}
