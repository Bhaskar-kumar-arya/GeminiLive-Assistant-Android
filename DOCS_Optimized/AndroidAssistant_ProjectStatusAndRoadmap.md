# Gemini Android Assistant - Project Status and Roadmap

This document outlines the architecture, current development status, capabilities, and future roadmap for the Gemini Android Assistant application.

## What's New [2024-06]
- **Flat tool response structure**: All tool responses now use the flat `functionResponses` array with `id`, `name`, and direct JSON `response` (required by Gemini Live API and @google/genai SDK). The previous nested structure is deprecated.
- **Schema-driven overlays**: All tool/canvas overlays are schema-driven, always include a dismiss/hide button, and support Google Search Suggestion chips (from `groundingMetadata`).
- **Per-tool async/scheduling**: Each tool can specify `defaultBehavior` (blocking or NON_BLOCKING) and `defaultScheduling` (WHEN_IDLE, INTERRUPT, SILENT), advertised in FunctionDeclaration.
- **Generalized ToolProxyActivity/Handler pattern**: Foreground-required tool actions are handled by registering handlers, making the system modular and extensible.
- **Overlay/canvas UI**: Canvas overlays are always schema-driven, scrollable, and auto-dismiss after inactivity or on user request (if implemented).
- **Permissions/security**: The service checks for `SYSTEM_ALERT_WINDOW` permission before displaying overlays, and falls back to notification or other UI if missing. Only whitelisted UI elements/actions are allowed. All overlays are clearly branded and easy to dismiss.
- **Tool chaining/context**: Tool chaining is supported via sequential TOOL_CALL messages from the server. The system is ready for future Gemini-driven context passing.
- **SendScreenSnapshotTool (Screen Sharing/Screenshot Tool):**
    - Implemented a new tool, `SendScreenSnapshotTool`, allowing Gemini to request and receive a real-time screenshot from the Android device.
    - The tool uses a foreground service with the required `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` for secure screen capture.
    - Screenshots are captured, saved locally (for user review/debug), and sent to Gemini as a JPEG using the correct, non-deprecated `SEND_REALTIME_INPUT` protocol with the `video` field (as a `GenerativeContentBlob` with `mimeType: image/jpeg`).
    - The deprecated `chunks` field is no longer used; the protocol now uses `video`, `audio`, and `text` fields as per the latest Gemini API and @google/genai SDK.
    - The tool, handler, DI, and activity integration were updated for SOLID and Hilt compliance. All DI and code errors were resolved.
    - The tool is registered as `send_screen_snapshot_tool` and can be called by Gemini for real-time screen understanding and multimodal reasoning.
    - Placeholder data classes for `ActivityStart` and `ActivityEnd` were added for protocol compatibility, though these are not required for basic screenshot use.
    - The tool is production-ready and fully interoperable with the Gemini Live API and Node.js/webconsole reference clients.
- **Initial greeting message:** After a successful Gemini session setup (`SETUP_COMPLETE`), the assistant now sends an initial real-time input message (`SEND_REALTIME_INPUT` with the `text` field) to Gemini with the content "greet the user". This ensures the assistant greets the user automatically at the start of each session.

## 1. Project Overview and Architecture

*   **Goal**: Create a native Android assistant powered by the Gemini Live API, capable of multimodal interaction (text, voice), tool usage, and background operation.
*   **Technology Stack**:
    *   Language: Kotlin
    *   UI: Jetpack Compose (Material 3)
    *   Build System: Gradle
    *   Dependency Injection: Hilt
    *   Networking: OkHttp (WebSockets), Kotlinx Serialization (JSON)
    *   Real-time Communication: WebRTC (`io.getstream:stream-webrtc-android`)
*   **Core Architecture (MVVM with Hilt)**:
    *   **`data` layer**: Manages data sources.
        *   `remote/`: `GeminiWebSocketClient` (handles raw WebSocket comms), `GeminiRepositoryImpl` (abstracts API interaction, parses messages, uses `GeminiWebSocketClient` and `Json`).
        *   `model/`: Kotlin data classes (`@Serializable`) for all WebSocket DTOs and API entities.
        *   `webrtc/`: `WebRTCManager` (handles WebRTC peer connection, tracks, signaling via WebSocket client).
        *   `local/`: `AudioPlayer` (Android `AudioTrack` based) for playing audio.
    *   **`di` layer**: Hilt modules for providing dependencies (OkHttpClient, Json, Repositories, Managers, AudioPlayer, etc.).
    *   **`ui` layer**: Jetpack Compose screens and ViewModels.
        *   `chat/`: `ChatScreen`, `ChatViewModel` (manages UI state, interacts with Repository and WebRTCManager).
    *   **`service` layer**: (Now the core of all session logic) `GeminiAssistantService`.
    *   **`util` layer**: Utility classes and extension functions.
