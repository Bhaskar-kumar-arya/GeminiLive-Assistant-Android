import { Content, Part } from "@google/genai";
import { EventEmitter } from "eventemitter3";
import { difference } from "lodash";
import WebSocket from 'ws';
import {
  ClientContentMessage,
  isInterrupted,
  isModelTurn,
  isServerContentMessage,
  isSetupCompleteMessage,
  isToolCallCancellationMessage,
  isToolCallMessage,
  isTurnComplete,
  LiveIncomingMessage,
  ModelTurn,
  RealtimeInputMessage,
  ServerContent,
  SetupMessage,
  SetupCompleteMessage,
  StreamingLog,
  ToolCall,
  ToolCallCancellation,
  ToolResponseMessage,
  type LiveConfig,
} from "./multimodal-live-types";
import { bufferToJSON, base64ToArrayBuffer } from "./utils";

/**
 * the events that this client will emit
 */
interface MultimodalLiveClientEventTypes {
  open: () => void;
  log: (log: StreamingLog) => void;
  close: (event: { code: number; reason: Buffer, wasClean: boolean }) => void;
  audio: (data: ArrayBuffer) => void;
  content: (data: ServerContent) => void;
  interrupted: () => void;
  setupcomplete: (data: SetupCompleteMessage['setupComplete']) => void;
  turncomplete: () => void;
  toolcall: (toolCall: ToolCall) => void;
  toolcallcancellation: (toolcallCancellation: ToolCallCancellation) => void;
}

export type MultimodalLiveAPIClientConnection = {
  url?: string;
  apiKey: string;
};

/**
 * A event-emitting class that manages the connection to the websocket and emits
 * events to the rest of the application.
 * If you dont want to use react you can still use this.
 */
export class MultimodalLiveClient extends EventEmitter<MultimodalLiveClientEventTypes> {
  public ws: WebSocket | null = null;
  protected config: LiveConfig | null = null;
  public url: string = "";
  public getConfig() {
    return { ...this.config };
  }

  constructor({ url, apiKey }: MultimodalLiveAPIClientConnection) {
    super();
    url =
      url ||
      `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent`;
    url += `?key=${apiKey}`;
    this.url = url;
    this.send = this.send.bind(this);
  }

  log(type: string, message: StreamingLog["message"]) {
    const log: StreamingLog = {
      date: new Date(),
      type,
      message,
    };
    this.emit("log", log);
  }

  connect(config: LiveConfig): Promise<boolean> {
    this.config = config;

    const wsInstance = new WebSocket(this.url);

    wsInstance.on("message", async (data: WebSocket.Data) => {
      if (data instanceof Buffer) {
        this.receive(data);
      } else if (typeof data === 'string') {
        this.receive(Buffer.from(data));
      } else {
        console.log("non buffer/string message", data);
      }
    });

    return new Promise((resolve, reject) => {
      const onError = (err: Error) => {
        this.disconnect(wsInstance);
        const message = `Could not connect to "${this.url}"`;
        this.log(`server.error`, message + `: ${err.message}`);
        reject(new Error(message + `: ${err.message}`));
      };
      wsInstance.on("error", onError);
      wsInstance.on("open", () => {
        if (!this.config) {
          reject("Invalid config sent to `connect(config)`");
          return;
        }
        this.log(`client.open`, `connected to socket`);
        this.emit("open");

        this.ws = wsInstance;

        const setupMessage: SetupMessage = {
          setup: this.config,
        };
        this._sendDirect(setupMessage);
        this.log("client.send", "setup");

        wsInstance.removeListener("error", onError);
        wsInstance.on("close", (code: number, reason: Buffer) => {
          this.disconnect(wsInstance);
          let reasonString = reason.toString();
          if (reasonString.toLowerCase().includes("error")) {
            const prelude = "ERROR]";
            const preludeIndex = reasonString.indexOf(prelude);
            if (preludeIndex > 0) {
              reasonString = reasonString.slice(
                preludeIndex + prelude.length + 1,
                Infinity
              );
            }
          }
          this.log(
            `server.close`,
            `disconnected ${code} ${reasonString ? `with reason: ${reasonString}` : ``}`
          );
          this.emit("close", { code, reason, wasClean: code === 1000 });
        });
        resolve(true);
      });
    });
  }

