package com.alphasim.brain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphasim.brain.data.prefs.AppSettings
import com.alphasim.brain.ui.AppNav
import com.alphasim.brain.ui.theme.AlphaSimTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AlphaSimApp).container
        container.session.restore()
        setContent {
            val settings by container.prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (settings.theme) {
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> true
            }
            AlphaSimTheme(darkTheme = dark) {
                AppNav(container)
            }
        }
    }
}
