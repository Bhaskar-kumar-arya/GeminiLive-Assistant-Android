# Gemini Live API Server - Comprehensive Overview

This document provides a comprehensive guide to the Gemini Live API WebSocket server, detailing its features, capabilities, and configuration for real-time, multimodal AI interaction.

## What's New [2024-06]
- All tool responses now use the flat `functionResponses` array with `id`, `name`, and direct JSON `response` (required by Gemini Live API and @google/genai SDK). The previous nested structure is deprecated.
- Canvas/tool overlays are always schema-driven, always include a dismiss/hide button, and support Google Search Suggestion chips (from `groundingMetadata`).
- Each tool can specify `defaultBehavior` (blocking or NON_BLOCKING) and `defaultScheduling` (WHEN_IDLE, INTERRUPT, SILENT), advertised in FunctionDeclaration.
- Tool chaining is supported via sequential TOOL_CALL messages from the server. The system is ready for future Gemini-driven context passing.
- The client checks for overlay permissions before displaying overlays, and falls back to notification or other UI if missing. Only whitelisted UI elements/actions are allowed.
- Overlays auto-dismiss after inactivity or on user request (if implemented).
- Foreground-required tool actions are handled by registering handlers in the ToolProxyActivity pattern, making the system modular and extensible.
- The Android client is now fully service-centric, with all chat/session logic handled by the background service (`GeminiAssistantService`).

## 1. Architecture

The Gemini Live API Server acts as a secure bridge between client applications (mobile, web) and Google's Gemini API. This architecture offers several advantages:

*   **API Key Security**: The Gemini API key is managed securely on the server, not exposed in client applications.
*   **Simplified Client Integration**: Clients interact via a WebSocket protocol, abstracting away direct Gemini API complexities.
*   **Centralized Logic**: Facilitates consistent feature implementation and updates.

```
┌───────────┐     WebSocket     ┌────────────────┐     WebSocket     ┌────────────┐
│ Client    │ <---------------> │ Gemini Live    │ <---------------> │ Gemini     │
│ (App)     │     Connection    │ API Server     │     Connection    │ Live API   │
└───────────┘                   └────────────────┘                   └────────────┘
```

## 2. Core Features

The server supports a rich set of features for building interactive AI experiences:

### 2.1. Multimodal Conversations

Engage with the Gemini model using various input and output types:

*   **Text Interaction**:
    *   Stream text responses character by character.
    *   Support for markdown formatting (code, tables, lists) in general model responses. Note: Specific tools, like canvas UI tools, might define their own text formatting rules (e.g., HTML).
    *   Progressive text appearance for a natural feel.
*   **Image Processing**:
    *   Send images (JPEG, PNG; recommended max 1024x1024, auto-resized) as part of messages.
    *   Model can analyze, describe, and reference image content.
    *   Use cases: product identification, scene description, document analysis.
*   **SEND_REALTIME_INPUT**:
    *   **Payload**: `{ "text"?: string, "audio"?: GenerativeContentBlob, "video"?: GenerativeContentBlob }`
    *   **Description**: Stream real-time text (e.g., from canvas UI), audio, or video (image frame). **Note:** The old `chunks`/`mediaChunks[]` field is **deprecated** and should not be used. Use `audio`, `video`, and `text` fields instead.

### DEPRECATION NOTICE: `chunks`/`mediaChunks[]` in SEND_REALTIME_INPUT
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

### 2.2. Voice Streaming & Audio Conversations

Enable natural voice-based interactions:

*   **Bidirectional Audio**:
    *   **Input (Client to Gemini)**: Stream real-time voice input from client microphone (typically 16kHz PCM) using the 'chunks' field in SEND_REALTIME_INPUT. For real-time text input (e.g., from canvas UI), use the 'text' field.
    *   **Output (Gemini to Client - Text-to-Speech)**: Receive text-to-speech audio responses from a selection of voices (typically 24kHz PCM via WebSocket, or Opus over WebRTC).
*   **Voice Options**: A selection of distinct voice personalities are available (e.g., Aoede, Puck, Charon, Kore, Fenrir), configurable in `LiveConfig`.
*   **Audio Quality**: PCM format (16kHz for input, 24kHz for WebSocket output) balanced for quality and bandwidth. Consistent audio chunk sizes (e.g., 100-200ms) are recommended for WebSocket streaming.

### 2.3. WebRTC Audio Streaming (Gemini Audio to Client)

For potentially lower latency and improved audio quality for Gemini's responses, the server supports streaming audio back to the client using WebRTC.

*   **Process**:
    1.  Receives 24kHz, 16-bit mono PCM audio from Gemini API via WebSocket.
    2.  Resamples the audio to 48kHz, 16-bit mono PCM.
    3.  Processes the 48kHz PCM data into 10ms frames.
    4.  Utilizes `@roamhq/wrtc` library's `RTCAudioSource` to handle Opus encoding of these PCM frames.
    5.  Streams the Opus encoded audio to the connected client via a WebRTC AudioTrack.
