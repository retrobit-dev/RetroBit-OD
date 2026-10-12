package dev.retrobit.od.ambient

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import dev.retrobit.od.R
import dev.retrobit.od.data.Prefs
import dev.retrobit.od.system.PowerWatch
import dev.retrobit.od.system.ProximityWatch
import dev.retrobit.od.ui.MainActivity
import dev.retrobit.od.util.BrightnessGuard
import dev.retrobit.od.util.Channels

/**
 * Layanan latar depan yang menjaga keputusan AOD: mendengarkan layar mati/nyala,
 * menampilkan [AmbientView] lewat overlay window (mode default) atau meminta
 * [AmbientActivity] (mode kompatibilitas XOS), dan mengurus brightness/timeout.
 */
class AmbientService : Service() {

    companion object {
        const val ACTION_SHOW = "dev.retrobit.od.action.SHOW"
        const val ACTION_HIDE = "dev.retrobit.od.action.HIDE"
        const val ACTION_REFRESH = "dev.retrobit.od.action.REFRESH"
        const val ACTION_STOP = "dev.retrobit.od.action.STOP"
        const val EXTRA_SECONDS = "seconds"

        private const val WAKE_DELAY_MS = 700L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { Prefs.get(this) }

    private var windowManager: WindowManager? = null
    private var overlay: AmbientView? = null

    private var showing = false
    private var armed = true
    private var selfWake = false
    private var ignoreRulesUntil = 0L

    private val autoHide = Runnable { hide(userDismiss = false) }
    private val showTask = Runnable { show(ignoreRulesUntil > System.currentTimeMillis()) }
    private val revealExpire = Runnable { refreshVisible() }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        onPrefsChanged()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_USER_PRESENT -> {
                    hide(userDismiss = false)
                    armed = true
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    PowerWatch.invalidateCache()
                    if (showing) checkGuards()
                }
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> if (showing) checkGuards()
                ACTION_HIDE -> hide(userDismiss = false)
                ACTION_REFRESH -> refreshVisible()
                ACTION_STOP -> {
                    prefs.ambientEnabled = false
                    AmbientController.sync(this@AmbientService)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ProximityWatch.refreshSupported(this)
        if (prefs.proximityGuard) ProximityWatch.start(this)
        prefs.addListener(prefsListener)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(ACTION_HIDE)
            addAction(ACTION_REFRESH)
            addAction(ACTION_STOP)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_SHOW -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 10).coerceIn(3, 120)
                ignoreRulesUntil = System.currentTimeMillis() + seconds * 1000L
                handler.removeCallbacks(autoHide)
                show(force = true)
            }
            ACTION_HIDE -> hide(userDismiss = false)
            ACTION_REFRESH -> {
                handler.postDelayed(revealExpire, 21_000L)
                refreshVisible()
            }
        }
        if (!prefs.ambientEnabled && !showing) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        maybePromptPermission()
        updateStatus()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoHide)
        handler.removeCallbacks(showTask)
        handler.removeCallbacks(revealExpire)
        prefs.removeListener(prefsListener)
        runCatching { unregisterReceiver(screenReceiver) }
        detachOverlay()
        BrightnessGuard.restore(this)
        ProximityWatch.stop()
        super.onDestroy()
    }

    // ── logika tampil ────────────────────────────────────────────────────────

    private fun onScreenOff() {
        handler.removeCallbacks(showTask)
        if (showing) {
            // user menekan tombol power saat AOD tampil = minta tidur penuh
            hide(userDismiss = true)
            return
        }
        if (!prefs.ambientEnabled || prefs.renderMode == Prefs.MODE_DREAM) return
        if (!armed) return
        handler.postDelayed(showTask, WAKE_DELAY_MS)
    }

    private fun onScreenOn() {
        if (selfWake) {
            selfWake = false
            return
        }
        if (!showing) armed = true
    }

    private fun show(force: Boolean = false) {
        if (showing) return
        if (!force && AmbientController.blockReason(this, prefs) != null) return
        if (force && prefs.renderMode == Prefs.MODE_OVERLAY && !AmbientController.isOverlayAllowed(this)) {
            maybePromptPermission()
            return
        }
        showing = true
        selfWake = true
        BrightnessGuard.apply(this, prefs)
        if (prefs.renderMode == Prefs.MODE_ACTIVITY) {
            val intent = Intent(this, AmbientActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            runCatching { startActivity(intent) }.onFailure {
                // kalau ROM menolak start-from-background, jatuh ke overlay bila bisa
                if (!attachOverlay()) hide(userDismiss = false)
                return
            }
        } else if (!attachOverlay()) {
            hide(userDismiss = false)
            return
        }
        val duration = when {
            force -> (ignoreRulesUntil - System.currentTimeMillis()).coerceAtLeast(1_000L)
            prefs.peekMode -> prefs.peekSeconds.coerceIn(3, 300) * 1000L
            else -> 0L
        }
        if (duration > 0L) handler.postDelayed(autoHide, duration)
        updateStatus()
    }

    private fun hide(userDismiss: Boolean) {
        handler.removeCallbacks(autoHide)
        handler.removeCallbacks(showTask)
        val wasShowing = showing
        showing = false
        if (userDismiss) armed = false
        detachOverlay()
        BrightnessGuard.restore(this)
        if (wasShowing) {
            runCatching { sendBroadcast(Intent(ACTION_HIDE).setPackage(packageName)) }
            updateStatus()
        }
    }

    private fun checkGuards() {
        if (AmbientController.blockReason(this, prefs) != null) hide(userDismiss = false)
    }

    private fun onPrefsChanged() {
        if (prefs.proximityGuard) ProximityWatch.start(this) else ProximityWatch.stop()
        if (showing) {
            checkGuards()
            refreshVisible()
        }
        updateStatus()
    }

    private fun refreshVisible() {
        overlay?.refreshNow()
        runCatching { sendBroadcast(Intent(ACTION_REFRESH).setPackage(packageName)) }
    }

    // ── jendela ──────────────────────────────────────────────────────────────

    private fun attachOverlay(): Boolean {
        if (overlay != null) return true
        val view = AmbientView(this)
        val lp = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            if (!prefs.forceBrightness) {
                screenBrightness = (prefs.brightnessPercent / 100f).coerceIn(0f, 1f)
            }
            windowAnimations = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        }
        return try {
            windowManager?.addView(view, lp)
            overlay = view
            true
        } catch (t: Throwable) {
            android.util.Log.w(AmbientController.TAG, "overlay ditolak", t)
            view.pause()
            overlay = null
            false
        }
    }

    private fun detachOverlay() {
        val view = overlay ?: return
        overlay = null
        view.pause()
        try {
            windowManager?.removeView(view)
        } catch (t: Throwable) {
            android.util.Log.w(AmbientController.TAG, "removeView gagal", t)
        }
    }

    // ── notifikasi ───────────────────────────────────────────────────────────

    private fun goForeground() {
        val notification = buildStatus()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                Channels.ID_ONGOING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Channels.ID_ONGOING, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildStatus(): Notification {
        val open = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getBroadcast(
            this,
            11,
            Intent(ACTION_STOP).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val reason = AmbientController.blockReason(this, prefs)
        val text = if (reason == null) {
            if (prefs.peekMode) {
                getString(R.string.notif_peek, prefs.peekSeconds)
            } else {
                getString(R.string.notif_always)
            }
        } else {
            getString(reason)
        }
        return NotificationCompat.Builder(this, Channels.AMBIENT)
            .setSmallIcon(R.drawable.ic_stat_ambient)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(prefs.accentColor)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_stat_ambient,
                    getString(R.string.notif_action_open),
                    open
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_stat_ambient,
                    getString(R.string.notif_action_stop),
                    stop
                ).build()
            )
            .build()
    }

    private fun updateStatus() {
        notificationManager().notify(Channels.ID_ONGOING, buildStatus())
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun maybePromptPermission() {
        val nm = notificationManager()
        val needed = prefs.renderMode == Prefs.MODE_OVERLAY && !AmbientController.isOverlayAllowed(this)
        if (!needed) {
            nm.cancel(Channels.ID_PERMISSION)
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            this,
            12,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, Channels.PERMISSION)
            .setSmallIcon(R.drawable.ic_stat_ambient)
            .setContentTitle(getString(R.string.notif_perm_overlay))
            .setContentText(getString(R.string.notif_perm_overlay_body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(Color.parseColor("#FFB000"))
            .build()
        nm.notify(Channels.ID_PERMISSION, n)
    }
}
