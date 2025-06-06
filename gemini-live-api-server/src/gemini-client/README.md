# Gemini Client Logic for Node.js Server

This directory contains the core client logic adapted from the `@live-api-web-console` project to enable a Node.js server to communicate with the Gemini Live API.

## Files

*   **`multimodal-live-client.ts`**:
    *   This is an adapted version of the original `MultimodalLiveClient`.
    *   It uses the `ws` library for WebSocket communication in a Node.js environment.
    *   Browser-specific APIs have been removed or replaced with Node.js equivalents.
    *   The `receive` method handles `Buffer` objects from the `ws` library.
    *   It emits a comprehensive set of events (e.g., `open`, `close`, `content`, `toolcall`, `audio`, `setupcomplete`) reflecting interactions with the Gemini API.
    *   Includes a `setConfig` method to dynamically update the Gemini connection configuration.
    *   Depends on `multimodal-live-types.ts` and `utils.ts`.

*   **`multimodal-live-types.ts`**:
    *   This file defines all the TypeScript types and type guards for messages exchanged with the Gemini Live API.
    *   It is crucial for ensuring type safety and correct message parsing/construction.
    *   Updated to include detailed `SetupCompleteMessage` (with `success` and `error` fields) and reflect that `ToolCallCancellationMessage` uses an array of `ids`.
    *   Requires `@google/generative-ai` for some base types.

*   **`utils.ts`**:
    *   This file contains utility functions adapted for the Node.js environment, such as `bufferToJSON` and `base64ToArrayBuffer`.
    *   Browser-specific code has been removed.

## Dependencies

The logic in this directory relies on the following external npm packages (which should be installed in the main server project):

*   `@google/generative-ai`: For core Gemini API types.
*   `eventemitter3`: Used by `MultimodalLiveClient` for its event-driven architecture.
*   `ws`: The WebSocket client library for Node.js.
*   `lodash`: Specifically the `difference` function is used in `multimodal-live-client.ts`.
*   `uuid` (Note: `uuid` is a dependency of the server, used in `gateway.ts`, not directly by files in this directory, but relevant for the overall client interaction flow).

## Current Status (as of completing Step 6 of Development Plan)

*   Core files (`multimodal-live-client.ts`, `multimodal-live-types.ts`, `utils.ts`) have been adapted for Node.js.
*   `multimodal-live-client.ts` is refactored for `ws`, handles Gemini events, includes `setConfig`, and emits detailed `setupcomplete` data.
*   `multimodal-live-types.ts` reflects the accurate structure of `SetupCompleteMessage` and `ToolCallCancellationMessage`.
*   `utils.ts` contains Node.js specific utility functions.

## Integration with `src/gateway.ts`

The `MultimodalLiveClient` and its associated types are primarily instantiated and utilized by `src/gateway.ts`. The gateway acts as the central nervous system for the WebSocket server:

*   It manages WebSocket connections from external client applications, assigning each a unique ID.
*   For each connected client app, it creates and manages a dedicated `MultimodalLiveClient` instance to interact with the Gemini API.
*   It defines and handles a specific WebSocket protocol (see `src/websocket-protocol-types.ts`) for communication between the client app and the server.
*   **Message Routing (Client App -> Gateway -> Gemini):**
    *   Receives messages from the client app (e.g., to connect, send text/audio, provide tool responses, update config).
    *   Invokes the appropriate methods on the corresponding `MultimodalLiveClient` instance (e.g., `connect()`, `send()`, `sendRealtimeInput()`, `sendToolResponse()`, `setConfig()`).
*   **Event Forwarding (Gemini -> MultimodalLiveClient -> Gateway -> Client App):**
    *   Listens for events emitted by the `MultimodalLiveClient` (e.g., `open`, `close`, `content`, `toolcall`, `audio`, `setupcomplete`, `interrupted`, `turncomplete`).
    *   Formats these events into the server-to-client WebSocket protocol.
    *   Transmits these formatted messages to the correct client app. For instance, audio data (`ArrayBuffer`) from Gemini is converted to a base64 string before being sent in an `AUDIO_CHUNK` message.

This separation of concerns ensures that the `gemini-client` module remains focused on the direct, low-level communication with the Gemini API, while `gateway.ts` handles the higher-level application logic of managing multiple clients, message translation, and session orchestration. 