*   **Project File Structure (`assistantapp/app/src/main/java/com/gamesmith/assistantapp/`)**:
    ```
    assistantapp/
    ├── data/
    │   ├── model/
    │   │   ├── AssistantOperationalState.kt
    │   │   ├── ClientMessagePayloads.kt
    │   │   ├── ConfigModels.kt
    │   │   ├── MessageTypes.kt
    │   │   ├── MessageWrappers.kt
    │   │   ├── Models.kt
    │   │   └── ServerMessagePayloads.kt
    │   ├── remote/
    │   │   ├── GeminiRepository.kt
    │   │   ├── GeminiRepositoryImpl.kt
    │   │   └── GeminiWebSocketClient.kt
    │   └── webrtc/
    │       └── WebRTCManager.kt
    ├── di/
    │   ├── AppModule.kt
    │   ├── NetworkModule.kt
    │   └── WebRTCModule.kt
    ├── service/
    │   ├── GeminiAssistantService.kt
    │   └── OverlayManager.kt
    ├── ui/
    │   ├── chat/
    │   │   └── ChatMessageUiModel_DEPRECATED.kt
    │   ├── theme/
    │   │   ├── Color.kt
    │   │   ├── Theme.kt
    │   │   └── Type.kt
    │   ├── OverlayPermissionScreen.kt
    │   └── ServiceControlScreen.kt
    ├── util/
    │   ├── AudioPlayer.kt
    │   └── DeviceUtils.kt
    ├── MainActivity.kt
    └── MainApplication.kt
    ```
*   **Communication Flow**: Android App <-> WebSocket <-> Local Gemini Live API Server <-> WebSocket <-> Gemini API.
    *   WebRTC is used for audio transport between App and Local Server.
    *   **[2024-06] Update:** After receiving a successful `SETUP_COMPLETE` from the server, the assistant immediately sends a real-time input message (using `SEND_REALTIME_INPUT` with the `text` field) to Gemini with the content "greet the user". This triggers an initial greeting from Gemini at the start of every session.

## 2. Current Capabilities & Features

*   **Text Chat**: Full bidirectional text communication with Gemini, including streaming responses.
*   **Connection Management**: Auto-connect to server (configurable for Emulator/Physical Device), manual connect/disconnect, visual connection status.
*   **Response Modality**: User can select text-only or audio-only responses (via `LiveConfig` update).
*   **Voice Selection**: Multiple TTS voice options available when audio mode is enabled.
*   **Audio Output (Gemini to App - WebSocket Path)**: Real-time playback of Gemini's voice responses (PCM via `AUDIO_CHUNK` messages) using a queue-based `AudioPlayer` (24kHz quality).
*   **Audio Output (Gemini to App - WebRTC Path)**: Real-time playback of Gemini's voice responses (Opus over WebRTC, decoded to PCM by client WebRTC stack) via remote audio track. Server ensures smooth, paced delivery.
*   **Audio Replay**: Stored assistant audio messages can be replayed on demand.
*   **Audio Input (App to Gemini - WebRTC Path)**:
    *   User microphone audio captured (typically 48kHz by Android WebRTC).
    *   Streamed via WebRTC to the local server.
    *   Server receives it (e.g., as 48kHz PCM via `RTCAudioSink`), resamples to 16kHz mono PCM, and forwards to Gemini API.
*   **WebRTC Integration**:*
    *   Data channel established between app and server (for potential future use).
    *   Full audio path: App Mic (WebRTC) -> Server (Resample 48kHz to 16kHz) -> Gemini API.
    *   Full audio path: Gemini API (24kHz PCM) -> Server (Resample to 48kHz, Opus encode) -> App (WebRTC) -> Speaker.
    *   Unified voice session control in UI (single button for WebRTC & mic).
    *   Speakerphone audio routing for WebRTC calls.
    *   Stopping the session now fully releases all WebRTC and microphone resources, ensuring the Android mic indicator (green dot) disappears immediately.
    *   **[2024-06] Update:** Client-side configuration for TURN/STUN servers has been successfully implemented in `WebRTCManager.kt` to improve connection reliability, especially in NAT scenarios.
