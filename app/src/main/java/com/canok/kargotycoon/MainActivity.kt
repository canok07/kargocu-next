package com.canok.kargotycoon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.canok.kargotycoon.ui.KargoTheme
import com.canok.kargotycoon.ui.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KargoTheme {
                WelcomeScreen()
            }
        }
    }
}
