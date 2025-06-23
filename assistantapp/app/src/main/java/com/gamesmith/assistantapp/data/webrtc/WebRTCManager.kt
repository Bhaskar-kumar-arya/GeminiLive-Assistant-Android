package com.gamesmith.assistantapp.data.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.gamesmith.assistantapp.data.remote.GeminiWebSocketClient
import com.gamesmith.assistantapp.data.remote.RTCIceCandidateJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.webrtc.DataChannel
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Base64 // Requires API level 26 or higher, or use a compatibility library
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import java.util.UUID
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiWebSocketClient: GeminiWebSocketClient,
    private val json: Json
) {
    private val webRTCManagerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val TAG = "WebRTCManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalAudioMode: Int = audioManager.mode // Store initial mode
    private var originalSpeakerphoneOn: Boolean = audioManager.isSpeakerphoneOn // Store initial speakerphone state
    private var audioFocusRequested: Boolean = false

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private val AUDIO_TRACK_ID = "ARDAMSa0"
    private val MEDIA_STREAM_ID = "ARDAMS"

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:192.168.134.88:3478").createIceServer(),
        PeerConnection.IceServer.builder("turn:192.168.134.88:3478?transport=udp")
            .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK) // Use INSECURE_NO_CHECK for local testing without valid certs
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:192.168.134.88:3478?transport=tcp")
            .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK) // Use INSECURE_NO_CHECK for local testing without valid certs
            .createIceServer()
    )

    var audioPlaybackListener: AudioPlaybackListener? = null

    interface AudioPlaybackListener {
        fun onAudioPlaybackStarted()
        fun onAudioPlaybackStopped()
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
            Log.d(TAG, "SignalingState changed to: $newState")
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            Log.d(TAG, "IceConnectionState changed to: $newState")
            if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                Log.i(TAG, "ICE Connection State: CONNECTED")
                // Microphone capture should be explicitly started by the service, not implicitly here.
                // setSpeakerphoneOn(true) // Managed by startMicrophoneCapture
            }
            if (newState == PeerConnection.IceConnectionState.FAILED) {
                Log.e(TAG, "ICE Connection State: FAILED")
                // If ICE fails, we should ensure microphone and speakerphone are off.
                stopMicrophoneCaptureInternal(true) // Pass a flag to indicate it's due to failure
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "IceConnectionReceivingChange: $receiving")
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
            Log.d(TAG, "IceGatheringState changed to: $newState")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                Log.d(TAG, "New ICE candidate found: $it")
                val rtcIceCandidateJson = RTCIceCandidateJson(
                    sdpMid = it.sdpMid,
                    sdpMLineIndex = it.sdpMLineIndex,
                    candidate = it.sdp
                )
                webRTCManagerScope.launch {
                    geminiWebSocketClient.sendWebRTCICECandidate(rtcIceCandidateJson)
                }
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.d(TAG, "ICE candidates removed: ${candidates?.contentToString()}")
        }

        override fun onAddStream(stream: MediaStream?) {
            Log.d(TAG, "Remote stream added: ${stream?.id}")
        }

        override fun onRemoveStream(stream: MediaStream?) {
            Log.d(TAG, "Remote stream removed: ${stream?.id}")
        }

        override fun onDataChannel(dc: DataChannel?) {
            dc?.let {
                Log.d(TAG, "Remote data channel received: ${it.label()}")
                this@WebRTCManager.dataChannel = it
                it.registerObserver(dataChannelObserver)
            }
        }

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "Renegotiation needed")
        }

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            Log.d(TAG, "Track added - Receiver: ${receiver?.id()}, Streams: ${streams?.joinToString { it.id }}")
            receiver?.track()?.let { track ->
                Log.d(TAG, "Track kind: ${track.kind()}, id: ${track.id()}, enabled: ${track.enabled()}")
                if (track.kind() == MediaStreamTrack.AUDIO_TRACK_KIND) {
                    Log.i(TAG, "Remote audio track received: ${track.id()}. Playback should be handled by WebRTC.")
                    track.setEnabled(true)
                    audioPlaybackListener?.onAudioPlaybackStarted()
                } else if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    Log.i(TAG, "Remote video track received: ${track.id()}.")
                } else {
                    Log.d(TAG, "Unknown remote track kind received: ${track.kind()}")
                }
            }
        }

        fun onRemoveTrack(receiver: RtpReceiver?) {
            Log.d(TAG, "Track removed - Receiver: ${receiver?.id()}")
            audioPlaybackListener?.onAudioPlaybackStopped()
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            Log.d(TAG, "Transceiver track event - mid: ${transceiver?.mid}, media type: ${transceiver?.mediaType}")
        }
    }

    private val dataChannelObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {
            Log.d(TAG, "DataChannel buffered amount changed: $previousAmount")
        }

        override fun onStateChange() {
            dataChannel?.state().let {
                Log.d(TAG, "DataChannel state changed: $it")
                if (it == DataChannel.State.OPEN) {
                    Log.i(TAG, "DataChannel state: OPEN. Sending ping.")
                    sendDataChannelMessage("ping")
                }
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer?) {
            buffer?.data?.let {
                val message = Charsets.UTF_8.decode(it).toString()
                Log.i(TAG, "Received message on DataChannel: $message")
            }
        }
    }

    fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        Log.d(TAG, "PeerConnectionFactory initialized.")

        val factoryBuilder = PeerConnectionFactory.builder()
        peerConnectionFactory = factoryBuilder.createPeerConnectionFactory()
        Log.d(TAG, "PeerConnectionFactory created.")
    }

    private fun hmacSha1(value: String, key: String): String {
        try {
            val signingKey = SecretKeySpec(key.toByteArray(), "HmacSHA1")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(signingKey)
            val rawHmac = mac.doFinal(value.toByteArray())
            return Base64.getEncoder().encodeToString(rawHmac) // Use Base64 encoding
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "HMAC-SHA1 algorithm not found: ${e.message}", e)
        } catch (e: InvalidKeyException) {
            Log.e(TAG, "Invalid key for HMAC-SHA1: ${e.message}", e)
        }
        return "" // Return empty string on error
    }

    fun createPeerConnection() {
        if (peerConnectionFactory == null) {
            initializePeerConnectionFactory()
        }

        val turnUsername = "android_client_${System.currentTimeMillis() / 1000}" // Use timestamp as username
        val turnPassword = hmacSha1("$turnUsername:gamesmith.com", "BhaskarArya979869") // Use username:realm and static-auth-secret

        val iceServersWithAuth = iceServers.map { server ->
            if (server.uri.startsWith("turn:")) {
                PeerConnection.IceServer.builder(server.uri)
                    .setUsername(turnUsername)
                    .setPassword(turnPassword)
                    .setTlsCertPolicy(server.tlsCertPolicy) // Keep the policy from the original server
                    .createIceServer()
            } else {
                server
            }
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServersWithAuth).apply {
            // Set the realm for TURN authentication
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
            // Add other necessary configurations
        }

        // Close existing peer connection before creating a new one
        peerConnection?.close()
        Log.d(TAG, "Previous PeerConnection closed if it existed.")

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, peerConnectionObserver)
        if (peerConnection == null) {
            Log.e(TAG, "Failed to create PeerConnection.")
        } else {
            Log.d(TAG, "New PeerConnection created.")
            createAndAddAudioTrack()
        }
    }

    private fun createAndAddAudioTrack() {
        if (peerConnectionFactory == null || peerConnection == null) {
            Log.e(TAG, "PeerConnectionFactory or PeerConnection not initialized. Cannot create audio track.")
            return
        }

        audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        Log.d(TAG, "AudioSource created.")

        localAudioTrack = peerConnectionFactory?.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack?.setEnabled(true)
        Log.d(TAG, "LocalAudioTrack created with ID: $AUDIO_TRACK_ID")

        localAudioTrack?.let { track ->
            val streamIds = listOf(MEDIA_STREAM_ID)
            peerConnection?.addTrack(track, streamIds)
            Log.d(TAG, "LocalAudioTrack added to PeerConnection with stream ID: $MEDIA_STREAM_ID")
        } ?: Log.e(TAG, "LocalAudioTrack was null, not added to PeerConnection.")
    }

    fun startMicrophoneCapture() {
        if (audioSource == null || localAudioTrack == null) {
            Log.w(TAG, "AudioSource or LocalAudioTrack not initialized. Cannot start microphone capture.")
            return
        }
        if (!audioFocusRequested) {
            originalAudioMode = audioManager.mode
            originalSpeakerphoneOn = audioManager.isSpeakerphoneOn
            // Request audio focus - simplified for now, proper handling is more complex
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            audioFocusRequested = true
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true // Default to speakerphone ON for voice sessions
            Log.d(TAG, "AudioManager mode set to MODE_IN_COMMUNICATION and speakerphone ON.")
        }
        localAudioTrack?.setEnabled(true)
        Log.i(TAG, "Microphone capture started (localAudioTrack enabled).")
        // setSpeakerphoneOn(true) // Handled by audio focus request logic
    }

    fun stopMicrophoneCapture() {
        stopMicrophoneCaptureInternal(false)
    }

    private fun stopMicrophoneCaptureInternal(isFailure: Boolean) {
        if (localAudioTrack == null && !isFailure) { // If not a failure, and track is null, nothing to do unless audio focus was held
            Log.w(TAG, "LocalAudioTrack not initialized. Cannot stop microphone capture.")
        }

        localAudioTrack?.setEnabled(false) // Disable track even if it was null but audio focus was set
        Log.i(TAG, "Microphone capture stopped (localAudioTrack disabled).")

        if (audioFocusRequested) {
            audioManager.mode = originalAudioMode
            audioManager.isSpeakerphoneOn = originalSpeakerphoneOn
            audioManager.abandonAudioFocus(null)
            audioFocusRequested = false
            Log.d(TAG, "AudioManager mode restored to original and speakerphone to original. Audio focus abandoned.")
        }
    }

    fun createDataChannel(label: String = "dataChannel") {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection not initialized. Cannot create DataChannel.")
            return
        }
        val init = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel(label, init)
        dataChannel?.registerObserver(dataChannelObserver)
        Log.d(TAG, "DataChannel '$label' created locally.")
    }

    fun createOffer() {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection not initialized. Cannot create offer.")
            return
        }
        val sdpConstraints = MediaConstraints().apply {
            // mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            // mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Log.d(TAG, "Offer created successfully: ${it.description}")
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) { Log.d(TAG, "Inner onCreateSuccess for setLocalDescription (offer).") }
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description (offer) set successfully.")
                            webRTCManagerScope.launch {
                                geminiWebSocketClient.sendWebRTCOffer(it.description)
                            }
                        }
                        override fun onCreateFailure(error: String?) { Log.e(TAG, "Inner onCreateFailure for setLocalDescription (offer): $error") }
                        override fun onSetFailure(error: String?) { Log.e(TAG, "Failed to set local description (offer): $error") }
                    }, it)
                } ?: Log.e(TAG, "Offer SDP was null after creation.")
            }
            override fun onCreateFailure(error: String?) { Log.e(TAG, "Failed to create offer: $error") }
            override fun onSetFailure(error: String?) { Log.e(TAG, "onSetFailure during createOffer (unexpected for createOffer itself): $error") }
            override fun onSetSuccess() { Log.d(TAG, "Outer onSetSuccess for createOffer (typically not primary path for offer).") }
        }, sdpConstraints)
    }

    fun handleRemoteAnswer(sdpAnswer: String) {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection not initialized. Cannot handle remote answer.")
            return
        }
        Log.d(TAG, "Handling remote answer: $sdpAnswer")
        val answerDescription = SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) { Log.d(TAG, "Inner onCreateSuccess for setRemoteDescription (answer).") }
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description (answer) set successfully.")
            }
            override fun onCreateFailure(error: String?) { Log.e(TAG, "Inner onCreateFailure for setRemoteDescription (answer): $error") }
            override fun onSetFailure(error: String?) { Log.e(TAG, "Failed to set remote description (answer): $error") }
        }, answerDescription)
    }

    fun addRemoteIceCandidate(candidateJson: RTCIceCandidateJson) {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection not initialized. Cannot add remote ICE candidate.")
            return
        }
        try {
            val iceCandidate = IceCandidate(
                candidateJson.sdpMid ?: "",
                candidateJson.sdpMLineIndex ?: -1,
                candidateJson.candidate ?: ""
            )
            peerConnection?.addIceCandidate(iceCandidate)
            Log.d(TAG, "Remote ICE candidate added: ${candidateJson.candidate}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding remote ICE candidate: ${e.message}", e)
        }
    }
    
    fun sendDataChannelMessage(message: String) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            val buffer = ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8))
            dataChannel?.send(DataChannel.Buffer(buffer, false))
            Log.i(TAG, "Sent message on DataChannel: $message")
        } else {
            Log.e(TAG, "DataChannel is not open. Cannot send message: $message. State: ${dataChannel?.state()}")
        }
    }

    fun close() {
        Log.d(TAG, "Closing WebRTCManager resources.")
        stopMicrophoneCaptureInternal(true) // Ensure audio is reset as part of closing

        dataChannel?.unregisterObserver()
        dataChannel?.close()
        dataChannel = null
        Log.d(TAG, "DataChannel closed and unobserved.")

        peerConnection?.dispose()
        peerConnection = null
        Log.d(TAG, "PeerConnection disposed.")

        audioSource?.dispose()
        audioSource = null
        Log.d(TAG, "AudioSource disposed.")

        localAudioTrack?.dispose() // Ensure localAudioTrack is also disposed
        localAudioTrack = null
        Log.d(TAG, "LocalAudioTrack disposed.")

        // Do not deinitialize PeerConnectionFactory globally here if it might be reused.
        // However, if this WebRTCManager instance is truly going away and no other part of the app
        // will use WebRTC imminently, factory.dispose() could be considered.
        // For now, let's assume the factory might be needed again or by other components.
        // peerConnectionFactory?.dispose()
        // peerConnectionFactory = null
        // Log.d(TAG, "PeerConnectionFactory disposed.")
        // PeerConnectionFactory.deinitializeAndroidGlobals() // If completely done with WebRTC in app

        Log.i(TAG, "WebRTCManager resources closed.")
    }

    fun disposePeerConnectionFactory() {
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        setSpeakerphoneOn(false)
        Log.d(TAG, "PeerConnectionFactory disposed.")
    }

    fun isPeerConnectionAvailable(): Boolean {
        return peerConnection != null && 
               (peerConnection?.connectionState() == PeerConnection.PeerConnectionState.CONNECTED ||
                peerConnection?.connectionState() == PeerConnection.PeerConnectionState.CONNECTING)
    }

    private fun setSpeakerphoneOn(enabled: Boolean) {
        if (enabled) {
            if (!audioFocusRequested) { // Only save original settings if we haven't already
                originalAudioMode = audioManager.mode
                originalSpeakerphoneOn = audioManager.isSpeakerphoneOn
                // Request audio focus (simplified example)
                // In a real app, handle focus changes with an OnAudioFocusChangeListener
                val result = audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audioFocusRequested = true
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.isSpeakerphoneOn = true
                    Log.d(TAG, "Speakerphone ON. Audio mode set to MODE_IN_COMMUNICATION. Audio focus granted.")
                } else {
                    Log.e(TAG, "Audio focus request failed.")
                }
            } else { // If focus already requested, just ensure speakerphone is set
                audioManager.isSpeakerphoneOn = true
                 Log.d(TAG, "Speakerphone ON (audio focus was already granted).")
            }
        } else {
            if (audioFocusRequested) {
                audioManager.isSpeakerphoneOn = originalSpeakerphoneOn // Restore original speakerphone state
                audioManager.mode = originalAudioMode           // Restore original audio mode
                audioManager.abandonAudioFocus(null)
                audioFocusRequested = false
                Log.d(TAG, "Speakerphone OFF. Audio mode restored to original. Audio focus abandoned.")
            } else {
                 Log.d(TAG, "Speakerphone OFF (audio focus was not active or already abandoned).")
            }
        }
    }
} 