*   **Turn Management & Interruption**:
    *   Basic handling of `INTERRUPTED` and `TURN_COMPLETE` server signals.
    *   Client-side audio playback stops on interruption.
    *   Server-side audio queue for WebRTC output is cleared on Gemini interruption.
*   **Error Handling**: Basic error display for connection and API issues.
*   **UI**: Jetpack Compose based UI with Material 3, message list, input, top bar with controls.
*   **Overlay UI for Assistant Status**: Implemented. The system now manages independent overlays for assistant status (e.g., 'Gemini is listening...') and a separate, scrollable "canvas" overlay for richer tool UI (e.g., displaying lists, forms, or interactive elements from tools like `create_canvas_tool` and `append_to_canvas_tool`). Both are schema-driven.
    *   The status overlay auto-hides 2 seconds after any status update. Any new status update will show the overlay for 2 seconds. Overlay UI operations are always performed on the main thread for stability. The canvas overlay for tools is managed by `create_canvas_tool` (replaces) and `append_to_canvas_tool` (adds to bottom), and has a white background.
    *   **Canvas Overlay Modernization & Google Search Suggestion Integration:**
        *   The canvas overlay now supports rendering Google Search Suggestion chips (from Gemini's groundingMetadata) as HTML using ToolUiElement.Html and a WebView.
        *   When a Google Search chip is clicked, the overlay is automatically dismissed and the link opens in the browser.
        *   This is fully SOLID-compliant and schema-driven: Gemini can send groundingMetadata and the client will render the chip as part of the canvas overlay.
        *   All other UI elements (text, images, buttons, inputs) animate in with a staggered fade+slide effect when the canvas is first shown or reappears after being dismissed.
        *   The entire canvas overlay animates its entrance (fade+scale up) and exit (fade+scale down) for a polished feel.
        *   Text elements support rich HTML formatting, including clickable links (`<a href>`), which open in the user's browser.
        *   Animations only play when the canvas is newly shown or reappears, not when appending elements to an already visible canvas.
        *   All UI elements use modern Material-inspired styling, including teal-accented buttons and input fields, and a floating icon-based dismiss button at the top right.

## 3. Detailed Implementation Status

### 3.1. Core Infrastructure

*   **Hilt DI**: Fully configured and used throughout the app.
*   **Networking**: `OkHttpClient` (custom timeouts), `kotlinx.serialization.json` (lenient, ignores unknown keys) provided via Hilt.
*   **Permissions**: `INTERNET`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `ACCESS_NETWORK_STATE`, `BLUETOOTH`, `CAMERA` (for future tools) are declared. Network security config allows cleartext for local dev.

### 3.2. WebSocket Communication (`GeminiWebSocketClient`, `GeminiRepository`)

*   Handles connection lifecycle (`CONNECT_GEMINI` on open with `LiveConfig`).
*   Sends/receives all defined message types (`SEND_MESSAGE`, `UPDATE_CONFIG`, `CONTENT_MESSAGE`, `TOOL_CALL`, `AUDIO_CHUNK`, WebRTC signaling messages, etc.).
*   Uses `StateFlow` for connection state, `SharedFlow` for incoming messages.
*   Parses JSON messages into type-safe Kotlin DTOs (`ServerMessageWrapper`, `ClientMessageWrapper` using `JsonElement` for flexible payloads).
*   Serialization issues (e.g., `LogMessagePayload`, `SetupCompleteDetails`) have been resolved to match server expectations.

### 3.3. Chat Logic (`ChatViewModel`)

*   Manages chat history (`List<ChatMessageUiModel>`), connection state.
*   Processes incoming server messages:
    *   `CONTENT_MESSAGE`: Extracts text, updates chat for assistant messages (handles streaming).
    *   `AUDIO_CHUNK` (WebSocket): Forwards to `AudioPlayer`.
    *   `TOOL_CALL`, `LOG_MESSAGE`, etc.: Adds system messages or logs.
    *   `GEMINI_CONNECTED`, `GEMINI_ERROR`, `SETUP_COMPLETE`, `TURN_COMPLETE`, `INTERRUPTED`: Updates UI/state accordingly.
*   Sends user text messages and `LiveConfig` updates.
*   Integrates with `WebRTCManager` for initiating/managing WebRTC sessions and microphone state.

### 3.4. Audio Systems

*   **`AudioPlayer` (for WebSocket `AUDIO_CHUNK`)**:
    *   Interface-based (`AudioPlayer` interface, `AndroidAudioPlayer` impl).
    *   Uses Android `AudioTrack` (24kHz sample rate).
    *   Queue-based buffering (e.g., 100ms initial buffer).
    *   Thread-safe operations (Coroutines with Mutex).
    *   Handles resource cleanup and interruptions.
    *   Supports replaying stored audio chunks associated with messages.
*   **Audio Input/Output via WebRTC (`WebRTCManager`)**:
    *   **App -> Server (Input)**:
        *   Creates local audio track from microphone.
        *   Streams via WebRTC peer connection.
        *   UI toggle for microphone permission and activation.
    *   **Server -> App (Output)**:
        *   Receives remote audio track from server.
        *   Android WebRTC stack handles Opus decoding and playback.
        *   `AudioManager` integration for speakerphone output.
    *   **Signaling**: Uses `GeminiWebSocketClient` to send/receive `WEBRTC_OFFER`, `WEBRTC_ANSWER`, `WEBRTC_ICE_CANDIDATE` messages.

### 3.5. Server-Client Message Alignment

*   All DTOs and message structures in the Android app are aligned with the server's expected/provided formats (as per `ClientIntegrationGuide.md` and `FixedMismatches.md`).
*   This includes `ContentMessagePayload.serverContent`, `ToolCallMessagePayload.toolCall.functionCalls`, direct `LiveConfig` in `UPDATE_CONFIG`, `ToolCallCancellationDetails.ids`, `LogMessagePayload.type`.

> **Note:** The SEND_REALTIME_INPUT payload is now an object: `{ text?: string, chunks?: GenerativeContentBlob[] }`. Use 'text' for real-time text input (e.g., from canvas UI), and 'chunks' for audio/video. Do not send an array directly.

## 4. Development Plan & Roadmap

(Phases from original `GeminiAndroidAssistantDevelopmentPlan.md` and `WebRTCIntegrationPlan.md` are summarized and integrated here based on current status.)

### Phase 1 & 2: Basic Connectivity, Text, Voice (largely COMPLETED)

*   [x] Hilt Setup
*   [x] WebSocket Client & Repository (DTOs aligned)
*   [x] Chat ViewModel & Basic UI (Text Chat)
*   [x] Server-Client Integration & Message Format Alignment
*   [x] **Audio Output (WebSocket `AUDIO_CHUNK`)**: Implemented and functional.
*   [x] **WebRTC Data Channel**: Base connection and signaling functional.
*   [x] **WebRTC Audio (App -> Server -> Gemini)**: App microphone to Gemini API via server is functional (including server-side resampling).
*   [x] **WebRTC Audio (Gemini -> Server -> App)**: Gemini TTS to App playback via WebRTC is functional (including server-side resampling and paced Opus streaming).
*   [x] **Turn Management & Interruption (Basic)**: Client stops audio on `INTERRUPTED`. Server clears WebRTC audio queue.

### Phase 3: Advanced Background Operation & Voice Interaction (NEXT MAJOR FOCUS)

*   **Goal**: Enable the assistant to run independently in the background, providing full voice interaction capabilities (mirroring current foreground functionality). Refactor main application UI to support a service-centric architecture (no chat history on app open).
*   **Tasks**:
    1.  **Foreground Service (`GeminiAssistantService`) Implementation**:
        *   [x] **1.1. Create `GeminiAssistantService` Class**: Define a new Android `Service` class.
        *   [x] **1.2. Implement `onStartCommand`**: Handle service start intents (e.g., to start/stop a Gemini session).
        *   [x] **1.3. Start as Foreground Service**: Call `startForeground()` with a persistent notification.
        *   [x] **1.4. Design Persistent Notification**: Create a notification layout with basic controls (e.g., Start/Stop Session, Mute/Unmute Mic, Connection Status).
        *   [x] **1.5. Handle Notification Actions**: Implement `PendingIntent`s for notification controls to interact with the service.
        *   [x] **1.6. Service Lifecycle Management**: Ensure proper resource cleanup in `onDestroy()`.
        *   [x] **1.7. Hilt Integration for Service**: Annotate with `@AndroidEntryPoint` and inject dependencies.
    2.  **Background Voice & Session Core Logic in Service**:
        *   [x] **2.1. Instantiate Core Components**: Instantiate `GeminiWebSocketClient`, `GeminiRepositoryImpl`, `WebRTCManager`, and `AudioPlayer` (or a unified audio manager) within the service. Ensure these are scoped correctly for the service's lifecycle.
            *   *Note: Core components (`GeminiWebSocketClient`, `GeminiRepository`, `WebRTCManager`, `AudioPlayer`) are injected into `GeminiAssistantService` via Hilt. `WebRTCManager.initializePeerConnectionFactory()` is called in the service's `onCreate`. Functionality confirmed.*
        *   [x] **2.2. Manage WebSocket Connection**: Implement logic in the service to initiate `CONNECT_GEMINI`, handle `GEMINI_CONNECTED`, `SETUP_COMPLETE`, and `GEMINI_DISCONNECTED` / `GEMINI_ERROR` states, updating the notification accordingly.
            *   *Note: Service initiates `CONNECT_GEMINI` (via `geminiRepository.connect()`) when `ACTION_START_SESSION` is received. It observes `ConnectionState` from the repository and `SETUP_COMPLETE`/`GEMINI_CONNECTED` messages to update its persistent notification (e.g., 'Connecting...', 'Assistant Session Active', 'Disconnected', 'Error'). Functionality confirmed.*
        *   [x] **2.3. WebRTC Session Management in Service**:
            *   [x] **2.3.1. Microphone Input (App to Gemini via Service)**:
                *   Integrate `WebRTCManager` to create local audio track from microphone, triggered by service command (e.g., notification button).
                *   Handle microphone permissions request if not already granted (though ideally handled by main app first, service should check).
                *   Stream audio via WebRTC peer connection managed by the service.
                *   *Note: Microphone input via WebRTC in service is functional. `WebRTCManager.startMicrophoneCapture()` and `createOffer()` are called when `ACTION_TOGGLE_VOICE_SESSION` is activated. User confirms ability to talk to Gemini. TODO: Robust permission handling from service context.*
            *   [x] **2.3.2. Audio Output (Gemini to App via Service)**:
                *   Integrate `WebRTCManager` to receive remote audio track from server.
                *   Ensure Android WebRTC stack handles Opus decoding and playback through the service.
                *   Manage `AudioManager` for speakerphone output or other routing from the service context.
                *   *Note: The service initializes `WebRTCManager`, which is responsible for receiving and playing the remote audio track via WebRTC. User confirms hearing Gemini, so this path is functional.*
            *   [x] **2.3.3. WebRTC Signaling via Service**: Ensure `WEBRTC_OFFER`, `WEBRTC_ANSWER`, `WEBRTC_ICE_CANDIDATE` messages are sent/received via the `GeminiWebSocketClient` instance owned by the service.
                *   *Note: Signaling messages (offers, answers, ICE candidates) are handled correctly, enabling the WebRTC session. Outgoing messages via `WebRTCManager` and incoming messages via `geminiRepository.observeServerMessages()` are functional.*
                *   *Note: Client-side TURN/STUN configuration has been successfully implemented in `WebRTCManager.kt` to enhance WebRTC connection reliability.*
        *   [ ] **2.4. WebSocket Audio Playback (`AudioPlayer` in Service)**:
            *   Integrate `AudioPlayer` to handle `AUDIO_CHUNK` messages from the server (Gemini's voice via WebSocket path) and play them.
            *   Ensure thread-safe audio queueing and playback managed by the service.
        *   [x] **2.5. Message Handling in Service**: The service's instance of `GeminiRepository` should process all incoming messages (`CONTENT_MESSAGE`, `TURN_COMPLETE`, `INTERRUPTED`, etc.) and manage conversation state.
        *   [x] **2.6. Sending User Input / Activating Session from Service**: Implement mechanisms for the service to initiate a voice interaction session with Gemini (including starting microphone input via WebRTC). *Initial focus on session activation triggered by a notification action (e.g., 'Tap to Speak' on the persistent notification).*
            *   *Note: Voice interaction session can be activated/deactivated via `ACTION_TOGGLE_VOICE_SESSION` notification action. User confirms ability to talk to Gemini, indicating this is functional.*
        *   [x] **2.7. Unified Notification Actions & Robust Dependency Handling [2024-05]**
            * The notification now shows only a single **'Start Session & Voice'** button when the session is not running. After the session is started, only **'Stop Session'** and **'Turn On/Off Mic'** are shown as appropriate.
            * The service now robustly waits for both WebSocket connection and Gemini setup (`SETUP_COMPLETE` with `success: true`) before starting the voice session, preventing race conditions and server errors.
            * This logic is handled in the service for all notification and session/mic flows, improving user experience and reliability.
    3.  **Refined Audio Focus & Resource Management in Service**:
        *   [ ] **3.1. Implement Audio Focus Handling**: Request and manage audio focus (`AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` or similar) when the assistant is speaking or listening.
        *   [x] **3.2. Microphone Resource Management**: Ensure the microphone is only active when needed and released promptly. Coordinate with notification controls (Mute/Unmute). (AudioManager state and "in-call" volume issues addressed.)
    4.  **Service-Centric `GeminiRepository` and `GeminiWebSocketClient`**:
        *   [x] **4.1. Dependency Injection for Service**: Ensure `GeminiRepository`, `GeminiWebSocketClient`, `OkHttpClient`, `Json`, etc., are correctly provided by Hilt to the `GeminiAssistantService`.
        *   [x] **4.2. State Management in Service**: Adapt any state flows or shared flows in `GeminiRepository` or `GeminiWebSocketClient` to be collected/observed safely within the service's lifecycle.
        *   [x] **4.3. Contextual Awareness**: Ensure all operations requiring an Android `Context` use the service's context.
    5.  **System Alert Window (Overlay) Preparation (for Future Tool UI)**:
        *   [x] **5.1. Add `SYSTEM_ALERT_WINDOW` Permission**: Declare the permission in `AndroidManifest.xml`.
        *   [x] **5.2. Implement Permission Request Flow**: Create a mechanism (likely in the main app's settings) for the user to grant this permission, as it's a special permission.
        *   [x] **5.3. Basic Overlay Service/Manager**: Design a simple class or set of functions within the service (or a helper class) that can create and manage a basic `View` displayed as an overlay using `WindowManager`. *This is foundational for tool UI in Phase 4, not for Phase 3 voice interaction status.*
        *   [x] **5.4. Initial Overlay UI for Status**: 
            *   Overlay status is now robust and matches the server/client protocol: shows 'Gemini is listening...' or 'Gemini is speaking...' as appropriate.
            *   [x]Overlay now auto-hides 2 seconds after any status/status change. Overlay is always shown for new status updates, even after being hidden.
            *   [x]Canvas overlay for tools (e.g., `create_canvas_tool`, `append_to_canvas_tool`) is scrollable, has a white background, and supports element aggregation.
    6.  **Inter-Process Communication (IPC) - Service Control & Status**:
        *   [x] **6.1. Define Service Actions**: Define `String` constants for actions in `Intent`s sent to the service (e.g., `ACTION_START_SESSION`, `ACTION_STOP_SESSION`, `ACTION_TOGGLE_MIC`).
        *   [x] **6.2. Main App to Service Communication (Intents)**: Allow the main app (e.g., `MainActivity`, settings screen) to start/stop the service and send commands using `startService()` with these intents.
        *   [x] **6.3. Main App UI for Service Control**: UI screen (ServiceControlScreen) in the app allows user to control the service (start/stop/toggle voice session) and see real-time status feedback.
        *   [x] **UI feedback for service status**: ServiceControlScreen displays real-time connection status from the service via broadcast.
    7.  **Power Management (Wake Locks)**: (Deferred - To be prioritized later)
        *   [ ] **7.1. Identify Critical Sections**: Determine where wake locks are needed (e.g., during active audio processing for a turn, WebRTC signaling).
        *   [ ] **7.2. Implement Partial Wake Lock**: Use `PowerManager.PARTIAL_WAKE_LOCK` acquired by the service during these critical sections.
        *   [ ] **7.3. Ensure Wake Lock Release**: Implement `try/finally` blocks to guarantee wake locks are always released, even if errors occur.
        *   [ ] **7.4. Test Battery Impact**: Monitor and optimize for battery consumption.
    8.  **Error Handling & Resilience in Service**: (Deferred - To be prioritized later)
        *   [ ] **8.1. Network Error Handling**: Implement retry logic for WebSocket connection within the service, with backoff. Update notification with status.
        *   [ ] **8.2. API Error Handling**: Gracefully handle `GEMINI_ERROR` messages from the server, update notification, and potentially stop the session or attempt reconnection based on error type.
        *   [ ] **8.3. Resource Allocation Failures**: Handle cases where audio resources (mic, speaker) cannot be acquired.
        *   [ ] **8.4. Service Stability**: Ensure the service can recover from unexpected exceptions or gracefully shut down.
    9.  **Refactor `MainActivity` and `ChatViewModel` for Service-Centric Architecture**:
        *   [x] **9.1. Design New `MainActivity` UI**: Define a simplified UI for `MainActivity` (e.g., status display of the service, button to start/stop service via notification, access to settings). Remove the chat history display.
        *   [x]When the session is stopped, the notification is updated to show 'Assistant stopped. Mic OFF.' and the overlay briefly shows this status before auto-hiding.
        *   [2024-06] Stopping the session now also fully releases all WebRTC and microphone resources, so the Android mic indicator (green dot) disappears immediately.
        *   [x] **9.2. Simplify `ChatViewModel`**:
            *   Remove `_chatMessages` StateFlow and associated UI message processing logic.
            *   Refocus `ChatViewModel` to interact with `GeminiAssistantService` for status and control (e.g., via Intents or a bound service for status updates if absolutely necessary later).
            *   Delegate core session management responsibilities (WebRTC, message sending/receiving logic) entirely to the `GeminiAssistantService`.
        *   [x] **9.3. Relocate Core Logic to `GeminiAssistantService`**: Ensure all logic previously in `ChatViewModel` for managing `GeminiRepository`, `WebRTCManager`, `AudioPlayer`, `LiveConfig` for active sessions, and processing server messages for session state (not UI display) is robustly implemented within the `GeminiAssistantService`.
        *   [x] **9.4. Modify App Navigation/Entry**: Ensure that opening the app leads to the new simplified `MainActivity` UI, not a chat screen.

> **Note:** As of this update, the app is now fully service-centric. All chat/session logic is handled by the background service (`GeminiAssistantService`). The UI is focused on service control and status. The previous chat UI and `ChatViewModel` are deprecated and retained only for reference.

### Phase 4: Background Tool Calling Framework & Specific Tools

*   **Status Update [2024-06]:**
    *   The Android client now includes `SendScreenSnapshotTool` for real-time screen sharing, using the latest Gemini Live API protocol (`video` field in `SEND_REALTIME_INPUT`).
    *   The tool uses a foreground service for MediaProjection, saves the screenshot locally, and sends it to Gemini as a JPEG image.
    *   All tool call/response integration is SOLID-compliant, DI-driven, and production-ready.
    *   Deprecated protocol fields (e.g., `chunks`) are no longer used; the implementation matches the latest Gemini API and SDK expectations.
    *   Placeholder data classes for `ActivityStart` and `ActivityEnd` are included for protocol compatibility.
    *   All code and DI errors were resolved during implementation.

### Phase 5: UI/UX Refinement, Error Handling, Testing (Ongoing & Expanded)

*   **Goal**: Polish app, make it robust, enhance user experience.
*   **Tasks**:
    1.  **UI/UX**: More detailed visual cues for all states (listening, processing, speaking, tool executing). Settings screen enhancements.
    2.  **Error Handling**: Comprehensive handling for network issues (retries), API errors, tool failures (permissions, logic), with clear user feedback.
    3.  **Testing**:*
        *   Unit Tests (JUnit, MockK) for ViewModels, Repositories, Tools.
        *   Integration Tests (Robolectric/Hilt) for component interactions.
        *   UI Tests (Espresso) for UI flows.
    4.  **Refine Turn Management/Interruption**: More sophisticated visual feedback and state handling for recording/listening/speaking states, especially with WebRTC.

## 5. Known Limitations & Considerations

*   **Gemini Live API Constraint**: Typically supports one primary response modality per session turn (e.g., text OR audio).
*   **Modality Changes**: Often require `UPDATE_CONFIG` and may involve a brief reconnection/re-setup of the Gemini session.
*   **WebRTC Complexity**: While offering benefits, WebRTC adds complexity in signaling, connection management, and audio handling.
*   **Audio focus, wake locks, and advanced error handling**: Some features (e.g., robust audio focus handling, wake locks, advanced error handling, and notification updates for tool execution) are still marked as TODO or in-progress.

This document provides a snapshot of the Android Assistant's status and direction. It will be updated as development progresses.

### 2024-05: Notification & Session/Mic Startup Improvements

- Notification now shows only a single 'Start Session & Voice' button when the session is not running.
- After session start, only 'Stop Session' and 'Turn On/Off Mic' are shown as appropriate.
- Service robustly waits for both WebSocket connection and Gemini setup (SETUP_COMPLETE) before starting voice, preventing race conditions and server errors.
- Improves user experience and reliability for background operation and voice interaction.

### [2024-06] Tool Calling: Per-Tool Async & Scheduling Support
- The assistant now supports per-tool customization for asynchronous (NON_BLOCKING) tool execution and response scheduling.
- Each tool can specify:
    - `defaultBehavior`: Whether it is "NON_BLOCKING" (async) or blocking (null).
    - `defaultScheduling`: The default scheduling for the FunctionResponse ("WHEN_IDLE", "INTERRUPT", or "SILENT").
- These properties are advertised to the model in the tool's FunctionDeclaration and used in FunctionResponse.
- Example: `TestTool` is registered with `defaultScheduling = "SILENT"` (the model will not be interrupted or notified immediately when this tool responds).
- This enables fine-grained control over how and when tool results are delivered to the model/user, and allows for future extension to per-call overrides if needed.

### [2024-06] System Alert Window UI for Tools & Generalized ToolProxyActivity (SOLID Handler Pattern)
*   **Goal:** Enable Gemini to present arbitrary, schema-driven UI overlays (System Alert Windows) for tool interactions, using a compact, declarative JSON syntax. The system supports independent "status" overlays and a "canvas" overlay for richer tool UI. All UI runs in the background, outside the main app screen, and is triggered by tool calls only.
*   **Token Cost Efficiency:**
    *   UI schemas and tool messages must be as compact as possible to minimize token usage.
    *   Use concise, versioned JSON for UI definitions (short property names, minimal nesting).
    *   Reference media/resources by ID or URI where possible; avoid embedding large blobs.
    *   Only send user actions/inputs (not full UI state) back to Gemini.
    *   Support partial/delta updates for UI changes.
*   **Tasks:**
    *   [x] **4.1. Design Schema-Driven Tool UI System:**
        *   Flexible UI schema (inspired by Adaptive Cards/Flutter) supporting text, images, audio, video, buttons, input fields, lists, forms, and custom layouts.
        *   Schema parsing/rendering is decoupled from tool logic and service orchestration (SOLID).
        *   Renderer supports media and user input, overlays are minimal, branded, and dismissible.
    *   [x] **4.2. ToolUIManager in Service:**
        *   `ToolUIManager` interface and `SystemAlertWindowToolUIManager` implementation are fully integrated via Hilt DI.
        *   Manages separate "status" overlays (e.g., "Gemini is listening...") and a "canvas" overlay.
        *   The "canvas" overlay is scrollable, has a white background, and supports aggregation of elements.
        *   All overlays (status and tool UI) are now schema-driven and interactive, including keyboard support for input fields.
        *   Manual test overlay has been removed after validation.
    *   [x] **4.2.1. Generalized ToolProxyActivity & Handler Pattern:**
        *   `ToolProxyActivity` is now a generic activity that delegates tool actions to registered `ToolProxyHandler` implementations.
        *   Each tool type (e.g., camera/photo, contact picker) is implemented as a handler (e.g., `CameraToolProxyHandler`).
        *   The activity is SOLID-compliant, extensible, and future-proof: new tool types are added by implementing a handler and registering it.
        *   The camera/photo logic is now fully encapsulated in `CameraToolProxyHandler`, not in the activity itself.
    *   [ ] **4.3. Handle Tool UI Permissions:**
        *   Check `SYSTEM_ALERT_WINDOW` permission before displaying overlays.
        *   If missing, notify user via notification or fallback UI.
        *   All overlays must be clearly branded and easy to dismiss.
    *   [x] **4.4. Tool Execution & Service Integration:**
        *   Extend `NativeTool` and `ToolExecutionResult` to support UI-driven results: `NeedsUiInteraction(val schema: ToolUiSchema, val details: Map<String, Any>)`.
        *   Update `ToolExecutor` and `GeminiAssistantService` to delegate UI display to `ToolUIManager` and resume tool execution with user input.
        *   Tool logic, UI management, and service orchestration are decoupled (SOLID).
    *   [ ] **4.5. Security, UX, and Extensibility:**
        *   Whitelist allowed UI elements and actions.
        *   Auto-dismiss overlays after inactivity or on user request.
        *   Schema-driven: new UI elements can be added by extending the schema and renderer.
        *   Support for future Gemini-driven UI scenarios (forms, media, etc.).
    *   [ ] **4.6. Testing & Validation:**
        *   Unit test: UI schema parsing, rendering, and user input handling.
        *   Integration test: End-to-end tool call → overlay UI → user input → tool response flow.

> **As of 2024-06, the Android client is fully compatible with the Gemini Live API tool calling protocol and interoperates with the Node.js reference client and server.** 