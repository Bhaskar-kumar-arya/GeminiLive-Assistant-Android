package com.gamesmith.assistantapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamesmith.assistantapp.service.GeminiAssistantService
import com.gamesmith.assistantapp.ui.theme.AssistantappTheme
import com.gamesmith.assistantapp.ui.OverlayPermissionScreen
import com.gamesmith.assistantapp.ui.ServiceControlScreen
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.setValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the GeminiAssistantService
        val serviceIntent = Intent(this, GeminiAssistantService::class.java)
        startService(serviceIntent)

        setContent {
            AssistantappTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    var screen by remember { mutableStateOf(0) } // 0 = ServiceControl, 1 = OverlayPermission
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(onClick = { screen = 0 }) { Text("Service Control") }
                            Button(onClick = { screen = 1 }) { Text("Overlay Permission") }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        when (screen) {
                            0 -> ServiceControlScreen()
                            1 -> OverlayPermissionScreen()
                        }
                    }
                }
            }
        }
    }
}