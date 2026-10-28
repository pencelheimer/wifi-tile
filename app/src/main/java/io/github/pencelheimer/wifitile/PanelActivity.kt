package io.github.pencelheimer.wifitile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/** Opens the system Internet panel when the tile is long-pressed. */
class PanelActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        finish()
    }
}
