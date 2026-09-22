package com.igor.fridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.igor.fridge.ui.navigation.IgorApp
import com.igor.fridge.ui.theme.IgorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IgorTheme {
                IgorApp()
            }
        }
    }
}
