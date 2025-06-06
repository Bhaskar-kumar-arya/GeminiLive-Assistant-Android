import dotenv from 'dotenv';
import WebSocket from 'ws';
import { handleConnection } from './gateway'; // Import the gateway handler
// import { MultimodalLiveClient } from './gemini-client/multimodal-live-client'; // Will be used later

// Load environment variables from .env file
dotenv.config();

const GEMINI_API_KEY = process.env.GEMINI_API_KEY;

if (!GEMINI_API_KEY) {
  console.error("Error: GEMINI_API_KEY not found in .env file. The server cannot connect to Gemini.");
  // Consider exiting if the API key is essential for core functionality upon startup,
  // but the gateway itself also checks for this key before trying to connect to Gemini.
  // process.exit(1); 
}

const port = process.env.PORT || 3001;

const wss = new WebSocket.Server({ port: Number(port) });

console.log(`WebSocket server started on port ${port}`);

// Delegate new connections to the gateway handler
wss.on('connection', (ws) => {
  console.log('Client attempting to connect...'); // Log connection attempt
  handleConnection(ws);
});

// The GEMINI_API_KEY check and logging can remain here for initial startup diagnostics,
// but the gateway.ts now also handles the API key for its operations.
console.log('GEMINI_API_KEY loaded in server.ts:', GEMINI_API_KEY ? 'Yes' : 'No'); 