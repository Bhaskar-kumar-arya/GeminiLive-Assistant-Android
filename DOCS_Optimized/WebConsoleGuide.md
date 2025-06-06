# @live-api-web-console - Developer Guide

This document provides an overview of the `@live-api-web-console` project, a React-based web application for interacting with the Gemini Live API Server.

## 1. Project Overview

The `@live-api-web-console` provides a comprehensive interface for real-time, multimodal AI communication with a Gemini model, via the Gemini Live API Server. Key features include:

*   Text-based messaging.
*   Real-time audio streaming (input from microphone, output playback) using 16kHz PCM for input and handling server-streamed audio (e.g., 24kHz PCM or WebRTC Opus).
*   Video/image streaming from webcam or screen sharing (JPEG encoded at 0.5-2 FPS).
*   Tool call requests from Gemini and tool response submissions from the client.
*   Visualization of Altair tool call responses.
*   Dynamic configuration of model behavior and settings (`LiveConfig`).
*   Comprehensive logging and debugging capabilities.

## 2. Architecture and Core Components

The application utilizes a WebSocket-based client to communicate with the Gemini Live API Server, which in turn communicates with the Gemini API.

### 2.1. Client-Server Communication Protocol

The web console uses the WebSocket protocol defined in `ClientIntegrationGuide.md`. Key message types it handles include:

*   **Outgoing (Console → Server)**: `CONNECT_GEMINI`, `SEND_MESSAGE`, `SEND_REALTIME_INPUT`, `SEND_TOOL_RESPONSE`, `UPDATE_CONFIG`, `DISCONNECT_GEMINI`.
*   **Incoming (Server → Console)**: `GEMINI_CONNECTED`, `SETUP_COMPLETE`, `CONTENT_MESSAGE`, `AUDIO_CHUNK`, `TOOL_CALL`, `TOOL_CALL_CANCELLATION`, `TURN_COMPLETE`, `GEMINI_ERROR`, `GEMINI_DISCONNECTED`.

### 2.2. React Component Structure

*   **`App.tsx`**: Root component, initializes `LiveAPIProvider`, manages global state like video streams.
*   **`LiveAPIProvider` (Context)**: Provides the `MultimodalLiveClient` instance, connection status, configuration, and API interaction methods to child components.
*   **`SidePanel`**: Main UI for console interaction.
    *   Displays logs via the `Logger` component.
    *   Contains text input area for sending messages.
    *   Uses `useLoggerStore` (Zustand) for centralized log management.
*   **`ControlTray`**: Manages media controls (microphone, webcam, screen share), connection status display, and settings access.
    *   Integrates `AudioRecorder` for capturing microphone input.
    *   Handles sending captured video frames.
*   **`SettingsDialog`**: Allows users to modify `LiveConfig` parameters (model, system instruction, voice, etc.) and reconnect.
*   **`AudioPulse`**: Visual feedback for audio volume levels.
*   **`Altair`**: Component for rendering Altair visualizations, registers a `render_altair` tool that Gemini can call.

### 2.3. Key Files and Logic

*   **`multimodal-live-client.ts`**: Core WebSocket client logic.
    *   Extends `EventEmitter` for event-driven interactions (`open`, `close`, `content`, `audio`, `toolcall`, etc.).
    *   Manages WebSocket lifecycle with the Gemini Live API Server.
    *   Parses incoming messages and uses type guards from `multimodal-live-types.ts`.
*   **`multimodal-live-types.ts`**: Defines TypeScript interfaces for all WebSocket message types and `LiveConfig`, based on `@google/generative-ai` types where applicable.
*   **`utils.ts`**: Utility functions, including:
    *   `audioContext()`: Manages `AudioContext` creation and browser autoplay policies.
    *   `blobToJSON()` / `base64ToArrayBuffer()`: Data conversion utilities.

### 2.4. Hooks and State Management

*   **`use-live-api.ts` (Hook)**: Manages the `MultimodalLiveClient` instance.
    *   Sets up audio streaming (playback via `AudioStreamer`, input via `AudioRecorder`).
    *   Provides API state (`connected`, `connecting`, `client`, `config`) and methods (`connect`, `disconnect`, `setConfig`, `sendMessage`, `sendRealtimeInput`, etc.) to components.
*   **`use-webcam.ts` / `use-screen-capture.ts` (Hooks)**: Manage media stream acquisition (camera, screen) and provide a unified `UseMediaStreamResult` interface.
*   **`store-logger.ts` (Zustand Store)**: Centralized log management with features like coalescing and history limits.

### 2.5. Audio Processing System

*   **`audio-recorder.ts`**: Captures microphone audio.
    *   Uses an `AudioWorkletProcessor` (`audio-processing.ts`) for real-time conversion to 16-bit PCM at 16kHz.
    *   Emits `data` events with Base64 encoded audio chunks for `SEND_REALTIME_INPUT`.
    *   Emits `volume` events for UI feedback (via `vol-meter.ts` AudioWorklet).
*   **`audio-streamer.ts`**: Manages playback of `AUDIO_CHUNK` messages from the server.
    *   Buffers and schedules audio playback using `AudioBufferSourceNode`.
    *   Supports dynamic `AudioWorklet` integration for potential future client-side audio processing.
*   **Worklets (`worklets/`)**: Offload audio processing to separate threads:
    *   `audio-processing.ts`: Converts float audio from microphone to 16-bit PCM, buffers, and chunks.
    *   `vol-meter.ts`: Calculates RMS volume for visual feedback.

## 3. Configuration (`LiveConfig`)

The web console allows users to modify the `LiveConfig` sent to the Gemini Live API Server. Key configurable options (accessible via SettingsDialog) include:

*   **`model`**: Target Gemini model (e.g., `models/gemini-2.0-flash-live-001`).
*   **`systemInstruction`**: Text to define the AI's persona or behavior.
*   **`generationConfig`**:
    *   `temperature`, `topK`, `topP`, `maxOutputTokens`.
    *   `responseModalities`: Typically `["text"]` or `["audio"]`.
    *   `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName`: For selecting TTS voice when audio modality is active.
*   **`tools`**: Can be extended if the console implements more client-side tools (like the built-in `render_altair`).

Changes to `LiveConfig` are sent via an `UPDATE_CONFIG` message, which may cause the server to re-initialize the session with Gemini.

## 4. Node.js Server Adaptation (Mentioned for Context)

While the web console is a client, `CONSOLEDOC.md` also describes adaptations made to the core client logic (`MultimodalLiveClient`) to create the Node.js **Gemini Live API Server itself**. This server acts as the bridge:

*   Uses `ws` library instead of browser WebSocket.
*   Handles Node.js `Buffer` instead of browser `Blob`.
*   Relays audio data simply, without browser audio APIs.
*   Defines its own WebSocket protocol for clients (like this web console) to connect to it (e.g., `CONNECT_GEMINI`, `SEND_MESSAGE` from client; `GEMINI_CONNECTED`, `CONTENT_MESSAGE` to client).

## 5. Error Handling

The web console includes mechanisms for:

*   Displaying connection status and errors from the WebSocket client.
*   Logging errors from the API (`GEMINI_ERROR`).
*   Attempting reconnection on unexpected disconnections (can be configured).
*   Handling audio context errors (e.g., browser autoplay restrictions).

This guide provides a focused overview of the web console. For the detailed WebSocket protocol it uses to communicate with the Gemini Live API Server, refer to `ClientIntegrationGuide.md`. 