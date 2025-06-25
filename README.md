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

## 📺 Demo Video

[![Watch the demo](https://img.youtube.com/vi/vXs2ktkDpAg/0.jpg)](https://youtube.com/shorts/vXs2ktkDpAg?feature=share)

Or watch on [YouTube](https://youtube.com/shorts/vXs2ktkDpAg?feature=share).

> **Note:** This demo video is from an earlier version of the app. The current version has more features and improvements, but I couldn't record a new video due to some issues in the app.

---

## ✨ Features

- **Multimodal Chat**: Text and voice (WebRTC/PCM) with Gemini, including streaming responses
- **Tool Calling**: Gemini can call device-native or UI tools (e.g., take photo, show canvas, send message to a person,generateImages (uses gemini 2.0 flash image generation model in a tool))
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
3. **After installing and running the app, you must manually grant overlay (draw over other apps) and microphone permissions from the device's Settings > Apps > [Your App] > Permissions.**
   - This is required for the assistant to function correctly (for overlays and voice input).
4. Use the Service Control UI to start/stop the assistant

---

## 🚦 How to Start the Server

1. **Navigate to the server directory:**
   ```sh
   cd gemini-live-api-server
   ```
2. **Install dependencies:**
   ```sh
   npm install
   ```
3. **Set up your `.env` file:**
   - Copy `.env.example` to `.env` (if provided), or create a `.env` file.
   - Add your Gemini API key and any other required environment variables.
4. **Start the server:**
   ```sh
   npm start
   ```
5. **Server should now be running on the configured port (default: 3001).**

> **Note:** The Android app requires the server to be running and reachable on the configured IP address. Make sure both devices are on the same network and the IP is set correctly in `GeminiAssistantService.kt` and `network_security_config.xml`.

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
- Client-side configuration for TURN/STUN servers has been successfully implemented in `assistantapp/app/src/main/java/com/gamesmith/assistantapp/data/webrtc/WebRTCManager.kt` to improve connection reliability, especially in NAT scenarios.
- **Important Note on TURN/STUN Server IP:** If your TURN/STUN server is running on a local machine (like a WSL instance), its internal IP address (`192.168.134.88` in the setup summary) may change over time. If the IP changes, you will need to:
    - Update the `coturn` server configuration (`/etc/turnserver.conf`) if it's bound to a specific internal IP.
    - Update the TURN/STUN server URLs in the Android client's `WebRTCManager.kt` with the new IP address.
    - Update any firewall or router port forwarding rules that use the old internal IP address.
- The project is **not fully pre-configured for emulator use from scratch**. You may need to adjust network settings and permissions for emulator scenarios.

---

## ⚙️ TURN/STUN Server Setup

For reliable WebRTC connections, especially across different networks or behind NAT, a TURN/STUN server is essential. This project has been configured to utilize a TURN/STUN server.

Here's a summary of the setup process. For detailed steps, refer to the [`TURN_STUN_Setup_Summary.md`](TURN_STUN_Setup_Summary.md) document.

1.  **Install coturn:** Install the `coturn` TURN/STUN server on a Linux-based system (like a WSL instance).
2.  **Configure `turnserver.conf`:** Edit the main configuration file (`/etc/turnserver.conf`) to set the `realm` (e.g., `gamesmith.com`) and configure a `static-auth-secret`.
3.  **Firewall Configuration:** Configure your firewall to allow incoming UDP and TCP traffic on port 3478 (and 5349 for TLS/DTLS) and forward these ports to the server's internal IP address.
4.  **TLS/DTLS Configuration (Recommended):** Obtain and configure SSL/TLS certificates for secure connections.
5.  **Manage the Service:** Use `sudo service coturn [status|start|stop|restart]` to manage the `coturn` background service.
6.  **Client Integration:** The Android client (`WebRTCManager.kt`) has been updated to use the configured TURN/STUN server URLs and authentication mechanism. Ensure the IP addresses in the client match your server's reachable IP.

**Important Note on Server IP:** If your TURN/STUN server is running on a local machine (like a WSL instance), its internal IP address may change over time. If the IP changes, you will need to update the server configuration, the client's `WebRTCManager.kt`, and any firewall/router rules accordingly.

---

## ⚙️ TURN/STUN Server Configuration

For reliable WebRTC connections, especially when clients are behind different types of NAT or firewalls, a TURN/STUN server is essential. This project's Android client is configured to utilize a TURN/STUN server to help establish peer-to-peer connections.

To configure the client to use your TURN/STUN server:

1.  Locate the `iceServers` list in the [`WebRTCManager.kt`](assistantapp/app/src/main/java/com/gamesmith/assistantapp/data/webrtc/WebRTCManager.kt) file.
2.  Update the `stun:` and `turn:` URLs in this list to point to your TURN/STUN server's public IP address and ports.
3.  If your TURN server requires authentication (like `coturn` with `static-auth-secret`), you will also need to configure the username and password generation logic within the `createPeerConnection` function in `WebRTCManager.kt` to match your server's requirements. The current implementation includes logic for `coturn`'s static authentication using a timestamp and HMAC-SHA1, but the IP address (`192.168.134.88`) is currently hardcoded. You should replace this with your server's IP and ensure the authentication logic matches your server setup.

You have a few options for obtaining a TURN/STUN server:

*   **Set up your own:** You can install and configure a server like `coturn` on your own infrastructure (e.g., a cloud server, a local machine with port forwarding). Refer to the `coturn` documentation or online guides for detailed setup instructions. The [`TURN_STUN_Setup_Summary.md`](TURN_STUN_Setup_Summary.md) document in this repository provides a summary of the steps taken for a local WSL setup, which might be helpful for context, but does not contain comprehensive setup instructions.
*   **Use a public server:** There are some public STUN servers available (e.g., `stun.l.google.com:19302`), but public TURN servers are less common and often require authentication or are part of commercial services.

Ensure the IP addresses and ports configured in the client's `WebRTCManager.kt` are reachable by the Android device.

---

## 🐞 Known Issues

- issues related to webRTC has been fixed by setting up STUN/TURN server.
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