*   **Paced Delivery**: A server-side queue and pacer mechanism ensures smooth, sequential 10ms audio frame delivery to the client, preventing packet bursts and audio overlap.
*   **Benefits**: Leverages WebRTC for real-time media, efficient Opus compression, and potentially improved resilience.

### 2.4. Custom Tool Integration (Function Calling)

Extend Gemini's capabilities by defining custom functions the model can invoke:

*   **Declaration**: Tools are defined in `LiveConfig` with a name, description, and JSON Schema for parameters.
*   **Execution Flow**:
    1.  Model identifies a need for a tool.
    2.  Server sends `TOOL_CALL` message to the client.
    3.  Client executes the function.
    4.  Client sends `SEND_TOOL_RESPONSE` with the result, using the flat structure:
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
    5.  Model incorporates the result into its response.
*   **Capabilities**: Supports nested functions, function chaining, error handling, and `TOOL_CALL_CANCELLATION`.
*   **Use Cases**: Location services, device integration, external API calls, data processing.

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

### Service-Centric Client Architecture
- The Android client is now fully service-centric. All chat/session logic is handled by the background service (`GeminiAssistantService`). The UI is focused on service control and status. The previous chat UI and `ChatViewModel` are deprecated and retained only for reference.

### 2.5. Session Management

Maintain coherent, contextual interactions:

*   **Conversation Context**: Model maintains context of the entire conversation history.
*   **Session Persistence**: WebSocket connection maintains session state. Reconnection typically requires re-establishing context.
*   **Interruption Handling**: Supports graceful interruptions (client or server-initiated) via `INTERRUPTED` messages or flags.

### 2.6. Dynamic Configuration

Adjust model behavior and capabilities at runtime during an active session:

*   **Updateable Parameters**: Model selection, system instructions, response modalities, voice settings, tool availability via `LiveConfig`.
*   **Process**: Client sends an `UPDATE_CONFIG` message with the new `LiveConfig`.
*   **Effects**: Some changes might require session restart (often handled automatically by the server); others apply immediately.

### Overlay Status Protocol
- The server sends an `ASSISTANT_SPEAKING` message before Gemini audio playback begins.
- The client updates its status overlay to 'Gemini is speaking...' on this message.
- When the client receives `TURN_COMPLETE` or `INTERRUPTED`, it updates the status overlay to 'Gemini is listening...'.
- Canvas/tool UIs (e.g., from `create_canvas_tool` or `append_to_canvas_tool`) are managed separately by the client based on tool schemas and do not directly involve specific server messages beyond the standard `TOOL_CALL` and `SEND_TOOL_RESPONSE` flow.

### 3.3. GenerativeContentBlob (for SEND_REALTIME_INPUT)

Used for streaming audio or video data. The SEND_REALTIME_INPUT payload is now:

```typescript
{
  text?: string; // For real-time text input (e.g., from canvas UI)
  audio?: GenerativeContentBlob; // For audio
  video?: GenerativeContentBlob; // For video/image
}
```

