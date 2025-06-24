package com.gamesmith.assistantapp.domain.tool

import android.content.Context
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
        val imageUri = args["imageUri"] as? String

        if (text.isNullOrBlank()) {
            return ToolExecutionResult.Error("Missing required 'text' parameter for image generation.")
        }
        // Call the generateImage function in the repository
        val result = geminiRepository.generateImage(text, imageUri)

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