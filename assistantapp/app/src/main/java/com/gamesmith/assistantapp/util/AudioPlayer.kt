package com.gamesmith.assistantapp.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for audio playback functionality.
 * Follows Interface Segregation Principle - only audio playback methods.
 */
interface AudioPlayer {
    /**
     * Plays a chunk of PCM audio data.
     * @param audioData Raw PCM audio bytes (16-bit, 16kHz)
     */
    suspend fun playChunk(audioData: ByteArray)
    
    /**
     * Plays base64 encoded PCM audio data.
     * @param base64AudioData Base64 encoded PCM audio
     */
    suspend fun playBase64Chunk(base64AudioData: String)
    
    /**
     * Replays stored audio data from a list of base64 chunks.
     * @param audioChunks List of base64 encoded PCM audio chunks
     */
    suspend fun replayStoredAudio(audioChunks: List<String>)
    
    /**
     * Stops current audio playback immediately.
     */
    suspend fun stopPlayback()
    
    /**
     * Releases audio resources. Call when done with the player.
     */
    fun release()
    
    /**
     * Returns true if currently playing audio.
     */
    val isPlaying: Boolean
}

/**
 * Android implementation of AudioPlayer using AudioTrack.
 * Follows Single Responsibility Principle - only handles audio playback.
 * Thread-safe implementation with proper resource management.
 * Uses 24kHz sample rate to match console configuration.
 */
@Singleton
class AndroidAudioPlayer @Inject constructor() : AudioPlayer {
    
    companion object {
        private const val TAG = "AndroidAudioPlayer"
        private const val SAMPLE_RATE = 24000 // Match console output sample rate
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 6 // Increased for better 24kHz performance
        private const val INITIAL_BUFFER_TIME_MS = 100 // 100ms initial buffer like console
    }
    
    private var audioTrack: AudioTrack? = null
    private val mutex = Mutex()
    private var playbackJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val audioQueue = mutableListOf<ByteArray>()
    private var isBuffering = true
    
    override val isPlaying: Boolean
        get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
    
    /**
     * Initializes AudioTrack if not already initialized.
     * Follows Open/Closed Principle - can be extended without modification.
     */
    private suspend fun initializeAudioTrack() {
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            releaseAudioTrack()
            
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (minBufferSize == AudioTrack.ERROR_BAD_VALUE || minBufferSize == AudioTrack.ERROR) {
                throw IllegalStateException("Unable to get minimum buffer size for AudioTrack")
            }
            
            val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER
            
            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .setEncoding(AUDIO_FORMAT)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                
                // Reset queue state for new track
                audioQueue.clear()
                isBuffering = true
                
                Log.d(TAG, "AudioTrack initialized with buffer size: $bufferSize (24kHz)")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AudioTrack", e)
                throw e
            }
        }
    }
    
    override suspend fun playChunk(audioData: ByteArray) {
        if (audioData.isEmpty()) {
            Log.w(TAG, "Received empty audio data")
            return
        }
        
        mutex.withLock {
            // Add to queue
            audioQueue.add(audioData)
            
            // Initialize if needed
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                initializeAudioTrack()
            }
            
            // Start buffering process if not already started
            if (isBuffering && playbackJob == null) {
                startBufferedPlayback()
            }
        }
    }
    
    /**
     * Starts buffered playback similar to console implementation.
     * Waits for initial buffer before starting playback.
     */
    private fun startBufferedPlayback() {
        playbackJob = coroutineScope.launch {
            try {
                val track = audioTrack ?: return@launch
                
                // Wait for initial buffer (like console's initialBufferTime)
                delay(INITIAL_BUFFER_TIME_MS.toLong())
                
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                    Log.d(TAG, "AudioTrack buffered playback started")
                }
                isBuffering = false
                
                // Process queue
                while (playbackJob?.isActive == true) {
                    val chunk = mutex.withLock {
                        if (audioQueue.isNotEmpty()) {
                            audioQueue.removeFirst()
                        } else null
                    }
                    
                    chunk?.let { data ->
                        val bytesWritten = track.write(data, 0, data.size)
                        if (bytesWritten < 0) {
                            Log.e(TAG, "Error writing buffered audio data: $bytesWritten")
                        } else {
                            Log.d(TAG, "Buffered write: $bytesWritten bytes")
                        }
                    } ?: delay(10) // Small delay if no data available
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in buffered playback", e)
            }
        }
    }
    
    override suspend fun playBase64Chunk(base64AudioData: String) {
        try {
            val audioData = Base64.decode(base64AudioData, Base64.DEFAULT)
            playChunk(audioData)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid base64 audio data", e)
            throw e
        }
    }
    
    override suspend fun replayStoredAudio(audioChunks: List<String>) {
        if (audioChunks.isEmpty()) {
            Log.w(TAG, "No audio chunks to replay")
            return
        }
        
        Log.d(TAG, "Starting replay of ${audioChunks.size} audio chunks")
        
        // Stop any current playback first
        stopPlayback()
        
        try {
            // Play all chunks sequentially
            for (base64Chunk in audioChunks) {
                playBase64Chunk(base64Chunk)
            }
            Log.d(TAG, "Replay initiated for stored audio")
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio replay", e)
            throw e
        }
    }
    
    override suspend fun stopPlayback() {
        mutex.withLock {
            try {
                playbackJob?.cancel()
                playbackJob = null
                
                // Clear audio queue
                audioQueue.clear()
                isBuffering = true
                
                audioTrack?.let { track ->
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                        track.flush() // Clear any buffered audio
                        Log.d(TAG, "AudioTrack playback stopped, flushed, and queue cleared")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping playback", e)
            }
        }
    }
    
    override fun release() {
        coroutineScope.launch {
            mutex.withLock {
                releaseAudioTrack()
            }
        }
    }
    
    /**
     * Internal method to release AudioTrack resources.
     * Must be called within mutex lock.
     */
    private fun releaseAudioTrack() {
        try {
            playbackJob?.cancel()
            playbackJob = null
            
            // Clear queue state
            audioQueue.clear()
            isBuffering = true
            
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                    track.release()
                    Log.d(TAG, "AudioTrack released and queue cleared")
                }
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
    }
} 