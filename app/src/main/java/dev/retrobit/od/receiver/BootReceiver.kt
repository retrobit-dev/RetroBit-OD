package dev.retrobit.od.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.retrobit.od.ambient.AmbientController

/** Nyalakan lagi layanan AOD setelah HP dinyalakan ulang / aplikasi di-update. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AmbientController.sync(context)
    }
}
