import { GenaiLiveClient } from './multimodal-live-client-genai';
import dotenv from 'dotenv';
import type { LiveConfig } from './multimodal-live-types';
// Remove WaveFile and fetch imports as they are no longer needed for audio
// import { WaveFile } from 'wavefile';
// import fetch from 'node-fetch';

dotenv.config();

const apiKey = process.env.GEMINI_API_KEY;
if (!apiKey) {
  throw new Error('GEMINI_API_KEY not set in environment');
}

// Instantiate the GenaiLiveClient
const client = new GenaiLiveClient({ apiKey });

// Attach event listeners
client.on('open', () => {
  console.log('[EVENT] open');
});

client.on('setupcomplete', (data) => {
  console.log('[EVENT] setupcomplete', data);
});

client.on('content', (data) => {
  // Simplified content log
  if (data?.modelTurn?.parts) {
    data.modelTurn.parts.forEach((part: any) => {
      if (part.text) {
        console.log('[EVENT] content: text =', part.text);
      } else if (part.functionCall) {
        console.log('[EVENT] content: functionCall =', JSON.stringify(part.functionCall, null, 2));
      } else {
        console.log('[EVENT] content: other part =', JSON.stringify(part, null, 2));
      }
    });
  } else {
    console.log('[EVENT] content (raw):', JSON.stringify(data, null, 2));
  }
});

// Remove audio event log as modality is text
// client.on('audio', (data) => {
//   console.log('[EVENT] audio chunk received, length:', data.byteLength);
// });

client.on('toolcall', (toolCall) => {
  console.log('[EVENT] toolcall', JSON.stringify(toolCall, null, 2));
  if (toolCall && toolCall.functionCalls) {
    const functionResponses = toolCall.functionCalls.map((fc: any) => {
      let actualToolOutput: any = { result: 'ok' }; // Default actual output

      if (fc.name === 'getCurrentTime') {
        actualToolOutput = { currentTime: new Date().toISOString() };
        console.log(`[ACTION] Executing getCurrentTime tool. Actual output: ${JSON.stringify(actualToolOutput)}`);
      } else {
        console.log(`[ACTION] Tool ${fc.name} called, providing default 'ok' actual output.`);
      }

      // Constructing the element for functionResponses array as per user's specified structure
      return {
        id: fc.id,
        name: fc.name, // name is at this level
        response: actualToolOutput, // response is the direct output object
      };
    });
    console.log('[ACTION] Sending tool response(s) with structure {id, name, response}:', JSON.stringify(functionResponses, null, 2));
    // The client.sendToolResponse method expects an object like { functionResponses: [...] }
    // The internal GenaiLiveClient.sendToolResponse will pass this object to the SDK's session.sendToolResponse
    client.sendToolResponse({ functionResponses });
  }
});

client.on('toolcallcancellation', (toolCallCancellation) => {
  console.log('[EVENT] toolcallcancellation', toolCallCancellation);
});

client.on('interrupted', () => {
  console.log('[EVENT] interrupted');
});

client.on('turncomplete', () => {
  console.log('[EVENT] turncomplete');
});

client.on('close', (event) => {
  console.log('[EVENT] close', event);
});

client.on('log', (log) => {
  console.log('[LOG]', log.type, log.message);
});

// Main async function to connect and test
(async () => {
  const getCurrentTimeTool = {
    functionDeclarations: [
      {
        name: 'getCurrentTime',
        description: 'Gets the current time.',
        // No parameters needed for getCurrentTime
      },
    ],
  };

  const config: LiveConfig = {
    model: 'gemini-2.0-flash-live-001',
    generationConfig: {
      responseModalities: 'text', // Set to text
    },
    tools: [getCurrentTimeTool], // Add the tool
  };

  try {
    await client.connect(config);
    // Now the session is ready, send a message to trigger the tool
    console.log("[TEST] Sending message to trigger 'getCurrentTime' tool...");
    await client.send([{ text: 'What time is it?' }]);

    // Removed audio download and sendRealtimeInput logic

    // The response handling is through event listeners.
    // Tool call responses are handled in the 'toolcall' event listener.
    // Final model responses are handled in the 'content' event listener.

  } catch (err) {
    console.error('Error during test:', err);
  }
  // Note: We are not explicitly closing the session here to allow all responses to come through.
  // In a real application, you would manage session.close() appropriately.
})(); 