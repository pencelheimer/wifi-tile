package io.github.pencelheimer.wifitile

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object Shell {
    val ready: Boolean
        get() = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun run(vararg cmd: String): String {
        @Suppress("DEPRECATION") // NOTE(pencelheimer): made private in API 13; API 12.x is pinned to keep calling it directly
        val process = Shizuku.newProcess(cmd, null, null)
        return process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
    }

    fun setWifi(enabled: Boolean) =
        run("svc", "wifi", if (enabled) "enable" else "disable")

    fun ssid(): String? =
        Regex("""connected to "(.+?)"""").find(run("cmd", "wifi", "status"))?.groupValues?.get(1)
}
