package com.gamesmith.assistantapp.ui

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import com.gamesmith.assistantapp.service.GeminiAssistantService

@Composable
fun ServiceControlScreen() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Unknown") }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == "com.gamesmith.assistantapp.SERVICE_STATUS") {
                    status = intent.getStringExtra("status") ?: "Unknown"
                }
            }
        }
        val filter = IntentFilter("com.gamesmith.assistantapp.SERVICE_STATUS")
        context.registerReceiver(receiver, filter)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                context.unregisterReceiver(receiver)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Gemini Assistant Service Control",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Status: $status",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { sendServiceAction(context, GeminiAssistantService.ACTION_START_SESSION) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Session")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { sendServiceAction(context, GeminiAssistantService.ACTION_STOP_SESSION) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop Session")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { sendServiceAction(context, GeminiAssistantService.ACTION_TOGGLE_VOICE_SESSION) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Toggle Voice Session")
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Use the buttons above to control the background assistant service.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun sendServiceAction(context: Context, action: String) {
    val intent = Intent(context, GeminiAssistantService::class.java).apply {
        this.action = action
    }
    context.startService(intent)
} 