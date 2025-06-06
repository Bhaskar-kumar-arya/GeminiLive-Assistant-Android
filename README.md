# Gemini Android Assistant & Live API Server

A next-generation, multimodal Android assistant powered by Google's Gemini API, featuring real-time voice, text, tool calling, and schema-driven overlays. Includes a Kotlin Android app and a Node.js Live API server for development and testing.

---

## 🚀 Introduction

This project provides a **native Android assistant** that communicates with the Gemini API via a secure, local WebSocket server. It supports:
- Real-time text and voice chat
- Tool calling (function calling) with background and UI-driven tools
- Schema-driven overlays (canvas/tool UIs)
- Service-centric, background operation
- Extensible, SOLID-compliant architecture

The repository also includes:
- **Gemini Live API Server** (Node.js): Secure bridge to Gemini API, manages API keys, tool logic, and protocol

---

## ✨ Features

- **Multimodal Chat**: Text and voice (WebRTC/PCM) with Gemini, including streaming responses
- **Tool Calling**: Gemini can call device-native or UI tools (e.g., take photo, show canvas, send message)
- **Schema-Driven Overlays**: Canvas/tool overlays are defined by compact JSON schemas, supporting text, images, inputs, buttons, and Google Search Suggestion chips
- **Service-Centric Android App**: All session logic runs in a background service (`GeminiAssistantService`), with persistent notification and overlay UI
- **SOLID & DI**: Fully modular, Hilt-powered dependency injection
- **Extensible Tool Framework**: Add new tools by implementing a handler and registering it
- **Security**: API keys never exposed to the client; overlays require explicit permissions

---

## 👤 About the Author

This project was created for fun by me, a 17-year-old 12th graduate student from Patna, India.
i used CURSOR AI along with a few LLMs.

---

## 🏗️ Architecture

- **Android App**: Kotlin, Jetpack Compose, Hilt, OkHttp, WebRTC, schema-driven overlays
- **Server**: Node.js, WebSocket, Gemini API, tool orchestration, protocol translation

**Communication Flow:**
```
Android App <-> WebSocket <-> Local Gemini Live API Server <-> Gemini API
```
- WebRTC is used for real-time audio
- Tool calls and overlays are managed via protocol messages

---

## 📁 Project Structure

```
assistantapp/                # Android app (Kotlin)
  app/src/main/java/com/gamesmith/assistantapp/
    data/                    # DTOs, WebSocket, WebRTC, models
    di/                      # Hilt modules
    service/                 # GeminiAssistantService, overlays
    ui/                      # Compose UI, overlays, themes
    util/                    # Utilities
    MainActivity.kt, ...
gemini-live-api-server/      # Node.js server (Gemini API bridge)
DOCS_Optimized/              # Detailed docs, integration guides
```

---

## 🛠️ Getting Started

### Prerequisites
- Android Studio (Giraffe+ recommended)
- Node.js (for server)
- Gemini API key ([get one here](https://aistudio.google.com/apikey))

### Android App
1. Open `assistantapp/` in Android Studio
2. Build & run on device/emulator
3. Grant overlay and microphone permissions as prompted
4. Use the Service Control UI to start/stop the assistant

### Server
1. `cd gemini-live-api-server`
2. `npm install`
3. Add your Gemini API key to the server config
4. `npm start`

---

## 💡 Usage

- The Android app connects to the local server via WebSocket
- After setup, the assistant greets the user and is ready for text/voice input
- Gemini can call tools (e.g., take a photo, show a canvas overlay, send a message)
- Tool overlays are schema-driven, always include a dismiss button, and support Google Search Suggestion chips
- All tool responses use the flat `functionResponses` array (see protocol docs)

---

## 🧩 Tool Calling & Overlays

- Tools are declared in the server config and registered in the Android app
- Supported tools include:
  - `get_current_time`, `send_screen_snapshot_tool`, `create_canvas_tool`, `append_to_canvas_tool`, `take_photo_tool`, `find_contact_tool`, `send_message_tool`, etc.
- Canvas overlays are defined by JSON schemas (see [Phase4_BackgroundToolCalling_Details.md](DOCS_Optimized/Phase4_BackgroundToolCalling_Details.md))
- Overlays are always branded, easy to dismiss, and support user input

---

## 🌐 Network Setup

- **Server and client must be on the same network** for communication.
- You must configure the IP address in two places:
  - `GeminiAssistantService.kt` (see `defaultServerUrl`)
  - `network_security_config.xml` (add your server's IP/domain)
- The project is **not fully pre-configured for emulator use from scratch**. You may need to adjust network settings and permissions for emulator scenarios.

---

## 🐞 Known Issues

- Sometimes, the connection between the server and client isn't properly established, which can cause microphone failure on the Android device. This is likely related to WebRTC connection setup. If you encounter this, try restarting the app and server, and ensure both are on the same network.
- **Gemini Live API Limitation:** When running tool/function calls asynchronously, the Gemini Live API is unable to reliably execute sequential function calls. It may execute a single call more than once, and does not relate the result of one function call to another. This is a limitation of the Gemini API itself, not of this implementation(correct me if i am wrong).

---

## 📚 Documentation

- [AndroidAssistant_ProjectStatusAndRoadmap.md](DOCS_Optimized/AndroidAssistant_ProjectStatusAndRoadmap.md)
- [ClientIntegrationGuide.md](DOCS_Optimized/ClientIntegrationGuide.md)
- [GeminiLiveAPIServer_Overview.md](DOCS_Optimized/GeminiLiveAPIServer_Overview.md)
- [Phase4_BackgroundToolCalling_Details.md](DOCS_Optimized/Phase4_BackgroundToolCalling_Details.md)
- [WebConsoleGuide.md](DOCS_Optimized/WebConsoleGuide.md)

---

## 🤝 Contributing

Contributions are welcome! Please see the [CONTRIBUTING.md](live-api-web-console/CONTRIBUTING.md) for guidelines. For major changes, open an issue first to discuss what you would like to change.

---

## 📝 License

This project is licensed under the [Apache 2.0 License](live-api-web-console/LICENSE) (see LICENSE for details).

---

## 🔗 Links
- [Gemini API Docs](https://ai.google.dev/gemini-api)
- [@google/genai SDK](https://www.npmjs.com/package/@google/generative-ai)

---

_This is an experiment and not an official Google product. See LICENSE and individual docs for more info._ 