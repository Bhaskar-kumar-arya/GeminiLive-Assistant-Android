// websocket-protocol-types.ts
// Defines the JSON-based message protocol for communication between a client application
// and the local Gemini Live API WebSocket server.

import type {
  Part,
  Content,
} from "@google/genai";
import type {
  LiveConfig,
  ServerContentMessage, // This is { serverContent: ModelTurn | TurnComplete | Interrupted }
  ToolCallMessage, // This is { toolCall: { functionCall: LiveFunctionCall[] } }
  ToolCallCancellationMessage, // This is { toolCallCancellation: { id: string } }
  SetupCompleteMessage, // This is { setupComplete: { success: boolean; error?: ... } }
  StreamingLog, // This is { date: Date; type: string; message: any; count?: number }
} from "./gemini-client/multimodal-live-types";

// --------------------------------------------------------------------------------
// Client App -> Server Message Types
// --------------------------------------------------------------------------------

// --- Payload Types for Client -> Server Messages ---

export interface ConnectGeminiPayload {
  initialConfig: LiveConfig;
}

export interface SendMessagePayload {
  parts: Part[];
  turnComplete?: boolean;
}

export interface SendRealtimeInputPayload {
  chunks: any[];
}

export interface SendToolResponsePayload {
  toolResponse: {
    functionResponses: {
      id: string;
      response: Content;
    }[];
  };
}

export interface GenerateImagePayload {
  text: string;
  imageUri?: string; // Optional URI for input image
}

// --- WebRTC Signaling Payloads ---
export interface RTCIceCandidateJson {
  sdpMid: string | null;
  sdpMLineIndex: number | null;
  candidate: string | null;
}

export interface WebRTCOfferPayload {
  sdp: string;
}

export interface WebRTCAnswerPayload { // Defined here for use by both client and server messages
  sdp: string;
}

export interface WebRTCIceCandidatePayload { // Defined here for use by both client and server messages
  candidate: RTCIceCandidateJson;
}

// For UPDATE_CONFIG, the payload is LiveConfig directly.

// --- Individual Client -> Server Message Shapes ---

export interface ConnectGeminiClientMessage {
  type: "CONNECT_GEMINI";
  payload: ConnectGeminiPayload;
}

export interface SendMessageClientMessage {
  type: "SEND_MESSAGE";
  payload: SendMessagePayload;
}

export interface SendRealtimeInputClientMessage {
  type: "SEND_REALTIME_INPUT";
  payload: SendRealtimeInputPayload;
}

export interface SendToolResponseClientMessage {
  type: "SEND_TOOL_RESPONSE";
  payload: SendToolResponsePayload;
}

export interface UpdateConfigClientMessage {
  type: "UPDATE_CONFIG";
  payload: LiveConfig; // Direct payload
}

export interface DisconnectGeminiClientMessage {
  type: "DISCONNECT_GEMINI";
  // No payload
}

// --- WebRTC Signaling Client Messages ---
export interface WebRTCOfferClientMessage {
  type: "WEBRTC_OFFER";
  payload: WebRTCOfferPayload;
}

export interface WebRTCIceCandidateClientMessage {
  type: "WEBRTC_ICE_CANDIDATE";
  payload: WebRTCIceCandidatePayload;
}

export interface GenerateImageClientMessage {
  type: "GENERATE_IMAGE"; // New message type for image generation request
  payload: GenerateImagePayload;
}

// --- Union Type for All Client -> Server Messages ---

export type ClientToServerMessage =
  | ConnectGeminiClientMessage
  | SendMessageClientMessage
  | SendRealtimeInputClientMessage
  | SendToolResponseClientMessage
  | UpdateConfigClientMessage
  | DisconnectGeminiClientMessage
  | WebRTCOfferClientMessage
  | WebRTCIceCandidateClientMessage
  | GenerateImageClientMessage; // Added for image generation

// --------------------------------------------------------------------------------
// Server -> Client App Message Types
// --------------------------------------------------------------------------------

// --- Payload Types for Server -> Client App Messages ---