  disconnect(wsInstance?: WebSocket) {
    if ((!wsInstance || this.ws === wsInstance) && this.ws) {
      this.ws.close();
      this.ws = null;
      this.log("client.close", `Disconnected`);
      return true;
    }
    return false;
  }

  protected async receive(data: Buffer) {
    const response: LiveIncomingMessage = (await bufferToJSON(
      data
    )) as LiveIncomingMessage;
    if (isToolCallMessage(response)) {
      this.log("server.toolCall", response);
      this.emit("toolcall", response.toolCall);
      return;
    }
    if (isToolCallCancellationMessage(response)) {
      this.log("receive.toolCallCancellation", response);
      this.emit("toolcallcancellation", response.toolCallCancellation);
      return;
    }

    if (isSetupCompleteMessage(response)) {
      this.log("server.event", "setupComplete received from Gemini");
      this.emit("setupcomplete", response.setupComplete);
      return;
    }

    if (isServerContentMessage(response)) {
      const { serverContent } = response;
      if (isInterrupted(serverContent)) {
        this.log("receive.serverContent", "interrupted");
        this.emit("interrupted");
        return;
      }
      if (isTurnComplete(serverContent)) {
        this.log("server.send", "turnComplete");
        this.emit("turncomplete");
      }

      if (isModelTurn(serverContent)) {
        let parts: Part[] = serverContent.modelTurn.parts;

        const audioParts = parts.filter(
          (p) => p.inlineData && p.inlineData.mimeType && p.inlineData.mimeType.startsWith("audio/pcm")
        );
        const base64s = audioParts.map((p) => p.inlineData?.data);

        const otherParts = difference(parts, audioParts);

        base64s.forEach((b64) => {
          if (b64) {
            const data = base64ToArrayBuffer(b64);
            this.emit("audio", data);
            this.log(`server.audio`, `buffer (${data.byteLength})`);
          }
        });
        if (!otherParts.length) {
          return;
        }

        parts = otherParts;

        const content: ModelTurn = { modelTurn: { parts } };
        this.emit("content", content);
        this.log(`server.content`, response);
      }
    } else {
      console.log("received unmatched message", response);
    }
  }

  sendRealtimeInput(chunks: any[]) {
    let hasAudio = false;
    let hasVideo = false;
    for (let i = 0; i < chunks.length; i++) {
      const ch = chunks[i];
      if (ch.mimeType.includes("audio")) {
        hasAudio = true;
      }
      if (ch.mimeType.includes("image")) {
        hasVideo = true;
      }
      if (hasAudio && hasVideo) {
        break;
      }
    }
    const message =
      hasAudio && hasVideo
        ? "audio + video"
        : hasAudio
        ? "audio"
        : hasVideo
        ? "video"
        : "unknown";

    const data: RealtimeInputMessage = {
      realtimeInput: {
        mediaChunks: chunks,
      },
    };
    this._sendDirect(data);
    this.log(`client.realtimeInput`, message);
  }

  sendToolResponse(toolResponse: ToolResponseMessage["toolResponse"]) {
    const message: ToolResponseMessage = {
      toolResponse,
    };

    this._sendDirect(message);
    this.log(`client.toolResponse`, message);
  }

  send(parts: Part | Part[], turnComplete: boolean = true) {
    parts = Array.isArray(parts) ? parts : [parts];
    const content: Content = {
      role: "user",
      parts,
    };

    const clientContentRequest: ClientContentMessage = {
      clientContent: {
        turns: [content],
        turnComplete,
      },
    };

    this._sendDirect(clientContentRequest);
    this.log(`client.send`, clientContentRequest);
  }

  _sendDirect(request: object) {
    if (!this.ws) {
      throw new Error("WebSocket is not connected");
    }
    const str = JSON.stringify(request);
    this.ws.send(str);
  }

  async setConfig(newConfig: LiveConfig): Promise<boolean> {
    this.log("client.setConfig", "Updating LiveConfig...");
    if (this.ws) {
      this.log("client.setConfig", "Disconnecting existing WebSocket connection...");
      this.disconnect();
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    this.log("client.setConfig", "Reconnecting with new configuration...");
    return this.connect(newConfig);
  }
}
