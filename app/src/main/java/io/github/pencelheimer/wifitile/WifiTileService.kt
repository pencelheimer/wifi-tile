package io.github.pencelheimer.wifitile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import eu.darken.porter.sdk.Porter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WifiTileService : TileService() {
    private val wifi by lazy { getSystemService(WifiManager::class.java) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null
    private var refreshing: Job? = null
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
        // The connection can arrive after SystemUI binds the tile, so re-render when it does
        listening = scope.launch { Porter.state.collect { refresh() } }
    }

    override fun onStopListening() {
        listening?.cancel()
        unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        scope.launch {
            if (!Shell.ready()) return@launch refresh()
            val enable = !wifi.isWifiEnabled
            render(if (enable) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE, "Wi-Fi", if (enable) "Turning on…" else "Off")
            runCatching { Shell.setWifi(enable) }.onFailure { refresh() }
        }
    }

    private fun refresh() {
        refreshing?.cancel()
        refreshing = scope.launch {
            if (!Shell.ready()) return@launch render(Tile.STATE_UNAVAILABLE, "Wi-Fi", "Porter needed")
            if (!wifi.isWifiEnabled) return@launch render(Tile.STATE_INACTIVE, "Wi-Fi", "Off")
            val ssid = runCatching { Shell.ssid() }.getOrNull()
            render(Tile.STATE_ACTIVE, ssid ?: "Wi-Fi", if (ssid == null) "Not connected" else "Connected")
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
