import WebSocket from 'ws';
import { v4 as uuidv4 } from 'uuid';
import dotenv from 'dotenv';
import wrtc from '@roamhq/wrtc'; // Changed to @roamhq/wrtc
const { RTCPeerConnection, RTCSessionDescription, RTCIceCandidate } = wrtc; // Changed to @roamhq/wrtc
import { OpusDecoder } from 'opus-decoder'; // Using opus-decoder library
import { GenaiLiveClient } from './gemini-client/multimodal-live-client-genai';
import { resample as resampleAudio } from 'wave-resampler'; // Changed import for resampling
import type {
    LiveConfig,
    ServerContentMessage as GeminiServerContentMessage,
    ToolCallMessage as GeminiToolCallMessage,
    ToolCallCancellationMessage as GeminiToolCallCancellationMessage,
    SetupCompleteMessage as GeminiSetupCompleteMessage,
    StreamingLog as GeminiStreamingLog,
    ToolCall,
    ToolCallCancellation,
} from './gemini-client/multimodal-live-types';
import { // Changed from import type
    ClientToServerMessage,
    ServerToClientMessage,
    GenerateImagePayload, // Import GenerateImagePayload
    ImageGenerationResultPayload, // Import ImageGenerationResultPayload
    ConnectGeminiPayload,
    SendMessagePayload,
    SendRealtimeInputPayload,
    SendToolResponsePayload,
    GeminiDisconnectedPayload,
    GeminiErrorPayload,
    AudioChunkPayload,
    WebRTCOfferPayload, // Added for WebRTC
    WebRTCAnswerPayload, // Added for WebRTC
    WebRTCIceCandidatePayload, // Added for WebRTC
    RTCIceCandidateJson, // Added for WebRTC
} from './websocket-protocol-types';
import { generateImage } from './image-generator'; // Import generateImage function
import chalk from 'chalk';
import path from 'path';
import * as fs from 'fs/promises'; // Import Node.js file system module
// Attempt to access RTCAudioSink via the main wrtc object
const RTCAudioSink = (wrtc as any).nonstandard?.RTCAudioSink;
const RTCAudioSource = (wrtc as any).nonstandard?.RTCAudioSource; // Added for sending audio

// Load environment variables
dotenv.config();
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;

if (!GEMINI_API_KEY) {
    console.error("CRITICAL ERROR: GEMINI_API_KEY not found in environment for gateway.ts. This should not happen.");
    // In a real scenario, you might want to prevent the server from starting or handle this more gracefully.
    // For now, we'll allow it to proceed but log a critical error.
}

// Map to store MultimodalLiveClient instances, keyed by client app's WebSocket connection ID
const activeGeminiClients = new Map<string, GenaiLiveClient>();
// Map to store RTCPeerConnection instances, keyed by client app's WebSocket connection ID
const activePeerConnections = new Map<string, wrtc.RTCPeerConnection>(); // Added for WebRTC
const activeAudioSources = new Map<string, any>(); // Added for WebRTC outgoing audio sources
const outgoingFrameQueues = new Map<string, Array<AudioFrame>>(); // Queue of frames to be sent
const isPacerActive = new Map<string, boolean>(); // Tracks if a pacer is active for a client
const activeWebSockets = new Map<string, WebSocket>();

// Define the structure for an audio frame
interface AudioFrame {
    samples: Int16Array;
    sampleRate: number;
    bitsPerSample: number;
    channelCount: number;
    numberOfFrames: number;
}

// --- BEGIN: Global declaration for audio write streams (debugging only) ---
// import type { WriteStream } from 'fs'; // REMOVE if fs isn't used elsewhere prominently, or keep if fs is generally used.
// declare global {
//   // eslint-disable-next-line no-var
//   var __audioWriteStreams: Map<string, WriteStream> | undefined;
//   // eslint-disable-next-line no-var
//   var __resampledAudioWriteStreams: Map<string, WriteStream> | undefined;
// }
// --- END: Global declaration ---

// --- BEGIN: Global declaration for outgoing audio write streams (debugging only) ---
// declare global {
//   // eslint-disable-next-line no-var
//   var __outgoingAudioWriteStreams: Map<string, WriteStream> | undefined;
// }
// --- END: Global declaration for outgoing audio ---

function safeJsonParse(data: WebSocket.Data): ClientToServerMessage | null {
    try {
        if (typeof data === 'string') {
            return JSON.parse(data) as ClientToServerMessage;
        }
        if (Buffer.isBuffer(data)) {
            return JSON.parse(data.toString('utf-8')) as ClientToServerMessage;
        }
        console.warn('Received non-string/non-buffer WebSocket message type:', typeof data);
        return null;
    } catch (error) {
        console.error('Failed to parse WebSocket message:', error);
        return null;
    }
}

