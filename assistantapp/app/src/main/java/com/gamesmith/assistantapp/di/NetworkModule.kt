package com.gamesmith.assistantapp.di

import com.gamesmith.assistantapp.data.remote.GeminiRepository
import com.gamesmith.assistantapp.data.remote.GeminiRepositoryImpl
import com.gamesmith.assistantapp.data.remote.GeminiWebSocketClient
import com.gamesmith.assistantapp.data.model.*
import com.gamesmith.assistantapp.util.AudioPlayer
import com.gamesmith.assistantapp.util.AndroidAudioPlayer
import dagger.Module
import dagger.Provides
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.gamesmith.assistantapp.data.webrtc.WebRTCManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(60, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS) // Keep the WebSocket connection alive
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true // Important for sending complete default objects if needed
        }
    }

    @Provides
    @Singleton
    fun provideGeminiWebSocketClient(
        okHttpClient: OkHttpClient,
        json: Json
    ): GeminiWebSocketClient {
        return GeminiWebSocketClient(okHttpClient, json)
    }

    @Provides
    @Singleton
    fun provideGeminiRepository(
        geminiWebSocketClient: GeminiWebSocketClient,
        json: Json,
        webRTCManager: WebRTCManager
    ): GeminiRepository {
        return GeminiRepositoryImpl(geminiWebSocketClient, json, webRTCManager)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    
    /**
     * Binds AudioPlayer interface to AndroidAudioPlayer implementation.
     * Follows Dependency Inversion Principle - depend on abstractions.
     */
    @Binds
    @Singleton
    abstract fun bindAudioPlayer(
        androidAudioPlayer: AndroidAudioPlayer
    ): AudioPlayer
} 