package com.gamesmith.assistantapp.di

import android.content.Context
import com.gamesmith.assistantapp.data.remote.GeminiWebSocketClient
import com.gamesmith.assistantapp.data.webrtc.WebRTCManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebRTCModule {
    @Provides
    @Singleton
    fun provideWebRTCManager(
        @ApplicationContext context: Context,
        geminiWebSocketClient: GeminiWebSocketClient,
        json: Json
    ): WebRTCManager {
        return WebRTCManager(context, geminiWebSocketClient, json)
    }
} 