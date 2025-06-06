# Gemini Live API Server - Client Integration Guide

This document provides the necessary information for client applications (mobile, web, desktop) to integrate with the Gemini Live API Server via its WebSocket interface.

## 1. Connection

1.  Establish a WebSocket connection to the server:
    `ws://<server-ip>:<port>` (Default port is 3001)
2.  After the WebSocket connection is open, the first message sent by the client **must** be `CONNECT_GEMINI` to initialize the session with the Gemini API.

## 2. WebSocket Protocol

All communication uses a JSON-based protocol. Each message follows a consistent structure:

```json
{
  "type": "MESSAGE_TYPE_STRING",
  "payload": { /* Message-specific payload object */ }
}
```

Or, if there's no payload:

```json
{
  "type": "MESSAGE_TYPE_STRING"
}
```

### 2.1. Message Flow Overview

```
Legend:
  C: Client (Your Application)
  S: Gemini Live API Server (Bridge)
  G: Gemini API

Initial Connection & Config:
  C --(WebSocket Open)--> S
  C --{CONNECT_GEMINI, payload:initialConfig}--> S
  S --{GEMINI_CONNECTED}--> C  (Confirms WebSocket to Server is OK, Gemini session init started)
  S --{SETUP_COMPLETE, payload:details}--> C (Confirms Gemini session with API is fully set up)

Typical Text/Content Exchange:
  C --{SEND_MESSAGE, payload:{parts, turnComplete:true}}--> S --> G
  G --(Streams content)--> S --{CONTENT_MESSAGE, payload:{serverContent}}--> C (Can be multiple)
  S --{TURN_COMPLETE}--> C (Indicates Gemini finished its turn)

Real-time Audio Input (User Speaking to Gemini):
  C --{SEND_REALTIME_INPUT, payload:{text?: string, audio?: GenerativeContentBlob, video?: GenerativeContentBlob}}--> S --> G (Multiple, continuous)
  (Gemini processes audio, then may send CONTENT_MESSAGE with text/audio response)

Tool Call:
  C --{SEND_MESSAGE}--> S --> G
  G --(Decides to use a tool)--> S --{TOOL_CALL, payload:{toolCall}}--> C
  C --(Executes tool)--> External Service / Local Function
  C --{SEND_TOOL_RESPONSE, payload:{toolResponse}}--> S --> G
  G --(Uses tool result)--> S --{CONTENT_MESSAGE}--> C

Configuration Update:
  C --{UPDATE_CONFIG, payload:newLiveConfig}--> S
  S --(Applies config, may restart session with Gemini)--> G
  S --{SETUP_COMPLETE, payload:details}--> C (If re-setup occurs)

Disconnection:
  C --{DISCONNECT_GEMINI}--> S
  S --{GEMINI_DISCONNECTED}--> C
```

### 2.2. Client -> Server Messages (App -> Server)

| Message Type             | Payload Structure                                      | Purpose                                                                 |
| ------------------------ | ------------------------------------------------------ | ----------------------------------------------------------------------- |
| `CONNECT_GEMINI`         | `{ "initialConfig": LiveConfig }`                     | **Required first message.** Establishes and configures Gemini session. |
| `SEND_MESSAGE`           | `{ "parts": Part[], "turnComplete": boolean }`        | Send text, images, or other content to Gemini.                          |
| `SEND_REALTIME_INPUT`    | `{ "text"?: string, "audio"?: GenerativeContentBlob, "video"?: GenerativeContentBlob }` | Stream real-time text (e.g., from canvas UI), audio, or video (image frame). **Note:** The old `chunks`/`mediaChunks[]` field is **deprecated** and should not be used. Use `audio`, `video`, and `text` fields instead. |
| `SEND_TOOL_RESPONSE`     | `{ "toolResponse": ToolResponsePayload }`             | Respond to a `TOOL_CALL` from Gemini.                                  |
| `UPDATE_CONFIG`          | `LiveConfig` (Directly as payload)                     | Change API configuration for the current session.                       |
| `DISCONNECT_GEMINI`      | (no payload)                                           | Politely end the Gemini session and close the WebSocket.                |
| `WEBRTC_OFFER`           | `{ "sdp": String }`                                   | Send WebRTC Session Description Protocol (SDP) offer for WebRTC setup.  |
| `WEBRTC_ICE_CANDIDATE`   | `RTCIceCandidateJson`                                  | Send a WebRTC ICE candidate for establishing the peer connection.       |

