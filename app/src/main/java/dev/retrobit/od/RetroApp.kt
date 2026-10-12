package dev.retrobit.od

import android.app.Application
import dev.retrobit.od.ambient.AmbientController
import dev.retrobit.od.data.Prefs
import dev.retrobit.od.util.Channels

class RetroApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Channels.ensure(this)
        Prefs.get(this)
        // Pulihkan layanan setelah restart / update (BootReceiver menangani BOOT_COMPLETED,
        // ini untuk kasus proses di-restart oleh sistem).
        AmbientController.sync(this)
    }
}
