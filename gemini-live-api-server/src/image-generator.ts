import { GoogleGenAI, Modality } from "@google/genai";
import * as fs from "node:fs";
import path from "node:path";

// Ensure the output directory exists
const outputDir = path.join(__dirname, 'generated_images');
if (!fs.existsSync(outputDir)){
    fs.mkdirSync(outputDir);
}

export async function generateImage(text: string, imageUri?: string): Promise<{ success: boolean; imageUrl?: string; error?: string }> {
    const apiKey = process.env.GEMINI_API_KEY; // Access API key from environment

    if (!apiKey) {
        console.error("GEMINI_API_KEY not found in environment. Cannot generate image.");
        return { success: false, error: "Server configuration error: API key missing." };
    }

    const ai = new GoogleGenAI({ apiKey });

    const contents: any[] = [{ text: text }];

    if (imageUri) {
        try {
            // Assuming imageUri is a local file path for now based on the sample.
            // For a real application, you might need to handle different URI schemes (e.g., http, content://)
            // and potentially download or access the image data differently.
            const imagePath = path.resolve(imageUri); // Resolve path relative to server
            const imageData = fs.readFileSync(imagePath);
            const base64Image = imageData.toString("base64");

            contents.push({
                inlineData: {
                    mimeType: "image/png", // TODO: Determine mime type dynamically
                    data: base64Image,
                },
            });
        } catch (error: any) {
            console.error(`Failed to read image from URI ${imageUri}:`, error);
            return { success: false, error: `Failed to read input image: ${error.message}` };
        }
    }

    let generatedImageUrl: string | undefined; // Declare outside try block

    try {
        console.log("Sending image generation request to Gemini...");
        const response = await ai.models.generateContent({
            model: "gemini-2.0-flash-preview-image-generation", // Use the appropriate model
            contents: contents,
            config: {
                responseModalities: [Modality.TEXT, Modality.IMAGE],
            },
        });

        // Process the response to find the generated image
        if (response.candidates && response.candidates.length > 0 && response.candidates[0].content && response.candidates[0].content.parts) {
            for (const part of response.candidates[0].content.parts) {
                if (part.inlineData && part.inlineData.mimeType && part.inlineData.mimeType.startsWith('image/')) {
                    const imageData = part.inlineData.data;
                    if (imageData) {
                        const buffer = Buffer.from(imageData, "base64");
                        const filename = `generated-image-${Date.now()}.png`; // TODO: Determine file extension dynamically
                        const filePath = path.join(outputDir, filename);
                        fs.writeFileSync(filePath, buffer);
                        console.log(`Image saved as ${filePath}`);
                        // Return a URL or path that the client can access.
                        // For simplicity, returning the filename relative to a potential serving directory.
                        generatedImageUrl = `/generated_images/${filename}`;
                        break; // Assuming only one image is generated per request
                    }
                }
            }
        }

        if (generatedImageUrl) {
            console.log("Image generation successful.");
            return { success: true, imageUrl: generatedImageUrl };
        } else {
            console.warn("Image generation successful, but no image part found in response.");
            return { success: false, error: "Image generation successful, but no image was returned by the model." };
        }

    } catch (error: any) {
        console.error("Error during image generation:", error);
        return { success: false, error: `Image generation failed: ${error.message}` };
    }
}