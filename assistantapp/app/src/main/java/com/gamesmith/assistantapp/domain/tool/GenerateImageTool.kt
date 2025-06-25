package com.gamesmith.assistantapp.domain.tool

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import com.gamesmith.assistantapp.data.model.ToolUiSchema
import com.gamesmith.assistantapp.data.remote.GeminiRepository // Assuming interaction via repository

class GenerateImageTool(
    private val geminiRepository: GeminiRepository // Inject GeminiRepository for server communication
) : NativeTool {
    override val name: String = "generate_image_tool"
    override val description: String = "Generates an image based on a text prompt and an optional input image URI."
    override val parametersJsonSchema: String = """
        {
          "type": "object",
          "properties": {
            "text": {
              "type": "string",
              "description": "The text prompt for image generation."
            },
            "imageUri": {
              "type": "string",
              "description": "Optional URI of an image to use as input."
            }
          },
          "required": ["text"]
        }
    """.trimIndent()
    override val defaultBehavior: String? = "NON_BLOCKING" // Image generation can take time
    override val defaultScheduling: String? = "WHEN_IDLE" // Avoid blocking critical operations

    override suspend fun execute(
        args: Map<String, Any>,
        serviceContext: Context,
        serviceScope: CoroutineScope
    ): ToolExecutionResult {
        val text = args["text"] as? String
        val imageUriString = args["imageUri"] as? String

        if (text.isNullOrBlank()) {
            return ToolExecutionResult.Error("Missing required 'text' parameter for image generation.")
        }

        var imageData: String? = null
        if (imageUriString != null) {
            try {
                val imageUri = Uri.parse(imageUriString)
                serviceContext.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    imageData = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                return ToolExecutionResult.Error("Failed to read image from URI: ${e.message}")
            }
        }

        // Call the generateImage function in the repository with imageData
        val result = geminiRepository.generateImage(text, imageData)

        return if (result.isSuccess) {
            val imageUrl = result.getOrNull()
            if (imageUrl != null) {
                // Return success with the image URL
                ToolExecutionResult.Success(mapOf("imageUrl" to imageUrl))
            } else {
                // Should not happen if isSuccess is true, but handle defensively
                ToolExecutionResult.Error("Image generation succeeded but returned no image URL.")
            }
        } else {
            // Return error with the failure message
            ToolExecutionResult.Error("Image generation failed: ${result.exceptionOrNull()?.message}")
        }
    }
}