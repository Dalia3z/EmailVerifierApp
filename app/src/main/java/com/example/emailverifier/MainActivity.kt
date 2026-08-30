package com.example.emailverifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.emailverifier.ui.MainScreen
import com.example.emailverifier.ui.theme.EmailVerifierTheme

/**
 * Single-activity app: the whole UI is Jetpack Compose (see ui/MainScreen.kt).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmailVerifierTheme {
                MainScreen()
            }
        }
    }
}