#### Protocol Note
- The `SEND_REALTIME_INPUT` payload must use the new fields (`audio`, `video`, `text`).
- The old `chunks`/`mediaChunks[]` field is deprecated and should not be used in new clients.
- See [@google/genai Session.sendRealtimeInput docs](https://googleapis.github.io/js-genai/release_docs/classes/live.Session.html#sendrealtimeinput) for details.

## 3. Configuration (`LiveConfig`)

The `LiveConfig` object is crucial for initializing and updating the Gemini session.

```typescript
interface LiveConfig {
  model: string;                   // e.g., "models/gemini-2.0-flash-live-001"
  systemInstruction?: Content;     // Sets AI behavior/persona (e.g., { parts: [{ text: "You are a helpful assistant." }] })
  generationConfig?: {
    temperature?: number;          // Controls randomness (0.0-1.0)
    topK?: number;                 // Limits token selection pool
    topP?: number;                 // Nucleus sampling parameter
    maxOutputTokens?: number;      // Limits response length
    stopSequences?: string[];      // Triggers early response completion
    responseModalities?: Array<"text" | "audio" | "image">; // e.g., ["audio"] or ["text"]
    speechConfig?: {
      voiceConfig: {
        prebuiltVoiceConfig: {
          voiceName: string;       // e.g. "Aoede", "Puck", "Charon", "Kore", "Fenrir"
        }
      }
    }
  };
  tools?: Tool[];                  // Available functions for the model to call (e.g., [{ googleSearch: {} }, { functionDeclarations: [...] }])
  safetySettings?: SafetySetting[]; // Harm categories and blocking thresholds
}

// Example Tool Structure within LiveConfig.tools
// {
//   "functionDeclarations": [
//     {
//       "name": "get_current_time",
//       "description": "Get current device time."
//     },
//     {
//       "name": "create_canvas_tool",
//       "description": "Creates a new canvas overlay. Args: {schema: JSON_STRING}. Text elements within the schema support HTML formatting.",
//       "parameters": { /* ... schema for args ... */ }
//     },
//     {
//       "name": "append_to_canvas_tool",
//       "description": "Appends to existing canvas. Args: {schema: JSON_STRING}. Text elements within the schema support HTML formatting.",
//       "parameters": { /* ... schema for args ... */ }
//     }
//     // ... other tools ...
//   ]
// }

// Example SafetySetting Structure
// {
//   "category": "HARM_CATEGORY_HARASSMENT",
//   "threshold": "BLOCK_MEDIUM_AND_ABOVE"
// }
```

Refer to the official Gemini documentation for the most up-to-date and complete list of `LiveConfig` parameters.

## 4. Supported Models

Various Gemini models can be used, each with different capabilities and performance characteristics. Examples include:

| Model                              | Capabilities                                     | Best For                                  |
| ---------------------------------- | ------------------------------------------------ | ----------------------------------------- |
| `models/gemini-2.0-flash-live-001` | Optimized for Live API, supports audio responses | Real-time voice, general queries          |
| `models/gemini-1.5-flash`          | Faster response times, multimodal                | Quick general queries, chatbots           |
| `models/gemini-1.5-pro`            | Most capable, comprehensive understanding        | Complex tasks, creative content generation |
| `models/gemini-pro`                | General purpose                                  | Text-based tasks, varied compatibility    |
| `models/gemini-2.0-vision`         | Advanced image understanding                     | Visual analysis, multimodal interactions  |

*Note: Model availability and specific "live" or "exp" tags can change. Always refer to official Gemini documentation for current model names.*

## 5. Safety Settings

Control content filtering for various harm categories:

*   **Harm Categories**: Hate speech, harassment, sexually explicit, dangerous content.
*   **Threshold Levels**: `BLOCK_NONE`, `BLOCK_LOW_AND_ABOVE`, `BLOCK_MEDIUM_AND_ABOVE`, `BLOCK_ONLY_HIGH`.
*   These are configured within the `safetySettings` array in `LiveConfig`.

## 6. Performance and Implementation Considerations

*   **Mobile Network Optimization**:
    *   Implement robust reconnection strategies for WebSockets.
    *   Balance audio quality vs. bandwidth. Client-side silence detection can reduce unnecessary audio transmission to the server.
    *   Optimize image sizes before sending.
*   **Server Capacity**:
    *   Plan for concurrent connections.
    *   Monitor memory usage, especially with audio buffering.
*   **Audio Chunking**: For WebSocket audio streaming, send audio in appropriately sized chunks (e.g., 100-200ms) for a balance between latency and overhead.
*   **Context Management**: For long conversations, consider techniques like summarizing previous context if nearing model context limits. Provide clear initial system instructions.

## 7. Development Utilities & Testing

The ecosystem may include:

*   **Interactive Chat Clients/Consoles**: For testing basic connectivity, text, and audio interactions.
*   **Specialized Test Clients**: For verifying specific features like configuration updates or streaming.
*   **Debugging Tools**: Logging and message inspection capabilities on the server and client.

## 8. Error Handling and Resilience

*   **Client-Side**: Implement connection retry mechanisms, detection of setup failures, and user notifications.
*   **Server-Side**: Robust WebSocket error handling, connection cleanup, API key validation, and error propagation to clients.

## 9. Future Considerations

Potential enhancements often include:
*   Persistent session management and conversation history.
*   Enhanced security with authentication and access controls.
*   Horizontal scaling for multiple concurrent clients.
*   Metrics collection and performance monitoring.
*   Extended tool execution environments.

## Background Tool Calling (Android Client Update 2024-06)

- The Android client now supports background tool calling:
    - Receives TOOL_CALL messages from the server.
    - Executes tools (including `create_canvas_tool`, `append_to_canvas_tool`) via a DI-driven ToolExecutor in the background service.
    - Sends SEND_TOOL_RESPONSE for both success and error results.
    - Example tools (`GetCurrentTimeTool`, `create_canvas_tool`, `append_to_canvas_tool`) are implemented and registered.
    - Tool UI is handled via schema-driven overlays managed by `ToolUIManager`, supporting separate status and canvas overlays. The canvas overlay is scrollable with a white background and aggregates content.

*   **CONTENT_MESSAGE**: Now includes an optional `groundingMetadata` field (if present in Gemini API response). This allows clients to render Google Search Suggestion chips and other grounding information in their UI overlays. For example, if `groundingMetadata.searchEntryPoint.renderedContent` is present, the client can display it as an HTML chip in a WebView or similar component. This enables seamless integration of Google Search Suggestion chips in the client experience. 