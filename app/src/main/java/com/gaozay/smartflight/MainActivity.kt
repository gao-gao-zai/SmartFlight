package com.gaozay.smartflight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import com.gaozay.smartflight.ui.SmartFlightRoot
import com.gaozay.smartflight.ui.theme.SmartFlightTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val (darkMode, setDarkMode) = rememberSaveable { mutableStateOf(false) }
            SmartFlightTheme(darkTheme = darkMode) {
                SmartFlightRoot(
                    state = viewModel.uiState,
                    darkMode = darkMode,
                    onToggleDarkMode = { setDarkMode(!darkMode) },
                )
            }
        }
    }
}
