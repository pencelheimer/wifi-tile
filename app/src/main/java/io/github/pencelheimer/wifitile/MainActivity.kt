package io.github.pencelheimer.wifitile

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import eu.darken.porter.sdk.PermissionState
import eu.darken.porter.sdk.Porter
import eu.darken.porter.sdk.PorterAvailability
import eu.darken.porter.sdk.PorterConnection
import eu.darken.porter.sdk.PorterConnectionState
import eu.darken.porter.sdk.PorterException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** What Porter needs from the user; a null [onAction] disables the button, a null [action] hides it. */
    private class Setup(val status: String, val action: String? = null, val onAction: (() -> Unit)? = null)

    private var setup by mutableStateOf(Setup("Checking Porter…"))
    private var tileAdded by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Porter.state.collectLatest { state ->
                    if (state !is PorterConnectionState.Connected) {
                        setup = describe(availability())
                        return@collectLatest
                    }
                    try {
                        state.connection.checkPermission()
                    } catch (e: PorterException) {
                    }
                    state.connection.permission.collect { setup = describe(state.connection, it) }
                }
            }
        }

        setContent {
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Wi-Fi Tile", style = MaterialTheme.typography.displaySmall)
                        Text(setup.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        setup.action?.let { action ->
                            val onAction = setup.onAction
                            FilledTonalButton(
                                onClick = { onAction?.invoke() },
                                enabled = onAction != null,
                                shapes = ButtonDefaults.shapes(),
                            ) { Text(action) }
                        }
                        Button(onClick = ::addTile, enabled = !tileAdded, shapes = ButtonDefaults.shapes()) {
                            Text(if (tileAdded) "Tile added" else "Add tile")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        tileAdded = WifiTileService.isAdded(this)
    }

    private suspend fun availability(): PorterAvailability? = try {
        Porter.availability(this)
    } catch (e: PorterException) {
        null
    }

    private fun describe(availability: PorterAvailability?) = when (availability) {
        PorterAvailability.NotInstalled -> Setup("Porter is not installed", "Get Porter") { open(Intent(Intent.ACTION_VIEW, Uri.parse(SETUP_URL))) }
        is PorterAvailability.InstalledNotConnected -> Setup("Porter is not running", "Open Porter") { openApp(availability.packageName) }
        is PorterAvailability.InstalledUnrecognized -> Setup("An unrecognized app holds Porter's permission")
        is PorterAvailability.Incompatible ->
            if (availability.incompatibility.serverTooOld) {
                Setup("Porter needs an update", "Open Porter", availability.packageName?.let { pkg -> { openApp(pkg) } })
            } else {
                Setup("Wi-Fi Tile needs an update to work with this Porter")
            }
        is PorterAvailability.Connected, null -> Setup("Connecting to Porter…")
    }

    private fun describe(connection: PorterConnection, permission: PermissionState) = when (permission) {
        PermissionState.Granted -> Setup("Ready", "Porter granted")
        is PermissionState.Denied ->
            if (permission.permanentlyDenied) {
                Setup("Access denied, allow Wi-Fi Tile in Porter")
            } else {
                Setup("Porter permission required", "Grant Porter") {
                    lifecycleScope.launch {
                        try {
                            connection.requestPermission() // the result arrives on connection.permission
                        } catch (e: PorterException) { // includes Porter restarting while the dialog was up
                        }
                    }
                }
            }
    }

    private fun openApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let(::open)
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }
    }

    private fun addTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, WifiTileService::class.java),
            "Wi-Fi",
            Icon.createWithResource(this, R.drawable.ic_wifi),
            mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                WifiTileService.setAdded(this, true)
                refresh()
            }
        }
    }

    private companion object {
        const val SETUP_URL = "https://porter.darken.eu/setup"
    }
}
