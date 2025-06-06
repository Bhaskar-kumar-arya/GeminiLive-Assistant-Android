# Phase 4: Background Tool Calling Framework & Specific Tools - Detailed Tasks

## What's New [2024-06]
- All tool responses now use the flat `functionResponses` array with `id`, `name`, and direct JSON `response` (required by Gemini Live API and @google/genai SDK). The previous nested structure is deprecated.
- Canvas/tool overlays are always schema-driven, always include a dismiss/hide button, and support Google Search Suggestion chips (from `groundingMetadata`).
- Each tool can specify `defaultBehavior` (blocking or NON_BLOCKING) and `defaultScheduling` (WHEN_IDLE, INTERRUPT, SILENT), advertised in FunctionDeclaration.
- Tool chaining is supported via sequential TOOL_CALL messages from the server. The system is ready for future Gemini-driven context passing.
- The service checks for `SYSTEM_ALERT_WINDOW` permission before displaying overlays, and falls back to notification or other UI if missing. Only whitelisted UI elements/actions are allowed. All overlays are clearly branded and easy to dismiss.
- Overlays auto-dismiss after inactivity or on user request (if implemented).
- Foreground-required tool actions are handled by registering handlers in the ToolProxyActivity pattern, making the system modular and extensible.

This document provides a detailed breakdown of the tasks for Phase 4, focusing on implementing a robust, SOLID-compliant framework for Gemini to call functions executed by the `GeminiAssistantService`. All UI overlays (canvas or otherwise) are triggered by tool calls, and Gemini can chain GUI tools with other tools as needed. The system is designed for future extensibility, including using UI tools in combination with other tool calls.

*   **Goal**: Implement a robust framework for Gemini to call functions (including GUI/canvas tools) executed by the `GeminiAssistantService`, using System Alert Windows for any necessary UI, and enable chained tool usage. All UI overlays are schema-driven and triggered via the tool calling protocol only.
*   **Key Principles:**
    *   All UI overlays (canvas or otherwise) are triggered by tool calls (no special-case logic).
    *   GUI/canvas tools are first-class tools, callable by Gemini via TOOL_CALL, with or without a canvas, and composable with other tools.
    *   The framework is SOLID-compliant and future-proof for new UI/tool types and chaining.

