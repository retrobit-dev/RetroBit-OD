package dev.retrobit.od.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.retrobit.od.R
import dev.retrobit.od.ambient.AmbientController

/**
 * Tile Quick Settings: satu ketukan = AOD nyala/mati. Saat AOD tampil, ketukan kedua
 * membuka buram kartu selama 20 detik (praktis buat tap-in/scan di gate kantor).
 */
class AmbientTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        val prefs = AmbientController.prefs(this)
        if (prefs.ambientEnabled) {
            // sudah nyala → buka kartu sebentar; kalau sudah terbuka, matikan AOD
            val revealLeft = prefs.cardRevealUntil - System.currentTimeMillis()
            if (prefs.cardMasked && revealLeft <= 0L && prefs.cardEnabled) {
                AmbientController.revealCard(this, 20)
            } else {
                prefs.ambientEnabled = false
                AmbientController.sync(this)
            }
        } else {
            prefs.ambientEnabled = true
            AmbientController.sync(this)
        }
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val prefs = AmbientController.prefs(this)
        tile.state = if (prefs.ambientEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(
            if (prefs.ambientEnabled) R.string.tile_on else R.string.tile_off
        )
        tile.updateTile()
    }
}
