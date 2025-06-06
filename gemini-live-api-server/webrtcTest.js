// Save as webrtc-test-client.js
const WebSocket = require('ws');
const wrtc = require('wrtc');

const SERVER_WS_URL = 'ws://localhost:3001'; // Change to your server's address/port

async function main() {
  const ws = new WebSocket(SERVER_WS_URL);

  ws.on('open', () => {
    console.log('WebSocket connected');
    // Send CONNECT_GEMINI (minimal config)
    ws.send(JSON.stringify({
      type: 'CONNECT_GEMINI',
      payload: {
        initialConfig: {
          model: 'models/gemini-2.0-flash-live-001',
          generationConfig: { responseModalities: ['audio'] }
        }
      }
    }));
  });

  let pc;
  let dataChannel;

  ws.on('message', async (data) => {
    const msg = JSON.parse(data);
    if (msg.type === 'GEMINI_CONNECTED') {
      console.log('GEMINI_CONNECTED');
    }
    if (msg.type === 'SETUP_COMPLETE') {
      console.log('SETUP_COMPLETE, starting WebRTC...');
      // Create PeerConnection
      pc = new wrtc.RTCPeerConnection();
      // Add dummy audio track (not capturing real mic)
      const audioTrack = new wrtc.nonstandard.RTCAudioSource().createTrack();
      pc.addTrack(audioTrack);

      // Data channel for ping-pong
      dataChannel = pc.createDataChannel('test');
      dataChannel.onopen = () => {
        console.log('Data channel open');
        dataChannel.send('ping');
      };
      dataChannel.onmessage = (e) => {
        console.log('Data channel message:', e.data);
      };

      // ICE candidate handling
      pc.onicecandidate = (event) => {
        if (event.candidate) {
          ws.send(JSON.stringify({
            type: 'WEBRTC_ICE_CANDIDATE',
            payload: {
              candidate: event.candidate.candidate,
              sdpMid: event.candidate.sdpMid,
              sdpMLineIndex: event.candidate.sdpMLineIndex
            }
          }));
        }
      };

      // Create and send offer
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      ws.send(JSON.stringify({
        type: 'WEBRTC_OFFER',
        payload: { sdp: offer.sdp }
      }));
    }
    if (msg.type === 'WEBRTC_ANSWER') {
      await pc.setRemoteDescription({ type: 'answer', sdp: msg.payload.sdp });
      console.log('Set remote description (answer)');
    }
    if (msg.type === 'WEBRTC_ICE_CANDIDATE') {
      try {
        await pc.addIceCandidate(msg.payload);
        console.log('Added ICE candidate from server');
      } catch (e) {
        console.error('Error adding ICE candidate:', e);
      }
    }
    if (msg.type === 'LOG_MESSAGE') {
      console.log('Server log:', msg.payload?.message);
    }
  });

  ws.on('close', () => console.log('WebSocket closed'));
  ws.on('error', (err) => console.error('WebSocket error:', err));
}

main();