*   **Tasks:**
    1.  **Tool Declaration & Configuration in Service**:
        *   [x] **1.1. Service-Side `LiveConfig` Update**: The `GeminiAssistantService` now constructs `LiveConfig` with all supported native and GUI tools when sending `CONNECT_GEMINI`. (Includes `create_canvas_tool`, `append_to_canvas_tool`).
            *   **Update [2024-06]:** Tool descriptions and parameter schemas are now sourced directly from each tool's implementation (the `NativeTool` class). The service dynamically generates tool declarations for `LiveConfig` using the `name`, `description`, and `parametersJsonSchema` properties of each tool. This ensures that all tool documentation, usage instructions, and schemas are always up to date and are maintained in a single place (the tool class itself).
        *   [x] **1.2. Define `ToolDtos.kt`**: DTOs for tool calls and responses are aligned and ready for service-based execution.
    2.  **Service-Centric Tool Handling System**:
        *   [x] **2.1. Define `NativeTool.kt` Interface**: SOLID-compliant interface and result sealed class are implemented.
        *   [x] **2.2. Implement `ToolExecutor.kt` in Service**: ToolExecutor is provided via Hilt DI and dispatches tool calls.
    3.  **Integrate Tool Handling into `GeminiAssistantService`**:
        *   [x] **3.1. Process `TOOL_CALL` Messages**: Service processes TOOL_CALL, executes tools (including GUI/canvas tools like `create_canvas_tool` and `append_to_canvas_tool`), and sends SEND_TOOL_RESPONSE for both success and error.
        *   [x] **3.2. Send `SEND_TOOL_RESPONSE`**: Implemented for both success and error. NeedsConfirmation/tool UI is handled via schema-driven overlays.
    4.  **System Alert Window UI for Tools & Generalized ToolProxyActivity (Handler Pattern):**
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

                * i added a test tool to see how GUI would look
    5.  **Implement Specific Native and GUI Tools (Executed by Service)**:
        *   (Each will implement `NativeTool` and be provided via Hilt to the `ToolExecutor` in the service)
        *   [x] **5.1. `GetCurrentTimeTool`**:
            *   Returns current formatted time.
            *   No System Alert Window UI needed. `ToolExecutionResult.Success`.
        *   [x] **5.2. `CreateCanvasTool` (GUI/Canvas Tool):**
            *   Name: `create_canvas_tool`
            *   Description: Creates a new scrollable canvas overlay (destroying any previous canvas). Use this to start a new UI sheet for the user. The 'schema' argument MUST be a JSON string (all keys and string values use double quotes) describing the initial elements to display. Supported element types: text (supports HTML like '<b>bold</b>', '<i>italic</i>'), image, input, button. Call this before using `append_to_canvas_tool`.
            *   Argument: `schema` (JSON string representing ToolUiSchema, e.g., `{"type":"canvas","elements":[{"type":"text","value":"Hello <b>World</b>!"}]}`)
            *   Behavior: Clears any existing canvas and displays the new elements in a scrollable view with a white background.
            *   Returns user input or acknowledgment as needed via callback.
        *   [x] **5.3. `AppendToCanvasTool` (GUI/Canvas Tool):**
            *   Name: `append_to_canvas_tool`
            *   Description: Appends new elements to the current canvas overlay (previously created with `create_canvas_tool`). Use this to add more UI elements below the existing ones. The 'schema' argument MUST be a JSON string (all keys and string values use double quotes) describing the elements to append. Supported element types: text (supports HTML like '<b>bold</b>', '<i>italic</i>'), image, input, button. Do not use this if a canvas has not been created yet.
            *   Argument: `schema` (JSON string representing ToolUiSchema, e.g., `{"type":"canvas","elements":[{"type":"text","value":"Another <i>message</i>."}]}`)
            *   Behavior: Adds the new elements to the bottom of the existing scrollable canvas.
            *   Returns user input or acknowledgment as needed via callback.
        *   [x] **5.4. `TakePhotoTool`**:
            *   Uses an `Intent` to a camera app.
            *   If using Intent, result comes back to the service (needs careful handling of `onActivityResult` or `ActivityResultLauncher` adapted for service).
            *   Handles camera permissions (service might need to request permission using a overlaywindow).
            *   Returns image URI . `ToolExecutionResult.Success`
        *   [x] **5.5. `FindContactTool`**:
            *   Uses `ContentResolver` from `serviceContext`.
            *   Handles contacts permissions.
            *   If multiple contacts match, might use `SYSTEM_ALERT_WINDOW` for disambiguation (e.g., show a short list). `ToolExecutionResult.Success` or `NeedsConfirmation`.
            *   Returns contact details.
        *   [x] **5.6. `SendMessageTool`**:
            *   Uses `Intent.ACTION_SEND` (or specific app URI schemes) launched from `serviceContext`.
            *   May require `SYSTEM_ALERT_WINDOW` for confirmation before sending, or if user input is needed for the message body if not provided by Gemini.
            *   Handles relevant permissions. `ToolExecutionResult.Success` or `NeedsConfirmation`.
    6.  **Tool Chaining & Context Management in Service**:
        *   [ ] **6.1. Design Tool Chaining Strategy**: Determine how the `GeminiAssistantService` will manage a sequence of tool calls from Gemini for a single user intent (e.g., "Take a photo and show it in a canvas, then send it to John Doe").
            *   Option A: Server sends sequential `TOOL_CALL` messages, service executes them one by one.
            *   Option B: Client-side (service) maintains short-term state if Gemini implies a chain, then sends individual results back. *Server-driven is often simpler.*
        *   [ ] **6.2. Contextual Data for Chained Tools**: Ensure data from one tool (e.g., photo URI) can be passed to a subsequent tool (e.g., send message tool) as part of Gemini's arguments.
    7.  **UI/UX for Background Tool Activity**:
        *   [ ] **7.1. Notification Updates**: The service's persistent notification should clearly indicate when a tool is being executed in the background (e.g., "Taking photo...", "Showing canvas...", "Sending message...").
        *   [ ] **7.2. System Alert UI Standards**: Ensure any System Alert Window UIs are minimal, non-intrusive, clearly branded as the assistant, and easy to dismiss.

---

**Notes:**
- GUI/canvas tools can be called with or without a canvas (e.g., just a single element, or a full layout).
- Gemini can chain GUI tools with other tools as needed, and the service must support this via the tool calling protocol only.
- The system is designed for future extensibility, including using UI tools in combination with other tool calls.
- All design and implementation should follow SOLID principles for maintainability and extensibility.

**Current Status [2024-05]:**
- ToolUIManager and SystemAlertWindowToolUIManager are fully implemented and integrated via DI.
- All overlays (status and tool UI) are now schema-driven and interactive, including keyboard support for input fields.
- Manual test overlay has been removed after validation.
- Next steps: permissions UX, security/whitelisting, extensibility, tool chaining, notification updates, and testing.

## ShowCanvasTool (Generic Canvas/Sheet Tool)

**Status:** Implemented and registered in LiveConfig (2024-06)

**Purpose:**
- Allows Gemini to display a vertical sheet/canvas overlay with any combination of UI elements (text, image, input, button, etc.) in the order specified by a schema.
- The tool is fully schema-driven and generic: Gemini provides a `schema` argument (as a JSON string) describing the UI to render.
- User input or acknowledgment is returned to Gemini as a map.

**How Gemini Should Call This Tool:**
- Tool name: `show_canvas_tool`
- Argument: `schema` (string) — must be a valid JSON document (all keys and string values use double quotes `"`).