#### DEPRECATION NOTICE: `chunks`/`mediaChunks[]` in SEND_REALTIME_INPUT
- The `chunks` (or `mediaChunks[]`) field in `SEND_REALTIME_INPUT` is **deprecated**. Only the first chunk is processed; all others are ignored.
- **Use the new fields:**
    - `audio` (for audio input)
    - `video` (for video/image frames, e.g., screenshots)
    - `text` (for real-time text input)
- This matches the latest Gemini API and @google/genai SDK expectations.

#### Example: Sending a Screenshot (Image) to Gemini
```json
{
  "type": "SEND_REALTIME_INPUT",
  "payload": {
    "video": {
      "mimeType": "image/jpeg",
      "data": "<base64-encoded-jpeg>"
    }
  }
}
```

### 2.3. Server -> Client Messages (Server -> App)

| Message Type             | Payload Structure                               | Purpose                                                                  |
| ------------------------ | ----------------------------------------------- | ------------------------------------------------------------------------ |
| `GEMINI_CONNECTED`       | (no payload)                                    | Confirms WebSocket to Server OK; Gemini session initialization started.  |
| `GEMINI_DISCONNECTED`    | `{ "reason"?: string }`                         | Gemini session ended (client- or server-initiated).                      |
| `GEMINI_ERROR`           | `{ "message": string, "details"?: any }`       | An error occurred related to the Gemini API or server processing.        |
| `CONTENT_MESSAGE`        | `{ "serverContent": ServerContent }`           | Contains model output (text, image data) from Gemini.                   |
| `TOOL_CALL`              | `{ "toolCall": ToolCallDetails }`              | Gemini is requesting the client to execute a declared function.          |
| `TOOL_CALL_CANCELLATION` | `{ "toolCallCancellation": { "ids": string[] } }` | Model has cancelled a pending tool call.                                 |
| `SETUP_COMPLETE`         | `SetupCompleteDetails`                          | Confirms initial `LiveConfig` setup (or `UPDATE_CONFIG`) with Gemini.   |
| `INTERRUPTED`            | `InterruptedDetails?`                           | Current Gemini response turn was interrupted (e.g., by new user input).    |
| `TURN_COMPLETE`          | (no payload, or optional details)               | Model has completed its current response turn.                           |
| `AUDIO_CHUNK`            | `{ "data": string }` (Base64 encoded PCM)      | Audio data from Gemini (TTS output if `responseModalities` includes audio). |
| `WEBRTC_ANSWER`          | `{ "sdp": String }`                            | Send WebRTC SDP answer for WebRTC setup.                                 |
| `WEBRTC_ICE_CANDIDATE`   | `RTCIceCandidateJson`                           | Send a WebRTC ICE candidate.                                             |
| `LOG_MESSAGE`            | `LogMessagePayload`                             | Server-side log message (e.g., for debugging).                           |
| `ASSISTANT_SPEAKING`     | `{ "speaking": true }`                         | Indicates Gemini is actively speaking (audio is playing). Client should update overlay to 'Gemini is speaking...'. |
| `groundingMetadata`      | `{ "groundingMetadata"?: object }`             | (Optional) Contains Google Search Suggestion chip info.                 |

## 3. Core Data Structures

These are simplified representations. Refer to `@google/generative-ai` and server/client type definitions for exact structures.

### 3.1. `LiveConfig`

Used in `CONNECT_GEMINI` and `UPDATE_CONFIG`. Defines the session's behavior.

```typescript
interface LiveConfig {
  model: string;                   // e.g., "models/gemini-2.0-flash-live-001"
  systemInstruction?: Content;     // { parts: [{ text: "System prompt" }] }
  generationConfig?: GenerationConfig;
  tools?: Tool[];                  // [{ functionDeclarations: [...] }]
  safetySettings?: SafetySetting[];
}

interface GenerationConfig {
  temperature?: number;
  topK?: number;
  topP?: number;
  maxOutputTokens?: number;
  stopSequences?: string[];
  responseModalities?: Array<"text" | "audio" | "image">; // Crucial: "text" or "audio"
  speechConfig?: {
    voiceConfig: {
      prebuiltVoiceConfig: { voiceName: string; } // e.g., "Aoede"
    }
  }
}
```

### 3.2. `Content` and `Part`

Used for sending and receiving message content.

