package com.github.muelli.kabelwacht

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.muelli.kabelwacht.ui.nav.KabelWachtNavHost
import com.github.muelli.kabelwacht.ui.theme.KabelWachtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KabelWachtTheme {
                KabelWachtNavHost()
            }
        }
    }
}