export function handleConnection(ws: WebSocket) {
    const clientId = uuidv4();
    activeWebSockets.set(clientId, ws);
    console.log(`Client app connected with ID: ${clientId}`);

    let peerConnection: wrtc.RTCPeerConnection;
    let audioSink: any = null; 

    ws.on('message', async (messageData: WebSocket.Data) => {
        const message: ClientToServerMessage | null = safeJsonParse(messageData); // Explicitly type message
        if (!message) {
            ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload: { message: 'Invalid JSON message received.' } } as ServerToClientMessage));
            return;
        }

        console.log(`[${clientId}] Received message from client app:`, message.type);

        let geminiClient = activeGeminiClients.get(clientId);
        // Retrieve the current peerConnection for this client, if it exists
        // Note: peerConnection is also declared in the outer scope of handleConnection,
        // but we should rely on activePeerConnections for established ones.
        const currentPc = activePeerConnections.get(clientId);

        try {
            switch (message.type) {
                case 'CONNECT_GEMINI':
                    if (geminiClient) {
                        console.log(`[${clientId}] Gemini connection already exists. Disconnecting old one.`);
                        await geminiClient.disconnect();
                        activeGeminiClients.delete(clientId);
                    }
                    if (!GEMINI_API_KEY) {
                        console.error(`[${clientId}] GEMINI_API_KEY is not available. Cannot connect to Gemini.`);
                        ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload: { message: 'Server configuration error: API key missing.' } } as ServerToClientMessage));
                        return;
                    }
                    console.log(`[${clientId}] Connecting to Gemini with config:`, (message.payload as ConnectGeminiPayload).initialConfig);
                    geminiClient = new GenaiLiveClient({ apiKey: GEMINI_API_KEY });
                    activeGeminiClients.set(clientId, geminiClient);

                    // Attach event listeners to this Gemini client instance
                    geminiClient.on('open', () => {
                        console.log(`[${clientId}] Gemini WebSocket connection opened.`);
                        ws.send(JSON.stringify({ type: 'GEMINI_CONNECTED' } as ServerToClientMessage));
                    });

                    geminiClient.on('close', ({ reason }) => {
                        console.log(`[${clientId}] Gemini WebSocket connection closed. Reason: ${reason.toString()}`);
                        const payload: GeminiDisconnectedPayload = { reason: reason.toString() };
                        ws.send(JSON.stringify({ type: 'GEMINI_DISCONNECTED', payload } as ServerToClientMessage));
                        activeGeminiClients.delete(clientId); // Clean up
                        // Clear queue and stop pacer on Gemini disconnect
                        outgoingFrameQueues.delete(clientId);
                        isPacerActive.delete(clientId);
                    });

                    geminiClient.on('setupcomplete', (data: GeminiSetupCompleteMessage['setupComplete']) => {
                        console.log(`[${clientId}] Gemini setup complete. Success: ${data.success}`);
                        const payload: GeminiSetupCompleteMessage = { setupComplete: data }; // This matches the expected payload structure
                        ws.send(JSON.stringify({ type: 'SETUP_COMPLETE', payload } as ServerToClientMessage));
                    });

                    geminiClient.on('content', (geminiContent: GeminiServerContentMessage['serverContent']) => {
                        console.log(`[${clientId}] Received content from Gemini.`);
                        const payload: GeminiServerContentMessage = { serverContent: geminiContent };
                        ws.send(JSON.stringify({ type: 'CONTENT_MESSAGE', payload } as ServerToClientMessage));
                    });

                    geminiClient.on('toolcall', (toolCall: ToolCall) => {
                        // Verbose logging for tool calls
                        if (toolCall && Array.isArray(toolCall.functionCalls)) {
                            toolCall.functionCalls.forEach((call) => {
                                console.log(chalk.cyan(`[${clientId}] [TOOL_CALL] Gemini requested tool: ${call.name} (ID: ${call.id})`));
                                console.log(chalk.cyan(`[${clientId}] [TOOL_CALL] Arguments:`), JSON.stringify(call.args, null, 2));
                            });
                        } else {
                            console.log(chalk.cyan(`[${clientId}] [TOOL_CALL] Gemini requested tool(s):`), JSON.stringify(toolCall, null, 2));
                        }
                        const payload: GeminiToolCallMessage = { toolCall };
                        ws.send(JSON.stringify({ type: 'TOOL_CALL', payload } as ServerToClientMessage));
                    });

                    geminiClient.on('toolcallcancellation', (toolCallCancellation: ToolCallCancellation) => {
                        console.log(`[${clientId}] Received tool call cancellation from Gemini.`);
                        const payload: GeminiToolCallCancellationMessage = { toolCallCancellation };
                        ws.send(JSON.stringify({ type: 'TOOL_CALL_CANCELLATION', payload } as ServerToClientMessage));
                    });

                    geminiClient.on('interrupted', () => {
                        console.log(`[${clientId}] Gemini processing interrupted.`);
                        // Stop pacer and clear audio queue
                        console.log(`[${clientId}] Gemini interruption: Clearing audio queue and stopping pacer.`);
                        isPacerActive.set(clientId, false);
                        const queue = outgoingFrameQueues.get(clientId);
                        if (queue) {
                            queue.length = 0; // Clear the array
                        }
                        // It's also fine to use outgoingFrameQueues.delete(clientId) if the queue should be entirely removed
                        // For now, clearing ensures the current pacer loop iteration (if any) sees an empty queue.

                        ws.send(JSON.stringify({ type: 'INTERRUPTED' } as ServerToClientMessage));
                    });

                    geminiClient.on('turncomplete', () => {
                        console.log(`[${clientId}] Gemini turn complete.`);
                        ws.send(JSON.stringify({ type: 'TURN_COMPLETE' } as ServerToClientMessage));
                    });

                    geminiClient.on('audio', (audioData: ArrayBuffer) => { // No longer async
                        // console.log(`[${clientId}] DEBUG: geminiClient.on('audio') FIRED. Received ${audioData.byteLength} bytes from Gemini.`); // REMOVE DEBUG LOG
                        const currentAudioSource = activeAudioSources.get(clientId);
                        if (!currentAudioSource) {
                            console.warn(`[${clientId}] Audio received from Gemini, but no active audio source. Discarding.`);
                            return;
                        }
                        if (audioData.byteLength === 0) {
                            // console.log(`[${clientId}] Received empty audio data from Gemini. Skipping.`);
                            return;
                        }

                        try {
                            const GEMINI_AUDIO_SAMPLE_RATE = 24000;
                            const WEBRTC_AUDIO_SAMPLE_RATE = 48000;
                            const SAMPLES_PER_10MS_FRAME = WEBRTC_AUDIO_SAMPLE_RATE / 100;
                            const BYTES_PER_10MS_FRAME = SAMPLES_PER_10MS_FRAME * 2;

                            // console.log(`[${clientId}] Gemini audio event: Received ${audioData.byteLength} bytes.`);

                            const pcmDataInt16_Gemini = new Int16Array(audioData);
                            if (pcmDataInt16_Gemini.length === 0) {
                                console.warn(`[${clientId}] Gemini audio data empty after Int16Array conversion.`);
                                return;
                            }

                            const resampledFloat64 = resampleAudio(pcmDataInt16_Gemini, GEMINI_AUDIO_SAMPLE_RATE, WEBRTC_AUDIO_SAMPLE_RATE, { method: "sinc", LPF: true });
                            const pcmDataInt16_WebRTC_FullChunk = new Int16Array(resampledFloat64.length);
                            for (let i = 0; i < resampledFloat64.length; i++) {
                                pcmDataInt16_WebRTC_FullChunk[i] = Math.max(-32768, Math.min(32767, Math.round(resampledFloat64[i])));
                            }

                            if (pcmDataInt16_WebRTC_FullChunk.length === 0) {
                                console.warn(`[${clientId}] Resampled Gemini audio data is empty.`);
                                return;
                            }

                            const newResampledDataBuffer = Buffer.from(pcmDataInt16_WebRTC_FullChunk.buffer, pcmDataInt16_WebRTC_FullChunk.byteOffset, pcmDataInt16_WebRTC_FullChunk.byteLength);

                            let tempProcessingBuffer = newResampledDataBuffer;
                            let framesAddedToQueueThisEvent = 0;

                            const clientFrameQueue = outgoingFrameQueues.get(clientId) || [];
                            outgoingFrameQueues.set(clientId, clientFrameQueue);

                            while (tempProcessingBuffer.length >= BYTES_PER_10MS_FRAME) {
                                const frameDataBuffer = tempProcessingBuffer.subarray(0, BYTES_PER_10MS_FRAME);
                                const remainingBuffer = tempProcessingBuffer.subarray(BYTES_PER_10MS_FRAME);

                                const tempBufferForInt16 = Buffer.alloc(frameDataBuffer.length);
                                frameDataBuffer.copy(tempBufferForInt16);
                                const frameSamplesInt16 = new Int16Array(tempBufferForInt16.buffer, tempBufferForInt16.byteOffset, tempBufferForInt16.length / 2);

                                if (frameSamplesInt16.length === SAMPLES_PER_10MS_FRAME) {
                                    const frame: AudioFrame = {
                                        samples: frameSamplesInt16,
                                        sampleRate: WEBRTC_AUDIO_SAMPLE_RATE,
                                        bitsPerSample: 16,
                                        channelCount: 1,
                                        numberOfFrames: SAMPLES_PER_10MS_FRAME,
                                    };
                                    clientFrameQueue.push(frame);
                                    framesAddedToQueueThisEvent++;
                                } else {
                                    console.warn(`[${clientId}] Frame generation: Extracted frame sample count mismatch. Expected ${SAMPLES_PER_10MS_FRAME}, got ${frameSamplesInt16.length}. Discarding.`);
                                }
                                tempProcessingBuffer = remainingBuffer;
                            }
                            if (framesAddedToQueueThisEvent > 0) {
                                // console.log(`[${clientId}] Added ${framesAddedToQueueThisEvent} frames to queue. Queue size: ${clientFrameQueue.length}`);
                            }
                            if (tempProcessingBuffer.length > 0) {
                                console.warn(`[${clientId}] Warning: ${tempProcessingBuffer.length} bytes remaining in tempProcessingBuffer after chunking. This leftover data is currently discarded.`);
                            }

                            ensurePacerStarted(clientId, currentAudioSource);
                            // console.log(`[${clientId}] DEBUG: ensurePacerStarted CALLED after processing Gemini audio. Queue size: ${outgoingFrameQueues.get(clientId)?.length ?? 0}`); // REMOVE DEBUG LOG

                        } catch (e: any) {
                            console.error(`[${clientId}] Error processing Gemini audio for queueing:`, e.message, e.stack);
                        }
                    });

                    geminiClient.on('log', (log: { type: string; message: any }) => {
                        // Wrap the log in a StreamingLog object with a date
                        const streamingLog = {
                            date: new Date(),
                            type: log.type,
                            message: log.message,
                        };
                        ws.send(JSON.stringify({ type: 'LOG_MESSAGE', payload: streamingLog } as ServerToClientMessage));
                    });

                    try {
                        await geminiClient.connect((message.payload as ConnectGeminiPayload).initialConfig);
                    } catch (connectError: any) {
                        console.error(`[${clientId}] Failed to connect to Gemini:`, connectError);
                        const payload: GeminiErrorPayload = { message: 'Failed to connect to Gemini.', details: connectError.message };
                        ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload } as ServerToClientMessage));
                        activeGeminiClients.delete(clientId); // Clean up if connect fails
                    }
                    break;

                case 'SEND_MESSAGE':
                    if (!geminiClient) {
                        ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload: { message: 'Gemini not connected. Send CONNECT_GEMINI first.' } } as ServerToClientMessage));
                        return;
                    }
                    const sendMessagePayload = message.payload as SendMessagePayload;
                    console.log(`[${clientId}] Sending message to Gemini:`, sendMessagePayload.parts);
                    geminiClient.send(sendMessagePayload.parts, sendMessagePayload.turnComplete);
                    break;

                case 'SEND_REALTIME_INPUT':
                    if (!geminiClient) {
                        ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload: { message: 'Gemini not connected.' } } as ServerToClientMessage));
                        return;
                    }
                    const sendRealtimeInputPayload = message.payload as SendRealtimeInputPayload;
                    console.log(`[${clientId}] Sending realtime input to Gemini.`, sendRealtimeInputPayload);
                    geminiClient.sendRealtimeInput(sendRealtimeInputPayload); // Pass the full payload object
                    break;

                case 'SEND_TOOL_RESPONSE':
                    if (!geminiClient) {
                        console.log(chalk.red(`[${clientId}] [ERROR] Gemini not connected. Cannot send tool response.`));
                        ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload: { message: 'Gemini not connected.' } } as ServerToClientMessage));
                        return;
                    }
                    const sendToolResponsePayload = message.payload as SendToolResponsePayload;
                    // Verbose logging for tool responses
                    if (sendToolResponsePayload && sendToolResponsePayload.toolResponse && Array.isArray(sendToolResponsePayload.toolResponse.functionResponses)) {
                        sendToolResponsePayload.toolResponse.functionResponses.forEach((resp) => {
                            console.log(chalk.green(`[${clientId}] [TOOL_RESPONSE] Responding to Gemini tool call ID: ${resp.id}`));
                            console.log(chalk.green(`[${clientId}] [TOOL_RESPONSE] Response:`), JSON.stringify(resp.response, null, 2));
                        });
                    } else {
                        console.log(chalk.green(`[${clientId}] [TOOL_RESPONSE] Responding to Gemini tool call(s):`), JSON.stringify(sendToolResponsePayload, null, 2));
                    }
                    geminiClient.sendToolResponse(sendToolResponsePayload.toolResponse);
                    break;

                case 'UPDATE_CONFIG':
                    if (!geminiClient) {
                        ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload: { message: 'Gemini not connected. Send CONNECT_GEMINI first.' } } as ServerToClientMessage));
                        return;
                    }
                    const newConfig = message.payload as LiveConfig;
                    console.log(`[${clientId}] Updating Gemini config:`, newConfig);
                    try {
                        await geminiClient.setConfig(newConfig);
                         // The 'open' and 'setupcomplete' events from the new connection will notify the client.
                    } catch (updateError: any) {
                        console.error(`[${clientId}] Failed to update Gemini config:`, updateError);
                        const payload: GeminiErrorPayload = { message: 'Failed to update Gemini configuration.', details: updateError.message };
                        ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload } as ServerToClientMessage));
                    }
                    break;

                case 'DISCONNECT_GEMINI':
                    if (geminiClient) {
                        console.log(`[${clientId}] Disconnecting from Gemini as per client request.`);
                        await geminiClient.disconnect();
                        // The 'close' event handler for geminiClient will send GEMINI_DISCONNECTED and clean up from map.
                    } else {
                        console.log(`[${clientId}] Received DISCONNECT_GEMINI, but no active Gemini client found.`);
                        // Optionally send a message if needed, but client might not expect one if it thinks it's already disconnected.
                    }
                    break;

                // --- WebRTC Signaling Cases ---
                case 'WEBRTC_OFFER':
                    try {
                        console.log(`[${clientId}] Received WEBRTC_OFFER`);
                        const { sdp } = message.payload as WebRTCOfferPayload;

                        // Clean up existing PeerConnection and AudioSource if any
                        const oldPc = activePeerConnections.get(clientId);
                        if (oldPc) {
                            console.log(`[${clientId}] Existing RTCPeerConnection found. Closing it before creating a new one.`);
                            oldPc.close();
                            activePeerConnections.delete(clientId);
                        }
                        const oldAudioSource = activeAudioSources.get(clientId);
                        if (oldAudioSource && typeof oldAudioSource.stop === 'function') {
                            console.log(`[${clientId}] Stopping existing RTCAudioSource.`);
                            oldAudioSource.stop(); // Ensure old source is stopped
                        }
                        activeAudioSources.delete(clientId);
                        outgoingFrameQueues.delete(clientId); // Clear queue for new connection
                        isPacerActive.set(clientId, false); // Reset pacer flag for new connection

                        const iceServers = [{ urls: 'stun:stun.l.google.com:19302' }];
                        // Assign to the peerConnection in the broader scope for other handlers if needed, but primarily manage through map
                        peerConnection = new RTCPeerConnection({ iceServers }); 
                        activePeerConnections.set(clientId, peerConnection);

                        // Create and add an audio track for sending Gemini's audio to the client
                        if (!RTCAudioSource) {
                            console.error(`[${clientId}] RTCAudioSource is not available. Cannot create outgoing audio track for WebRTC.`);
                            // Optionally send an error to the client app
                        } else {
                            const newAudioSource = new RTCAudioSource();
                            const audioTrack = newAudioSource.createTrack();
                            if (audioTrack) {
                                peerConnection.addTrack(audioTrack); 
                                console.log(`[${clientId}] Added RTCAudioSource track to PeerConnection for outgoing audio.`);
                                activeAudioSources.set(clientId, newAudioSource);
                            } else {
                                console.error(`[${clientId}] Failed to create track from RTCAudioSource.`);
                            }
                        }
                        
                        // The following line was removed as addTrack is used above.
                        // peerConnection.addTransceiver('audio', { direction: 'sendrecv' });

                        peerConnection.onconnectionstatechange = (event: Event) => { // Added Event type
                            console.log(`[${clientId}] WebRTC PeerConnection state changed: ${peerConnection.connectionState}`);
                            if (peerConnection.connectionState === 'connected') {
                                console.log(`[${clientId}] WebRTC PeerConnection connected.`);
                                // Potentially trigger data channel test or notify client further if needed
                            }
                            if (peerConnection.connectionState === 'failed') {
                                console.error(`[${clientId}] WebRTC PeerConnection failed.`);
                            }
                        };

                        peerConnection.onicecandidate = (event: wrtc.RTCPeerConnectionIceEvent) => {
                            if (event.candidate) {
                                console.log(`[${clientId}] Sending ICE candidate to client`);
                                const iceCandidatePayload: WebRTCIceCandidatePayload = { candidate: event.candidate.toJSON() as RTCIceCandidateJson };
                                ws.send(JSON.stringify({ type: 'WEBRTC_ICE_CANDIDATE', payload: iceCandidatePayload } as ServerToClientMessage));
                            }
                        };

                        peerConnection.ondatachannel = (event: wrtc.RTCDataChannelEvent) => {
                            console.log(`[${clientId}] Data channel received:`, event.channel.label);
                            const dataChannel = event.channel;
                            dataChannel.onmessage = (msgEvent: MessageEvent) => {
                                console.log(`[${clientId}] Message from data channel:`, msgEvent.data);
                                if (msgEvent.data === 'ping') {
                                    dataChannel.send('pong');
                                }
                            };
                            dataChannel.onopen = () => console.log(`[${clientId}] Data channel opened`);
                            dataChannel.onclose = () => console.log(`[${clientId}] Data channel closed`);
                        };
                        
                        // Placeholder for ontrack when audio/video is added
                        peerConnection.ontrack = (event: any) => {
                            console.log(`[${clientId}] Track event received. Track Kind: ${event.track.kind}, ID: ${event.track.id}, Label: ${event.track.label}`);
                            console.log(`[${clientId}] Transceiver:`, event.transceiver);
                            console.log(`[${clientId}] Receiver:`, event.receiver);
                            // console.log(`[${clientId}] Streams associated with track:`, event.streams);

                            const track = event.track;
                            const receiver = event.receiver;

                            if (track.kind === 'audio') {
                                console.log(`[${clientId}] Audio track received. Properties: id=${track.id}, label=${track.label}, kind=${track.kind}, readyState=${track.readyState}, muted=${track.muted}`);
                                
                                if (!RTCAudioSink) {
                                    console.error(`[${clientId}] RTCAudioSink is not available in this wrtc fork through .nonstandard. Cannot process audio.`);
                                    return;
                                }

                                audioSink = new RTCAudioSink(track); // audioSink is now from the outer scope
                                console.log(`[${clientId}] RTCAudioSink created for track ID: ${track.id}`);

                                let pcmBuffer = Buffer.alloc(0);
                                const CHUNK_SIZE_BYTES = 3200; // Corresponds to 100ms of 16kHz, 16-bit mono audio (16000 samples/sec * 1 channel * 2 bytes/sample * 0.1 sec)

                                audioSink.ondata = (data: { samples: Buffer | Int16Array, sampleRate: number, bitsPerSample: number, channelCount: number, numberOfFrames: number }) => {
                                    // Log details of received PCM data
                                    // console.log(`[${clientId}] RTCAudioSink ondata: SR=${data.sampleRate}, Channels=${data.channelCount}, Frames=${data.numberOfFrames}, Bits=${data.bitsPerSample}, SamplesLen=${data.samples.byteLength}`);
                                    
                                    if (data.samples.byteLength > 0) {
                                        let samplesBuffer: Buffer;
                                        if (data.samples instanceof Int16Array) {
                                            samplesBuffer = Buffer.from(data.samples.buffer, data.samples.byteOffset, data.samples.byteLength);
                                        } else if (Buffer.isBuffer(data.samples)) {
                                            samplesBuffer = data.samples;
                                        } else {
                                            console.error(`[${clientId}] Unknown samples type received:`, typeof data.samples);
                                            return;
                                        }

                                        // Resample if necessary
                                        let resampledBuffer = samplesBuffer;
                                        if (data.sampleRate === 48000 && data.channelCount === 1 && data.bitsPerSample === 16) {
                                            // Convert Buffer to Int16Array for wave-resampler
                                            const pcmDataInt16 = new Int16Array(samplesBuffer.buffer, samplesBuffer.byteOffset, samplesBuffer.length / 2);
                                            const resampledPcmDataFloat64 = resampleAudio(pcmDataInt16, data.sampleRate, 16000, { method: "sinc", LPF: true });
                                            
                                            // Convert Float64Array back to Int16Array and then to Buffer
                                            const resampledPcmDataInt16 = new Int16Array(resampledPcmDataFloat64.length);
                                            for (let i = 0; i < resampledPcmDataFloat64.length; i++) {
                                                resampledPcmDataInt16[i] = Math.max(-32768, Math.min(32767, Math.round(resampledPcmDataFloat64[i])));
                                            }
                                            resampledBuffer = Buffer.from(resampledPcmDataInt16.buffer);
                                            // console.log(`[${clientId}] Resampled audio from ${data.sampleRate}Hz to 16000Hz. Original size: ${samplesBuffer.length}, Resampled size: ${resampledBuffer.length}`);
                                        } else if (data.sampleRate !== 16000) {
                                            console.warn(`[${clientId}] Audio received with unhandled sample rate: ${data.sampleRate}Hz, channels: ${data.channelCount}, bits: ${data.bitsPerSample}. Not resampling.`);
                                        }

                                        // Use the resampledBuffer (which might be the original if no resampling occurred)
                                        pcmBuffer = Buffer.concat([pcmBuffer, resampledBuffer]);

                                        while (pcmBuffer.length >= CHUNK_SIZE_BYTES) {
                                            const chunk = pcmBuffer.subarray(0, CHUNK_SIZE_BYTES);
                                            pcmBuffer = pcmBuffer.subarray(CHUNK_SIZE_BYTES);

                                            const base64Audio = chunk.toString('base64');
                                            
                                            const geminiClient = activeGeminiClients.get(clientId);
                                            if (geminiClient && (geminiClient as any).session) { // Check if geminiClient and its session exist
                                                console.log(`[${clientId}] Sending ${chunk.length} bytes of PCM data (approx. 100ms) to Gemini.`);
                                                // Corrected payload format for sendRealtimeInput
                                                geminiClient.sendRealtimeInput({
                                                    audio: { // SDK expects an object with an 'audio' key
                                                        mimeType: 'audio/pcm;rate=16000',
                                                        data: base64Audio
                                                    }
                                                });
                                            } else {
                                                console.warn(`[${clientId}] Gemini client not connected or session not found. Cannot send audio data.`);
                                            }
                                        }
                                    }
                                };
                                audioSink.onerror = (error: any) => {
                                    console.error(`[${clientId}] RTCAudioSink error:`, error);
                                };
                                track.onended = () => {
                                    console.log(`[${clientId}] Audio track ${track.id} ended. Stopping sink.`);
                                    if (audioSink) {
                                        audioSink.stop();
                                        audioSink = null; // Clear reference
                                    }
                                };
                            } else if (track.kind === 'video') {
                                console.log(`[${clientId}] Video track received. Not currently processed.`);
                            }
                        };

                        // Modify peerConnection.onconnectionstatechange to stop the sink if it exists
                        const originalOnConnectionStateChange = peerConnection.onconnectionstatechange;
                        peerConnection.onconnectionstatechange = (event: Event) => {
                            if (originalOnConnectionStateChange) {
                                (originalOnConnectionStateChange as any)(event);
                            }
                            // console.log(`[${clientId}] WebRTC PeerConnection state changed (in wrapped handler): ${peerConnection.connectionState}`); // This can be verbose
                            if (peerConnection.connectionState === 'closed' || peerConnection.connectionState === 'failed' || peerConnection.connectionState === 'disconnected') {
                                console.log(`[${clientId}] WebRTC connection closed/failed/disconnected. Stopping audio sink if it exists.`);
                                if (audioSink) { // audioSink is now accessible here
                                    audioSink.stop();
                                    audioSink = null; // Clear reference
                                    console.log(`[${clientId}] RTCAudioSink stopped due to connection state change.`);
                                }
                            }
                        };

                        await peerConnection.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp }));
                        const answer = await peerConnection.createAnswer();
                        await peerConnection.setLocalDescription(answer);

                        const answerPayload: WebRTCAnswerPayload = { sdp: answer.sdp! }; // sdp can be null, ensure it's not.
                        ws.send(JSON.stringify({ type: 'WEBRTC_ANSWER', payload: answerPayload } as ServerToClientMessage));
                        console.log(`[${clientId}] Sent WEBRTC_ANSWER`);

                    } catch (error: any) {
                        console.error(`[${clientId}] Error processing WEBRTC_OFFER:`, error);
                        const payload: GeminiErrorPayload = { message: 'Error processing WebRTC offer.', details: error.message };
                        ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload } as ServerToClientMessage));
                    }
                    break;

                case 'GENERATE_IMAGE': // Handle image generation request
                    console.log(`[${clientId}] Received GENERATE_IMAGE request.`);
                    const generateImagePayload = message.payload as GenerateImagePayload;
                    
                    // Call the image generation function
                    const imageResult = await generateImage(
                        generateImagePayload.text,
                        generateImagePayload.imageUri
                    );

                    // Send the result back to the client
                    // Read the generated image file and encode it as Base64
                    let imageDataBase64: string | undefined;
                    if (imageResult.success && imageResult.imageUrl) {
                        try {
                            // Construct the full server-side path
                            const imageFilePath = path.join(__dirname, imageResult.imageUrl); // Assumes imageUrl is relative to src/
                            const imageBuffer = await fs.readFile(imageFilePath);
                            imageDataBase64 = imageBuffer.toString('base64');
                            console.log(`[${clientId}] Read and Base64 encoded image from ${imageFilePath}`);
                        } catch (fileError) {
                            console.error(`[${clientId}] Error reading image file ${imageResult.imageUrl}:`, fileError);
                            // Continue without image data, send error in payload if possible
                        }
                    }

                    const imageResultPayload: ImageGenerationResultPayload = {
                        // Include original success/error and imageUrl
                        ...imageResult,
                        // Add the Base64 encoded image data if available
                        imageData: imageDataBase64
                    };

                    ws.send(JSON.stringify({ type: 'IMAGE_GENERATION_RESULT', payload: imageResultPayload } as ServerToClientMessage));
                    console.log(`[${clientId}] Sent IMAGE_GENERATION_RESULT to client (with${imageDataBase64 ? '' : 'out'} image data).`);
                    break;

                case 'WEBRTC_ICE_CANDIDATE':
                    try {
                        console.log(`[${clientId}] Received WEBRTC_ICE_CANDIDATE`);
                        const { candidate } = message.payload as WebRTCIceCandidatePayload;
                        const pc = activePeerConnections.get(clientId);
                        if (pc && candidate && candidate.candidate) { // Ensure candidate and its properties are not null
                            await pc.addIceCandidate(new RTCIceCandidate(candidate as any)); // Type assertion for candidate
                             console.log(`[${clientId}] Added ICE candidate`);
                        } else {
                            console.warn(`[${clientId}] Could not add ICE candidate, PeerConnection or candidate data missing.`);
                        }
                    } catch (error: any) {
                        console.error(`[${clientId}] Error processing WEBRTC_ICE_CANDIDATE:`, error);
                        const payload: GeminiErrorPayload = { message: 'Error processing WebRTC ICE candidate.', details: error.message };
                        ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload } as ServerToClientMessage));
                    }
                    break;

                default:
                    console.warn(`[${clientId}] Received unknown message type:`, (message as any).type);
                    ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload: { message: `Unknown message type: ${(message as any).type}` } } as ServerToClientMessage));
            }
        } catch (error: any) {
            console.error(`[${clientId}] Error processing message type ${message?.type}:`, error);
            const payload: GeminiErrorPayload = { message: `Error processing message: ${message?.type}`, details: error.message };
            ws.send(JSON.stringify({ type: 'GEMINI_ERROR', payload } as ServerToClientMessage));
        }
    });

    ws.on('close', () => {
        console.log(`Client app disconnected: ${clientId}`);
        const geminiClient = activeGeminiClients.get(clientId);
        if (geminiClient) {
            console.log(`[${clientId}] Client app WebSocket closed, ensuring Gemini client is also disconnected.`);
            geminiClient.disconnect(); 
        }
        // Clean up WebRTC connection
        const pc = activePeerConnections.get(clientId);
        if (pc) {
            pc.close();
            activePeerConnections.delete(clientId);
            console.log(`[${clientId}] WebRTC PeerConnection closed and cleaned up.`);
        }
        // Clean up WebRTC audio source related states
        const audioSourceToStop = activeAudioSources.get(clientId);
        if (audioSourceToStop && typeof audioSourceToStop.stop === 'function') {
            console.log(`[${clientId}] Stopping RTCAudioSource due to client disconnect.`);
            audioSourceToStop.stop();
        }
        activeAudioSources.delete(clientId);
        outgoingFrameQueues.delete(clientId); // Clear frame queue
        isPacerActive.delete(clientId); // Clear pacer active flag
        activeWebSockets.delete(clientId);

        // --- BEGIN: Final cleanup of all recording streams on client disconnect ---
        // const rawInputStream = (globalThis as any).__audioWriteStreams?.get(clientId);
        // if (rawInputStream) {
        //     rawInputStream.end();
        //     (globalThis as any).__audioWriteStreams?.delete(clientId);
        //     console.log(`[${clientId}] Cleaned up raw 48kHz input recording stream on disconnect.`);
        // }
        // const resampledInputStream = (globalThis as any).__resampledAudioWriteStreams?.get(clientId);
        // if (resampledInputStream) {
        //     resampledInputStream.end();
        //     (globalThis as any).__resampledAudioWriteStreams?.delete(clientId);
        //     console.log(`[${clientId}] Cleaned up resampled 16kHz input recording stream on disconnect.`);
        // }
        // const outgoingStream = (globalThis as any).__outgoingAudioWriteStreams?.get(clientId);
        // if (outgoingStream) {
        //     outgoingStream.end();
        //     (globalThis as any).__outgoingAudioWriteStreams?.delete(clientId);
        //     console.log(`[${clientId}] Cleaned up outgoing client audio recording stream on disconnect.`);
        // }
        // --- END: Final cleanup of all recording streams on client disconnect ---
    });

    ws.on('error', (error) => {
        console.error(`[${clientId}] WebSocket error for client app:`, error);
        // The 'close' event will usually follow, which handles cleanup.
        // However, if a specific error message needs to be sent to the client before close, it could be done here.
        // For instance, if the ws connection is still open: ws.send(JSON.stringify( ... error message ... ));
    });
} 