```typescript
interface Content {
  parts: Part[];
  role?: string; // "user", "model", "function"
}

interface Part {
  text?: string;
  inlineData?: {
    mimeType: string; // e.g., "image/jpeg", "image/png"
    data: string;     // Base64 encoded data
  };
  functionCall?: FunctionCall;     // Model requests a tool call
  functionResponse?: FunctionResponse; // Client provides tool result
  // Potentially other part types like fileData, videoMetadata, etc.
}
```

### 3.3. `GenerativeContentBlob` (for `SEND_REALTIME_INPUT`)

Used for streaming audio or video data.

```typescript
interface GenerativeContentBlob {
  mimeType: string;  // e.g., "audio/pcm;rate=16000", "image/jpeg"
  data: string;      // Base64 encoded chunk data
}
```
*Payload for `SEND_REALTIME_INPUT`: `{ "text"?: string, "audio"?: GenerativeContentBlob, "video"?: GenerativeContentBlob }`*

### 3.4. Tool-Related Structures

*   **`Tool` (in `LiveConfig`)**: Defines tools available to Gemini.
    ```typescript
    interface Tool {
      functionDeclarations?: FunctionDeclaration[];
      googleSearch?: {}; // Example of a built-in tool
    }

    interface FunctionDeclaration {
      name: string;
      description: string;
      parameters?: JsonSchema; // JSON schema for function arguments
    }
    ```

*   **`ToolCallDetails` (in `TOOL_CALL` payload from Server)**:
    ```typescript
    // Payload: { toolCall: ToolCallDetails }
    interface ToolCallDetails {
      functionCalls: LiveFunctionCall[]; // Renamed from FunctionCall in some docs
    }

    interface LiveFunctionCall { // Or simply FunctionCall depending on exact server type
      id: string;       // Unique ID for this specific call, used in response
      name: string;     // Function name to execute
      args: object;     // Arguments for the function
    }
    ```

*   **`ToolResponsePayload` (in `SEND_TOOL_RESPONSE` payload from Client)**:
    ```typescript
    // Payload: { toolResponse: ToolResponsePayload }
    interface ToolResponsePayload {
      functionResponses: FunctionResponseData[];
    }
    interface FunctionResponseData {
      id: string; // Corresponds to LiveFunctionCall.id
      name: string; // Name of the function/tool
      response: object; // Direct JSON object result from the tool
    }
    ```
    *Note: The previous structure using Content/Part/functionResponse nesting is deprecated. Use the flat structure above.*

*   **Example SEND_TOOL_RESPONSE message:**
    ```json
    {
      "type": "SEND_TOOL_RESPONSE",
      "payload": {
        "toolResponse": {
          "functionResponses": [
            {
              "id": "function-call-123",
              "name": "getCurrentTime",
              "response": { "currentTime": "2024-06-01T12:34:56Z" }
            }
          ]
        }
      }
    }
    ```

### 3.5. Server -> Client Message Payloads (Detailed)

*   **`ServerContent` (within `CONTENT_MESSAGE` payload)**:
    ```typescript
    // Payload: { serverContent: ServerContent }
    interface ServerContent {
      modelTurn?: Content; // Model's response (text, image)
      userTurn?: Content;  // Echo of user's input (less common in live)
      interrupted?: boolean;
      usageMetadata?: UsageMetadata;
      turnComplete?: boolean; // Can also be a separate TURN_COMPLETE message
    }
    ```

*   **`SetupCompleteDetails` (payload of `SETUP_COMPLETE`)**:
    ```typescript
    interface SetupCompleteDetails {
      success: boolean;
      error?: {
        code?: number;
        message?: string;
        details?: any;
      };
      // Other details like effectiveConfig might be included
    }
    ```

*   **`InterruptedDetails` (payload of `INTERRUPTED`)**:
    *   Often no payload, or might contain information about why the turn was interrupted.

*   **`LogMessagePayload` (payload of `LOG_MESSAGE`)**:
    ```typescript
    interface LogMessagePayload {
      type: "info" | "warn" | "error"; // Or other log levels
      message: string;
      details?: any;
    }
    ```

### 3.6. WebRTC Signaling Structures

*   **`RTCIceCandidateJson` (payload for `WEBRTC_ICE_CANDIDATE`)**:
    ```typescript
    interface RTCIceCandidateJson {
      candidate: string | null;
      sdpMid: string | null;
      sdpMLineIndex: number | null;
      // usernameFragment?: string | null; // Less common for basic exchange
    }
    ```
    *Sent by both client and server.*