export interface GeminiDisconnectedPayload {
  reason?: string;
}

export interface GeminiErrorPayload {
  message: string;
  details?: any;
}

export interface AudioChunkPayload {
  /** Audio data, typically base64 encoded string if sent over JSON, or ArrayBuffer if binary is used. */
  data: ArrayBuffer | string;
}

export interface ImageGenerationResultPayload {
  success: boolean;
  imageUrl?: string; // URI of the generated image on the server
  imageData?: string; // Base64 encoded image data
  error?: string;
}

// For CONTENT_MESSAGE, payload is ServerContentMessage
// For TOOL_CALL, payload is ToolCallMessage
// For TOOL_CALL_CANCELLATION, payload is ToolCallCancellationMessage
// For SETUP_COMPLETE, payload is SetupCompleteMessage
// For INTERRUPTED, payload is optionally ServerContentMessage
// For TURN_COMPLETE, payload is optionally ServerContentMessage
// For LOG_MESSAGE, payload is StreamingLog

// --- Individual Server -> Client App Message Shapes ---

export interface GeminiConnectedServerMessage {
  type: "GEMINI_CONNECTED";
  // No payload
}

export interface GeminiDisconnectedServerMessage {
  type: "GEMINI_DISCONNECTED";
  payload: GeminiDisconnectedPayload;
}

export interface GeminiErrorServerMessage {
  type: "GEMINI_ERROR";
  payload: GeminiErrorPayload;
}

export interface ContentServerMessage {
  type: "CONTENT_MESSAGE";
  payload: ServerContentMessage; // Contains { serverContent: ModelTurn | TurnComplete | Interrupted }
}

export interface ToolCallServerMessage {
  type: "TOOL_CALL";
  payload: ToolCallMessage; // Contains { toolCall: { functionCall: LiveFunctionCall[] } }
}

export interface ToolCallCancellationServerMessage {
  type: "TOOL_CALL_CANCELLATION";
  payload: ToolCallCancellationMessage; // Contains { toolCallCancellation: { ids: string[] } }
}

export interface SetupCompleteServerMessage {
  type: "SETUP_COMPLETE";
  payload: SetupCompleteMessage; // Contains { setupComplete: { success: boolean; error?: ... } }
}

export interface InterruptedServerMessage {
  type: "INTERRUPTED";
  payload?: ServerContentMessage; // Optional: original ServerContentMessage that indicated interruption
}

export interface TurnCompleteServerMessage {
  type: "TURN_COMPLETE";
  payload?: ServerContentMessage; // Optional: original ServerContentMessage that indicated turn completion
}

export interface LogMessageServerMessage {
  type: "LOG_MESSAGE";
  payload: StreamingLog;
}

export interface AudioChunkServerMessage {
  type: "AUDIO_CHUNK";
  payload: AudioChunkPayload;
}

export interface ImageGenerationResultServerMessage {
  type: "IMAGE_GENERATION_RESULT"; // New message type for image generation result
  payload: ImageGenerationResultPayload;
}

// --- WebRTC Signaling Server Messages ---
export interface WebRTCAnswerServerMessage {
  type: "WEBRTC_ANSWER";
  payload: WebRTCAnswerPayload;
}

export interface WebRTCIceCandidateServerMessage {
  type: "WEBRTC_ICE_CANDIDATE";
  payload: WebRTCIceCandidatePayload;
}

// --- Union Type for All Server -> Client App Messages ---

export type ServerToClientMessage =
  | GeminiConnectedServerMessage
  | GeminiDisconnectedServerMessage
  | GeminiErrorServerMessage
  | ContentServerMessage
  | ToolCallServerMessage
  | ToolCallCancellationServerMessage
  | SetupCompleteServerMessage
  | InterruptedServerMessage
  | TurnCompleteServerMessage
  | LogMessageServerMessage
  | AudioChunkServerMessage
  | WebRTCAnswerServerMessage
  | WebRTCIceCandidateServerMessage
  | ImageGenerationResultServerMessage; // Added for image generation result