**Schema Format (string content):**
```json
{
  "type": "canvas",
  "elements": [
    {"type": "text", "value": "Some <b>bold</b> and <i>italic</i> text."},
    {"type": "image", "src": "data:image/png;base64,..."},
    {"type": "input", "label": "Your name", "id": "name", "hint": "Enter name..."},
    {"type": "button", "label": "Submit", "action": "submit"}
  ]
}
```

**Supported element types:**
- `text`: `{ "type": "text", "value": "..." }` (value supports HTML formatting)
- `image`: `{ "type": "image", "src": "..." }` (data URI or URL)
- `input`: `{ "type": "input", "label": "...", "id": "...", "hint": "..." }`
- `button`: `{ "type": "button", "label": "...", "action": "..." }`

**Canvas Dismiss/Hide Button:**
- The canvas overlay always includes a dismiss (hide) button at the top, labeled "Hide". This button hides (but does not clear) the canvas overlay. You do **not** need to add a `dismiss_button` element to your schema; it is always present. Appending to the canvas will make it visible again with all previous content.

**Notes:**
- The tool is fully generic and can render any combination/order of supported elements.
- The schema must be a valid JSON string (not Python dict, not single-quoted).
- The tool is registered in LiveConfig and available for Gemini to call.
- If the schema is invalid, the tool will return an error.

--- 

**Note:** The SEND_REALTIME_INPUT payload is now an object: `{ text?: string, chunks?: GenerativeContentBlob[] }`. Use 'text' for real-time text input (e.g., from canvas UI), and 'chunks' for audio/video. Do not send an array directly. 

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

### Tool Chaining & Context Management
- Tool chaining is supported via sequential TOOL_CALL messages from the server. The client is ready for future Gemini-driven context passing if needed.

### Overlay/Canvas UI and Google Search Suggestion Chips
- Canvas overlays are always schema-driven, scrollable, and always include a dismiss/hide button at the top (no need to add it to the schema).
- Overlays auto-dismiss after inactivity or on user request (if implemented).
- Only whitelisted UI elements/actions are allowed. The schema is extensible for new UI elements.
- If a CONTENT_MESSAGE includes `groundingMetadata.searchEntryPoint.renderedContent`, it is rendered as an HTML chip in the canvas overlay. Clicking it opens the link and dismisses the overlay.

### Service-Centric Architecture
- The Android client is now fully service-centric. All chat/session logic is handled by the background service (`GeminiAssistantService`). The UI is focused on service control and status. The previous chat UI and `ChatViewModel` are deprecated and retained only for reference.

## ShowCanvasTool (Generic Canvas/Sheet Tool)

**Status:** Implemented and registered in LiveConfig (2024-06)

**Purpose:**
- Allows Gemini to display a vertical sheet/canvas overlay with any combination of UI elements (text, image, input, button, etc.) in the order specified by a schema.
- The tool is fully schema-driven and generic: Gemini provides a `schema` argument (as a JSON string) describing the UI to render.
- User input or acknowledgment is returned to Gemini as a map.

**How Gemini Should Call This Tool:**
- Tool name: `show_canvas_tool`
- Argument: `schema` (string) — must be a valid JSON document (all keys and string values use double quotes `"`).

**Schema Format (string content):**
```json
{
  "type": "canvas",
  "elements": [
    {"type": "text", "value": "Some <b>bold</b> and <i>italic</i> text."},
    {"type": "image", "src": "data:image/png;base64,..."},
    {"type": "input", "label": "Your name", "id": "name", "hint": "Enter name..."},
    {"type": "button", "label": "Submit", "action": "submit"}
  ]
}
```

**Supported element types:**
- `text`: `{ "type": "text", "value": "..." }` (value supports HTML formatting)
- `image`: `{ "type": "image", "src": "..." }` (data URI or URL)
- `input`: `{ "type": "input", "label": "...", "id": "...", "hint": "..." }`
- `button`: `{ "type": "button", "label": "...", "action": "..." }`

**Canvas Dismiss/Hide Button:**
- The canvas overlay always includes a dismiss (hide) button at the top, labeled "Hide". This button hides (but does not clear) the canvas overlay. You do **not** need to add a `dismiss_button` element to your schema; it is always present. Appending to the canvas will make it visible again with all previous content.

**Notes:**
- The tool is fully generic and can render any combination/order of supported elements.
- The schema must be a valid JSON string (not Python dict, not single-quoted).
- The tool is registered in LiveConfig and available for Gemini to call.
- If the schema is invalid, the tool will return an error.

--- 

**Note:** The SEND_REALTIME_INPUT payload is now an object: `{ text?: string, chunks?: GenerativeContentBlob[] }`. Use 'text' for real-time text input (e.g., from canvas UI), and 'chunks' for audio/video. Do not send an array directly. 

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