*   **SDP Offer/Answer (payload for `WEBRTC_OFFER` / `WEBRTC_ANSWER`)**:
    `{ "sdp": String }` (String containing the SDP)

## 4. Common Operations Examples

(Refer to `GeminiLiveAPIServer_Overview.md` or specific client SDKs for more detailed examples of operations like sending messages, handling audio, etc.)

### 4.1. Establishing Connection & Initial Configuration

1.  Client opens WebSocket to `ws://server-ip:port`.
2.  On successful WebSocket `open` event, client sends:
    ```json
    {
      "type": "CONNECT_GEMINI",
      "payload": {
        "initialConfig": {
          "model": "models/gemini-2.0-flash-live-001",
          "systemInstruction": { "parts": [{ "text": "You are a helpful assistant." }] },
          "generationConfig": { "responseModalities": ["audio"] /* or ["text"] */ }
          /* Other LiveConfig parameters */
        }
      }
    }
    ```
3.  Client expects `GEMINI_CONNECTED` and then `SETUP_COMPLETE` from server.

### 4.2. Sending Text Message

```json
{
  "type": "SEND_MESSAGE",
  "payload": {
    "parts": [{ "text": "Hello, what is the weather today?" }],
    "turnComplete": true
  }
}
```

### 4.3. Streaming Microphone Audio (PCM)

Client continuously sends chunks as they are captured:

```json
{
  "type": "SEND_REALTIME_INPUT",
  "payload": {
    "audio": {
      "mimeType": "audio/pcm;rate=16000",
      "data": "base64EncodedAudioDataChunk1"
    }
  }
}
```

### 4.3a. Sending Real-time Text Input (e.g., Canvas UI)

Client sends a JSON-encoded string as real-time input:

```json
{
  "type": "SEND_REALTIME_INPUT",
  "payload": {
    "text": "{\"action\":\"button_click\",\"buttonId\":\"ok\"}"
  }
}
```

### 4.4. Responding to a Tool Call

If server sends `TOOL_CALL` with `functionCall.id = "call123"`, `functionCall.name = "get_weather"`, `functionCall.args = { "location": "London" }`:

Client executes `get_weather("London")` which returns e.g., `{"temperature": "15C", "condition": "Cloudy"}`.

Client sends:
```json
{
  "type": "SEND_TOOL_RESPONSE",
  "payload": {
    "toolResponse": {
      "functionResponses": [
        {
          "id": "call123",
          "name": "get_weather",
          "response": {
            "temperature": "15C",
            "condition": "Cloudy"
          }
        }
      ]
    }
  }
}
```

## 5. Implementation Notes

*   **Audio Format**: For audio input, ensure PCM audio (e.g., 16kHz, 16-bit mono). For audio output via WebSocket, expect Base64 encoded PCM (e.g., 24kHz). For WebRTC, Opus is typically used over the wire.
*   **Error Handling**: Implement robust reconnection logic for WebSockets. Handle `GEMINI_ERROR` messages.
*   **Resource Management**: Clean up resources (e.g., audio players/recorders, WebRTC connections) on disconnection or errors.
*   **Idempotency & Retries**: Consider strategies if network issues cause uncertainty about message delivery, though the Live API is stream-oriented.
*   **Message Size Limits**: Be aware of potential WebSocket message size limits if sending very large image data directly; chunking or alternative methods might be needed for extremely large assets (though `inlineData` is common for typical images).

This guide should provide a solid foundation for integrating with the Gemini Live API Server. Always refer to the latest server-side and Gemini API documentation for any updates to protocols or data structures. 

#### Overlay Status Handling
- When the client receives `ASSISTANT_SPEAKING`, it should update the status overlay to 'Gemini is speaking...'.
- When the client receives `TURN_COMPLETE` or `INTERRUPTED`, it should update the status overlay to 'Gemini is listening...'.
- The status overlay should never show 'Idle'.
- Canvas/tool UIs (e.g., from `create_canvas_tool` or `append_to_canvas_tool`) are managed separately and show content/interactions as defined by the tool's schema.

**Android Client Update [2024-06]:**
- The Android client now supports background tool calling:
    - Receives TOOL_CALL messages from the server.
    - Executes tools (including `create_canvas_tool`, `append_to_canvas_tool`) via a DI-driven ToolExecutor in the background service.
    - Sends SEND_TOOL_RESPONSE for both success and error results.
    - Example tools (`GetCurrentTimeTool`, `create_canvas_tool`, `append_to_canvas_tool`) are implemented and registered.
    - Tool UI is handled via schema-driven overlays managed by `ToolUIManager`, supporting separate status and canvas overlays. The canvas overlay is scrollable with a white background and aggregates content. 

