const WebSocket = require('ws');
const { WaveFile } = require('wavefile');
const fetch = (...args) => import('node-fetch').then(({default: fetch}) => fetch(...args));

(async () => {
  const ws = new WebSocket('ws://localhost:3001'); // Change if your server runs elsewhere

  ws.on('open', async () => {
    // 1. Connect Gemini session with audio modality
    ws.send(JSON.stringify({
      type: 'CONNECT_GEMINI',
      payload: {
        initialConfig: {
          model: 'gemini-2.0-flash-live-001',
          generationConfig: { responseModalities: 'audio' }
        }
      }
    }));

    // 2. Wait a moment for setup (or listen for SETUP_COMPLETE)
    setTimeout(async () => {
      // Download and encode the sample audio
      const audioURL = 'https://storage.googleapis.com/generativeai-downloads/data/16000.wav';
      const response = await fetch(audioURL);
      const audioBuffer = await response.arrayBuffer();
      const wav = new WaveFile();
      wav.fromBuffer(Buffer.from(audioBuffer));
      wav.toSampleRate(16000);
      wav.toBitDepth("16");
      const base64Audio = wav.toBase64();

      // 3. Send the audio as realtime input
      ws.send(JSON.stringify({
        type: 'SEND_REALTIME_INPUT',
        payload: {
          audio: {
            mimeType: "audio/pcm;rate=16000",
            data: base64Audio
          }
        }
      }));
    }, 2000);
  });

  ws.on('message', (msg) => {
    console.log('[SERVER]', msg.toString());
  });

  ws.on('error', (err) => {
    console.error('[ERROR]', err);
  });

  ws.on('close', (code, reason) => {
    console.log('[CLOSE]', code, reason.toString());
  });
})(); 