package dev.retrobit.od.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Hanya menghitung jumlah notifikasi aktif (judul/isi tidak pernah disimpan).
 * Izinnya diberikan user lewat Setelan → Akses notifikasi.
 */
class NotificationMonitor : NotificationListenerService() {

    companion object {
        @Volatile
        var activeCount: Int = 0
            private set

        /** true kalau user sudah memberi akses notifikasi (UI cek lewat Settings.Secure,
         *  karena listener bisa belum terikat saat layar dibuka). */
        @Volatile
        var granted: Boolean = false
    }

    override fun onListenerConnected() {
        granted = true
        activeCount = count()
    }

    override fun onListenerDisconnected() {
        granted = false
        activeCount = 0
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        activeCount = count()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        activeCount = count()
    }

    private fun count(): Int = try {
        activeNotifications?.size ?: 0
    } catch (t: Throwable) {
        0
    }
}
