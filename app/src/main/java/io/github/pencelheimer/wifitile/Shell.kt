package io.github.pencelheimer.wifitile

import eu.darken.porter.sdk.Porter
import eu.darken.porter.sdk.PorterException
import eu.darken.porter.sdk.isGranted
import eu.darken.porter.sdk.extras.exec
import kotlinx.coroutines.withTimeout

object Shell {
    suspend fun ready(): Boolean {
        val connection = Porter.connection.value ?: return false
        return try {
            connection.checkPermission().isGranted
        } catch (e: PorterException) {
            false
        }
    }

    private suspend fun run(vararg cmd: String): String {
        val connection = checkNotNull(Porter.connection.value) { "Porter is not connected" }
        return withTimeout(5_000) { connection.exec(*cmd) }.output
    }

    suspend fun setWifi(enabled: Boolean) =
        run("svc", "wifi", if (enabled) "enable" else "disable")

    suspend fun ssid(): String? =
        Regex("""connected to "(.+?)"""").find(run("cmd", "wifi", "status"))?.groupValues?.get(1)
}
