# Migration to @google/genai Live API

This document provides a step-by-step checklist for migrating your Node.js server from the legacy `@google/generative-ai` SDK to the new [`@google/genai`](https://www.npmjs.com/package/@google/genai) SDK, using the SDK's built-in Live API support. The approach is to start with a minimal working example, then incrementally port message routing and event handling logic, testing each step thoroughly before moving to the next.

---

## References
- [@google/genai NPM](https://www.npmjs.com/package/@google/genai)
- [Official SDK Docs](https://googleapis.github.io/js-genai/)
- [Live API Documentation](https://ai.google.dev/gemini-api/docs/live)
- [Migration Guide](https://cloud.google.com/vertex-ai/generative-ai/docs/migrate/migrate-google-ai)

---

## 1. Preparation ✅

- [x] **Review the [Live API documentation](https://ai.google.dev/gemini-api/docs/live)** to understand the new streaming/session model.
- [x] **Backup your codebase** before making any changes.
- [x] **Audit all usages** of `@google/generative-ai` in your codebase (especially in `src/gemini-client/` and `src/gateway.ts`).

---

## 2. Update Dependencies ✅

- [x] Remove the old SDK:
  ```sh
  npm uninstall @google/generative-ai
  ```
- [x] Install the new SDK:
  ```sh
  npm install @google/genai
  ```
- [x] Update all imports in the codebase to use `@google/genai` instead of `@google/generative-ai`.
- [x] Resolve initial linter/type errors related to SDK migration.

---

## 3. Minimal Working Example: Live API ✅

A minimal working example using the new SDK is implemented in `gemini-live-api-server/interactive-chat.js`.

### How to Run the Minimal Working Example

1. **Set up your environment:**
   - Ensure you have a valid `GEMINI_API_KEY` in a `.env` file at the root of `gemini-live-api-server`:
     ```
     GEMINI_API_KEY=your_actual_gemini_api_key_here
     ```
2. **Install dependencies:**
   ```sh
   npm install
   ```
3. **Run the test script:**
   ```sh
   node gemini-live-api-server/interactive-chat.js
   ```
   - This will connect to the Gemini Live API, send a test message, and print the response.

#### Troubleshooting
- If you see errors about a missing API key, double-check your `.env` file.
- If you see errors about missing modules, run `npm install` from the project root.
- Ensure your API key has access to the `gemini-2.0-flash-live-001` model.

---

## 4. Incremental Migration Steps

### 4.1. Update Imports and Types
- [x] Replace all imports from `@google/generative-ai` with `@google/genai`.
- [x] Update all type usages (`Content`, `Part`, `Tool`, etc.) to use the new SDK's types.

### 4.2. Refactor Gemini Client Logic
- [x] Remove or refactor custom WebSocket handling in `multimodal-live-client.ts` to use the SDK's built-in Live API support. (New client `multimodal-live-client-genai.ts` created)
- [x] Replace manual event emitters with the SDK's callback/event system. (Implemented in `multimodal-live-client-genai.ts`)
- [x] Use the `session.sendClientContent` and related methods for sending messages. (Implemented for text in `multimodal-live-client-genai.ts`)
- [x] Use the `session.sendRealtimeInput` for sending audio. (Implemented in `multimodal-live-client-genai.ts`)
- [x] Handle streaming, tool calls, and function responses using the SDK's callbacks. (Basic content streaming and event emission in place in `multimodal-live-client-genai.ts`. Tool call/function response handling will be verified during gateway integration.)

**Status:** The new `GenaiLiveClient` in `multimodal-live-client-genai.ts` successfully uses the `@google/genai` SDK. Basic text sending (`send`) and realtime audio input (`sendRealtimeInput`) have been tested directly with a test script (`test-genai-live-client.ts`) and are functional. The client emits events compatible with the existing gateway structure.

### 4.3. Update Protocol and Message Routing
- [ ] Refactor your server's message routing (in `gateway.ts`) to use the new session and event model.
- [ ] Ensure all messages to/from the mobile client are still compatible, or update the client protocol if needed.
- [ ] Update any custom types in `websocket-protocol-types.ts` to match the new SDK's types and message structures.

### 4.4. Test Each Step
- [ ] After each incremental change, run your server and test:
  - Text, audio, and multimodal generation
  - Tool calling and function response
  - Streaming and real-time input/output
  - Error handling, reconnection, and session management

---

## 5. Security and API Key Handling
- [ ] Ensure API keys are **never exposed to the client**.
- [ ] For production, consider using Vertex AI with a service account for better security ([see guide](https://cloud.google.com/vertex-ai/generative-ai/docs/migrate/migrate-google-ai)).

---

## 6. Regression and Load Testing
- [ ] Run all existing regression tests.
- [ ] Add/expand tests for new SDK features (streaming, tool calling, etc.).
- [ ] Perform load testing to ensure the new SDK meets your performance requirements.

---

## 7. Documentation and Rollout
- [ ] Update internal documentation to reflect the new SDK usage.
- [ ] Communicate any protocol changes to the mobile client team.
- [ ] Plan a staged rollout and monitor for issues.

---

## 8. Cleanup
- [ ] Remove any unused legacy code, types, or dependencies.
- [ ] Ensure all code is using the new SDK and is well-documented.

---

## 9. Additional Notes
- Only one response modality (`TEXT` or `AUDIO`) can be set per session ([see Live API docs](https://ai.google.dev/gemini-api/docs/live)).
- The Live API is intended for server-side use; never expose API keys in client-side code.
- For advanced features (tool calling, session resumption, etc.), see the [Live API documentation](https://ai.google.dev/gemini-api/docs/live).

---

## 10. Current Status & Outstanding Issues (2024-06)

### Summary of Recent Changes
- The server has been fully migrated to use the new `@google/genai` SDK and the `GenaiLiveClient` for all Gemini API interactions.
- All message routing in `gateway.ts` has been updated to use the new session/event model and protocol, including correct handling of `SEND_REALTIME_INPUT` payloads.
- End-to-end audio input from the Android client (via WebRTC, resampled to 16kHz by the server) to Gemini has been confirmed as working.
- The server correctly handles WebRTC signaling, audio resampling from client, and audio queueing for WebRTC audio output from Gemini.

### Current Issue: Gemini Audio Response with Android Client's `LiveConfig`
- While the Android client successfully sends audio to Gemini, and the server can receive audio *from* Gemini, a crucial issue remains with how Gemini chooses its response modality.
- **Observation**: When the `gemini-live-api-server` uses a **minimal, hardcoded `LiveConfig`** that explicitly requests `responseModalities: ['audio']` and a specific voice (e.g., "Aoede"), Gemini **correctly sends back an audio response** which the Android client can play.
- However, when the server uses the **`LiveConfig` sent by the Android client**, Gemini defaults to sending a **text-only response**, even if the client's config also specifies `responseModalities: ['audio']`.
- This strongly suggests that some part of the more complex `LiveConfig` generated by the Android client (beyond just the `responseModalities` and `speechConfig`) is causing Gemini to override or ignore the request for an audio response. The previous issue concerning "no audio received from Android device" has been resolved, as audio input is now functional.

### Next Steps
- **Isolate the `LiveConfig` Discrepancy**:
    - Obtain the exact JSON representation of the default `LiveConfig` being sent by the Android client.
    - Compare this client-generated `LiveConfig` with the minimal server-side hardcoded `LiveConfig` that successfully elicits an audio response.
    - Systematically test variations of the client's `LiveConfig` (e.g., by commenting out sections or simplifying parameters on the client-side, or by temporarily modifying it on the server if the client can't be changed quickly) to pinpoint which specific parameter(s) or structural differences are causing Gemini to default to text.
- **Correct Android Client's `LiveConfig`**:
    - Once the problematic element(s) in the client's `LiveConfig` are identified, modify the Android client's `GeminiAssistantService.kt` (or relevant config generation logic) to produce a `LiveConfig` that reliably results in audio responses from Gemini.
- **Revert Server-Side Hardcoding**:
    - After the client's `LiveConfig` is confirmed to work correctly for audio responses, remove the temporary hardcoded `LiveConfig` from `gemini-live-api-server/src/gemini-client/multimodal-live-client-genai.ts` and allow the server to use the configuration provided by the client via the `CONNECT_GEMINI` message.
- This focused approach will ensure that the desired audio interaction is consistently achieved with the client's intended configuration.

---

## 10.1. Tool Call/Response Integration Status (2024-06)

### Current Status
- The server now passes `tools` from the client-provided `LiveConfig` to the Gemini SDK, enabling Gemini tool calling.
- When Gemini issues a tool call (e.g., `create_canvas_tool`), the server relays this to the client, and the client executes the tool and sends a tool response back to the server.
- The server then sends the tool response to Gemini. Example tool response payload:

```json
{
  "parts": [
    {
      "text": null,
      "inlineData": null,
      "functionResponse": {
        "name": "create_canvas_tool",
        "response": {
          "acknowledged": true
        },
        "scheduling": "INTERRUPT"
      }
    }
  ],
  "role": "function"
}
```

### Next Steps / Issues
- Confirm that the tool response structure matches Gemini's expectations for function calling (see SDK and API docs for latest requirements).
- If Gemini does not process the tool response as expected, compare the payload with the official documentation and adjust as needed.
- Continue tool call/response integration and testing in a new thread as planned.

---

**Current Status:**
- Steps 1, 2, and the minimal working example are complete.
- The codebase is now ready for further migration and feature work using the new SDK.

**By following this checklist, you will ensure a smooth, future-proof migration to the latest Google GenAI SDK with Live API support.** 