// Helper function to manage the pacer loop
async function pacerLoop(clientId: string, audioSource: any) {
    const PACING_INTERVAL_MS = 10; // Target 10ms pacing, adjust if needed based on performance
    const queue = outgoingFrameQueues.get(clientId);

    // console.log(`[${clientId}] DEBUG: pacerLoop ENTERED. Initial queue size: ${queue?.length ?? 'N/A'}. Pacer active: ${isPacerActive.get(clientId)}`); // REMOVE DEBUG LOG

    // --- BEGIN: Setup outgoing audio recording for debugging ---
    // const fs = require('fs'); 
    // const path = require('path'); 
    // let outgoingAudioWriteStream: WriteStream | undefined;
    // if (!(globalThis as any).__outgoingAudioWriteStreams) {
    //     (globalThis as any).__outgoingAudioWriteStreams = new Map<string, WriteStream>();
    // }
    // if (!(globalThis as any).__outgoingAudioWriteStreams.has(clientId)) {
    //     const outgoingAudioFilePath = path.join(__dirname, `sent_audio_to_client_${clientId}.pcm`);
    //     outgoingAudioWriteStream = fs.createWriteStream(outgoingAudioFilePath, { flags: 'a' });
    //     (globalThis as any).__outgoingAudioWriteStreams.set(clientId, outgoingAudioWriteStream);
    //     console.log(`[${clientId}] Pacer: Recording outgoing audio to ${outgoingAudioFilePath}`);
    // } else {
    //     outgoingAudioWriteStream = (globalThis as any).__outgoingAudioWriteStreams.get(clientId);
    // }
    // --- END: Setup outgoing audio recording ---

    if (!queue) {
        console.error(`[${clientId}] Pacer: No queue found. Stopping.`);
        isPacerActive.set(clientId, false);
        return;
    }

    let framesSentThisCycle = 0;
    let hasSentSpeakingThisCycle = false; // Track if ASSISTANT_SPEAKING was sent
    const cycleStartTime = Date.now();

    // Modified loop condition to check isPacerActive
    while (queue.length > 0 && isPacerActive.get(clientId) === true) {
        const frame = queue.shift(); // Get the next frame from the front of the queue
        if (frame && audioSource) {
            try {
                // Check if audioSource is still valid/not stopped, if it has such a property
                if (typeof audioSource.stop === 'function' && (audioSource as any)._stopped) {
                     console.warn(`[${clientId}] Pacer: Audio source appears to be stopped. Halting pacer. Frames remaining: ${queue.length}`);
                     isPacerActive.set(clientId, false);
                     return; // Exit pacer if source is stopped
                }
                // --- SEND ASSISTANT_SPEAKING BEFORE FIRST FRAME ---
                if (!hasSentSpeakingThisCycle) {
                    const ws = activeWebSockets.get(clientId);
                    if (ws && ws.readyState === ws.OPEN) {
                        ws.send(JSON.stringify({ type: 'ASSISTANT_SPEAKING', payload: { speaking: true } }));
                        console.log(`[${clientId}] Pacer: Sent ASSISTANT_SPEAKING`);
                    }
                    hasSentSpeakingThisCycle = true;
                }
                // --- END ---
                audioSource.onData(frame);
                // console.log(`[${clientId}] DEBUG: pacerLoop - audioSource.onData(frame) CALLED for a frame.`); // REMOVE DEBUG LOG

                // --- BEGIN: Write outgoing frame to file for debugging ---
                // if (outgoingAudioWriteStream && outgoingAudioWriteStream.writable && frame.samples) {
                //     const bufferToWrite = Buffer.from(frame.samples.buffer, frame.samples.byteOffset, frame.samples.byteLength);
                //     outgoingAudioWriteStream.write(bufferToWrite);
                // }
                // --- END: Write outgoing frame to file ---

                framesSentThisCycle++;
                // console.log(`[${clientId}] Pacer: Sent 1 frame. Queue size: ${queue.length}`);
            } catch (e: any) {
                console.error(`[${clientId}] Pacer: Error sending frame via onData:`, e.message, e.stack);
                isPacerActive.set(clientId, false); // Stop pacer on error for safety
                return;
            }
        } else if (!audioSource) {
            console.warn(`[${clientId}] Pacer: Audio source disappeared during pacing. Stopping pacer. Frames remaining: ${queue.length}`);
            isPacerActive.set(clientId, false);
            return;
        }


        // Modified sleep condition to check isPacerActive
        if (queue.length > 0 && isPacerActive.get(clientId) === true) {
            await new Promise(resolve => setTimeout(resolve, PACING_INTERVAL_MS));
        }
    }
    
    const cycleEndTime = Date.now();
    if (framesSentThisCycle > 0) {
        console.log(`[${clientId}] Pacer: Finished sending ${framesSentThisCycle} frames in this cycle (took ${cycleEndTime - cycleStartTime}ms). Queue empty or pacer stopped.`);
    }

    isPacerActive.set(clientId, false);
    if (!isPacerActive.get(clientId) || queue.length === 0) { // Check if explicitly stopped or queue is empty
        console.log(`[${clientId}] Pacer explicitly stopped or completed. Queue length: ${queue?.length ?? 'N/A'}`);
    }

    // --- BEGIN: Cleanup outgoing audio write stream for this pacer instance ---
    // Note: This cleans up when a pacer loop *finishes*. 
    // We also need cleanup if the client disconnects while pacer is theoretically active but queue is empty.
    // if (outgoingAudioWriteStream && outgoingAudioWriteStream.writable) {
    //     console.log(`[${clientId}] Pacer: Pacer loop ended. Closing outgoing audio recording stream.`);
    //     outgoingAudioWriteStream.end(); 
    // }
    // --- END: Cleanup outgoing audio write stream ---
}

function ensurePacerStarted(clientId: string, audioSource: any) {
    // console.log(`[${clientId}] DEBUG: ensurePacerStarted ENTERED.`); // REMOVE DEBUG LOG
    if (!audioSource) {
        console.warn(`[${clientId}] ensurePacerStarted: No audio source provided. Cannot start pacer.`);
        return;
    }
    if (!isPacerActive.get(clientId)) {
        // console.log(`[${clientId}] Pacer: Not active, starting now.`);
        isPacerActive.set(clientId, true);
        pacerLoop(clientId, audioSource).catch(error => {
            console.error(`[${clientId}] Pacer loop encountered an unhandled error:`, error);
            isPacerActive.set(clientId, false); // Ensure pacer is marked inactive on unhandled exception
        });
    } else {
        // console.log(`[${clientId}] Pacer: Already active.`);
    }
} 