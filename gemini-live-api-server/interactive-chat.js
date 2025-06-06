// Minimal working example for Gemini Live API using @google/genai
require('dotenv').config();
const { GoogleGenAI, Modality } = require('@google/genai');

const apiKey = process.env.GEMINI_API_KEY;
if (!apiKey) {
  console.error('GEMINI_API_KEY not set in environment.');
  process.exit(1);
}

const ai = new GoogleGenAI({ apiKey });
const model = 'gemini-2.0-flash-live-001';
const config = { responseModalities: [Modality.TEXT] };

async function main() {
  const session = await ai.live.connect({
    model,
    callbacks: {
      onopen: () => console.log('Session opened'),
      onmessage: (msg) => console.log('Received:', msg),
      onerror: (e) => console.error('Error:', e),
      onclose: (e) => console.log('Session closed:', e?.reason),
    },
    config,
  });

  // Send a simple text message
  await session.sendClientContent({
    turns: [{ role: 'user', parts: [{ text: 'Hello, Gemini!' }] }],
    turnComplete: true,
  });

  // Close the session after a short delay (for demo)
  setTimeout(() => session.close(), 5000);
}

main(); 