package com.gamesmith.assistantapp.ui.toolproxy

import android.app.Activity
import android.content.Intent

interface ToolProxyHandler {
    fun canHandle(action: String): Boolean
    fun start(activity: Activity, intent: Intent, onResult: (Intent) -> Unit)
} 