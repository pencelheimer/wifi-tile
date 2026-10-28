package io.github.pencelheimer.wifitile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.util.concurrent.Executors

class WifiTileService : TileService() {
    private val wifi by lazy { getSystemService(WifiManager::class.java) }
    private val worker = Executors.newSingleThreadExecutor()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onTileAdded() = setAdded(this, true)

    override fun onTileRemoved() = setAdded(this, false)

    override fun onStartListening() {
        setAdded(this, true)
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun onStopListening() = unregisterReceiver(receiver)

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    override fun onClick() {
        if (!Shell.ready) return refresh()
        val enable = !wifi.isWifiEnabled
        render(if (enable) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE, "Wi-Fi", if (enable) "Turning on…" else "Off")
        worker.execute { Shell.setWifi(enable) }
    }

    private fun refresh() {
        if (!Shell.ready) return render(Tile.STATE_UNAVAILABLE, "Wi-Fi", "Shizuku needed")
        if (!wifi.isWifiEnabled) return render(Tile.STATE_INACTIVE, "Wi-Fi", "Off")
        worker.execute {
            val ssid = runCatching { Shell.ssid() }.getOrNull()
            mainExecutor.execute { render(Tile.STATE_ACTIVE, ssid ?: "Wi-Fi", if (ssid == null) "Not connected" else "Connected") }
        }
    }

    private fun render(state: Int, label: String, subtitle: String) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = label
        tile.subtitle = subtitle
        tile.updateTile()
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("tile", MODE_PRIVATE)

        fun isAdded(context: Context) = prefs(context).getBoolean("added", false)

        fun setAdded(context: Context, added: Boolean) = prefs(context).edit().putBoolean("added", added).apply()
    }
}
