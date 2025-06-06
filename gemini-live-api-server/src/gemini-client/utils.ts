/**
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Node.js equivalent for blobToJSON, assuming blob is a Buffer
export async function bufferToJSON(buffer: Buffer): Promise<any> {
  try {
    return JSON.parse(buffer.toString('utf-8'));
  } catch (error) {
    console.error("Failed to parse buffer to JSON:", error);
    throw error;
  }
}

// Node.js equivalent for base64ToArrayBuffer
export function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const buffer = Buffer.from(base64, 'base64');
  // To get a true ArrayBuffer that doesn\\'t share memory with the original Buffer:
  const arrayBuffer = new ArrayBuffer(buffer.length);
  const view = new Uint8Array(arrayBuffer);
  for (let i = 0; i < buffer.length; ++i) {
    view[i] = buffer[i];
  }
  return arrayBuffer;
  // Simpler if shared memory is okay (often it is for read-only purposes):
  // return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
}
