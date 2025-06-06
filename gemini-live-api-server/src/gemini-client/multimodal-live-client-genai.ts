import { GoogleGenAI, Modality, MediaResolution, type Content, type Part, type Tool, type GenerationConfig, StartSensitivity, EndSensitivity } from '@google/genai';
import { EventEmitter } from 'eventemitter3';
import type { LiveConfig } from './multimodal-live-types';

// Event types for compatibility with the gateway
interface GenaiLiveClientEventTypes {
  open: () => void;
  close: (event: { code: number; reason: string; wasClean: boolean }) => void;
  content: (data: any) => void;
  audio: (data: ArrayBuffer) => void;
  toolcall: (toolCall: any) => void;
  toolcallcancellation: (toolCallCancellation: any) => void;
  interrupted: () => void;
  setupcomplete: (data: any) => void;
  turncomplete: () => void;
  log: (log: { type: string; message: any }) => void;
}

export class GenaiLiveClient extends EventEmitter<GenaiLiveClientEventTypes> {
  private ai: GoogleGenAI;
  private session: any = null;
  private config: LiveConfig | null = null;
  private model: string = '';
  private apiKey: string;

  constructor({ apiKey }: { apiKey: string }) {
    super();
    this.apiKey = apiKey;
    this.ai = new GoogleGenAI({ apiKey });
  }

  async connect(config: LiveConfig): Promise<boolean> {
    this.config = config;
    this.model = config.model;

    // Map config to SDK config using the client-provided config
    const sdkConfig: any = {
      responseModalities: [
        config.generationConfig?.responseModalities?.includes('audio')
          ? Modality.AUDIO
          : Modality.TEXT
      ],
      speechConfig: config.generationConfig?.speechConfig,
      systemInstruction: config.systemInstruction,
      tools: config.tools,
      realtimeInputConfig: {
        automaticActivityDetection: {
          disabled: false,
          startOfSpeechSensitivity: StartSensitivity.START_SENSITIVITY_HIGH,
          endOfSpeechSensitivity: EndSensitivity.END_SENSITIVITY_HIGH,
          prefixPaddingMs: 20,
          silenceDurationMs: 100,
        }
      }
    };
    if (config.generationConfig?.mediaResolution) {
      sdkConfig.mediaResolution = config.generationConfig.mediaResolution;
    }
    try {
      this.session = await this.ai.live.connect({
        model: this.model,
        config: sdkConfig,
        callbacks: {
          onopen: () => {
            this.emit('open');
            this.emit('setupcomplete', { success: true });
          },
          onclose: (e: any) => {
            this.emit('close', { code: e.code || 1000, reason: e.reason || '', wasClean: true });
          },
          onmessage: (msg: any) => {
            // console.log('[GenaiLiveClient DEBUG] Raw message from Gemini SDK:', JSON.stringify(msg, null, 2)); // REMOVE DEBUG LOG
            // Handle streamed responses, tool calls, etc.
            if (msg.serverContent) {
              this.emit('content', msg.serverContent);
              if (msg.serverContent.interrupted) this.emit('interrupted');
              if (msg.serverContent.turnComplete) this.emit('turncomplete');
              // Audio streaming
              if (msg.serverContent.modelTurn && msg.serverContent.modelTurn.parts) {
                for (const part of msg.serverContent.modelTurn.parts) {
                  if (part.inlineData && part.inlineData.mimeType && part.inlineData.mimeType.startsWith('audio/pcm')) {
                    const b64 = part.inlineData.data;
                    if (b64) {
                      const buf = Buffer.from(b64, 'base64');
                      this.emit('audio', buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength));
                    }
                  }
                }
              }
            }
            if (msg.toolCall) {
              this.emit('toolcall', msg.toolCall);
            }
            if (msg.toolCallCancellation) {
              this.emit('toolcallcancellation', msg.toolCallCancellation);
            }
            // Add more event mappings as needed
          },
          onerror: (e: any) => {
            this.emit('log', { type: 'error', message: e });
          },
        },
      });
      return true;
    } catch (err) {
      this.emit('log', { type: 'error', message: err });
      throw err;
    }
  }

  disconnect() {
    if (this.session) {
      this.session.close();
      this.session = null;
      this.emit('log', { type: 'client.close', message: 'Disconnected' });
      return true;
    }
    return false;
  }

  async send(parts: Part | Part[], turnComplete: boolean = true) {
    if (!this.session) throw new Error('Session not connected');
    const content: Content = {
      role: 'user',
      parts: Array.isArray(parts) ? parts : [parts],
    };
    await this.session.sendClientContent({
      turns: [content],
      turnComplete,
    });
    this.emit('log', { type: 'client.send', message: content });
  }

  async sendRealtimeInput(realtimeInputPayload: any) {
    if (!this.session) throw new Error('Session not connected');
    
    // Use the SDK's dedicated sendRealtimeInput method
    await this.session.sendRealtimeInput(realtimeInputPayload); 
    
    this.emit('log', { type: 'client.realtimeInput', message: realtimeInputPayload });
  }

  async sendToolResponse(toolResponse: any) {
    if (!this.session) throw new Error('Session not connected');
    await this.session.sendToolResponse(toolResponse);
    this.emit('log', { type: 'client.toolResponse', message: toolResponse });
  }

  async setConfig(newConfig: LiveConfig): Promise<boolean> {
    this.disconnect();
    await new Promise((resolve) => setTimeout(resolve, 100));
    return this.connect(newConfig);
  }
} 