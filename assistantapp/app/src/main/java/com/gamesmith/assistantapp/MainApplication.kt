package com.gamesmith.assistantapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {
    // You can add custom application-level logic here if needed
    override fun onCreate() {
        super.onCreate()
        // Initialization code here
    }
} 