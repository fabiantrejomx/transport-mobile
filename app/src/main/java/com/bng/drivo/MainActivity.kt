package com.bng.drivo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.bng.drivo.data.SettingsRepository
import com.bng.drivo.ui.MapScreen
import com.bng.drivo.ui.theme.DrivoTheme

class MainActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme by settingsRepository.isDarkTheme.collectAsState(initial = false)
            DrivoTheme(darkTheme = isDarkTheme) {
                MapScreen(
                    settingsRepository = settingsRepository,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