## 10. Generalized ToolProxyActivity & Handler Pattern (Android Client)

For any tool actions that require a foreground UI (e.g., taking a photo, picking a contact), the Android client uses a generalized `ToolProxyActivity`.

- `ToolProxyActivity` is a generic activity that delegates tool actions to registered `ToolProxyHandler` implementations.
- Each tool type (e.g., camera/photo, contact picker) is implemented as a handler (e.g., `CameraToolProxyHandler`).
- The activity is SOLID-compliant, extensible, and future-proof: new tool types are added by implementing a handler and registering it.
- The camera/photo logic is now fully encapsulated in `CameraToolProxyHandler`, not in the activity itself.

This design ensures that all foreground-required tool actions are handled in a modular, maintainable, and extensible way. 

> **Update 2024-06:**
> The tool call/response protocol is now confirmed to work with both Node.js and Android clients. The required structure for tool responses is:
> 
> - Each object in `functionResponses` must have `id`, `name`, and `response` (the direct JSON object output from the tool).
> - This structure is required by the @google/genai SDK and is now implemented in both reference clients.

**As of 2024-06, the Android client is fully compatible with this protocol and can execute and respond to tool calls using this structure.** 

- **Google Search Suggestion Chips (groundingMetadata):**
    - If a CONTENT_MESSAGE includes a `groundingMetadata` field with `searchEntryPoint.renderedContent`, the client renders this as an HTML chip in the canvas overlay (using a WebView).
    - When the chip is clicked, the overlay is dismissed and the link opens in the browser.
    - This is handled via a ToolUiElement.Html element and is fully schema-driven and SOLID-compliant. 

## What's New [2024-06]
- All tool responses now use the flat `functionResponses` array with `id`, `name`, and direct JSON `response` (required by Gemini Live API and @google/genai SDK). The previous nested structure is deprecated.
- Canvas/tool overlays are always schema-driven, always include a dismiss/hide button, and support Google Search Suggestion chips (from `groundingMetadata`).
- Each tool can specify `defaultBehavior` (blocking or NON_BLOCKING) and `defaultScheduling` (WHEN_IDLE, INTERRUPT, SILENT), advertised in FunctionDeclaration.
- Tool chaining is supported via sequential TOOL_CALL messages from the server. The system is ready for future Gemini-driven context passing.
- The client checks for overlay permissions before displaying overlays, and falls back to notification or other UI if missing. Only whitelisted UI elements/actions are allowed.
- Overlays auto-dismiss after inactivity or on user request (if implemented).
- Foreground-required tool actions are handled by registering handlers in the ToolProxyActivity pattern, making the system modular and extensible.

### Tool Response Structure (Flat)
All tool responses **must** use the flat structure:
```json
{
  "type": "SEND_TOOL_RESPONSE",
  "payload": {
    "toolResponse": {
      "functionResponses": [
        {
          "id": "function-call-123",
          "name": "getCurrentTime",
          "response": { "currentTime": "2024-06-01T12:34:56Z" }
        }
      ]
    }
  }
}
```

### Overlay/Canvas UI and Google Search Suggestion Chips
- Canvas overlays are always schema-driven, scrollable, and always include a dismiss/hide button at the top (no need to add it to the schema).
- Overlays auto-dismiss after inactivity or on user request (if implemented).
- Only whitelisted UI elements/actions are allowed. The schema is extensible for new UI elements.
- If a CONTENT_MESSAGE includes `groundingMetadata.searchEntryPoint.renderedContent`, it is rendered as an HTML chip in the canvas overlay. Clicking it opens the link and dismisses the overlay. 

### Service-Centric Architecture
- The Android client is now fully service-centric. All chat/session logic is handled by the background service (`GeminiAssistantService`). The UI is focused on service control and status. The previous chat UI and `ChatViewModel` are deprecated and retained only for reference.

#### Protocol Note
- The `SEND_REALTIME_INPUT` payload must use the new fields (`audio`, `video`, `text`).
- The old `chunks`/`mediaChunks[]` field is deprecated and should not be used in new clients.
- See [@google/genai Session.sendRealtimeInput docs](https://googleapis.github.io/js-genai/release_docs/classes/live.Session.html#sendrealtimeinput) for details. 