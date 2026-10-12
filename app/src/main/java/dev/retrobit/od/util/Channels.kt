package dev.retrobit.od.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.retrobit.od.R

object Channels {

    const val AMBIENT = "ambient"
    const val PERMISSION = "permission"

    const val ID_ONGOING = 1001
    const val ID_PERMISSION = 1002

    fun ensure(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ambient = NotificationChannel(
            AMBIENT,
            context.getString(R.string.notif_ch_ambient),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
        }
        val perm = NotificationChannel(
            PERMISSION,
            context.getString(R.string.notif_ch_perm),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { setShowBadge(true) }
        nm.createNotificationChannel(ambient)
        nm.createNotificationChannel(perm)
    }
}
