package com.gamesmith.assistantapp.domain.tool

import android.content.Context
import com.gamesmith.assistantapp.data.model.ToolUiElement
import com.gamesmith.assistantapp.data.model.ToolUiSchema
import kotlinx.coroutines.CoroutineScope

class TestTool : NativeTool {
    override val name: String = "test_gui_tool"
    override val description: String = "Shows a GUI overlay with an input field. After the user submits, your response should be: 'You entered: ' followed by the value from the 'echo' field in the tool's output."
    override val parametersJsonSchema: String = "{}" // No parameters for this test tool
    override val defaultBehavior: String? = "NON_BLOCKING"
    override val defaultScheduling: String? = "WHEN_IDLE"

    override suspend fun execute(
        args: Map<String, Any>,
        serviceContext: Context,
        serviceScope: CoroutineScope
    ): ToolExecutionResult {
        // If user input is present, return it as success
        val userInput = args["test_input"] as? String
        if (userInput != null) {
            return ToolExecutionResult.Success(mapOf(
                "test_input" to userInput,
                "echo" to userInput
            ))
        }
        // Show a schema-driven overlay with text, input, and button
        val schema = ToolUiSchema(
            elements = listOf(
                ToolUiElement.Text("This is a test of the Gemini GUI tool system. Please enter any value and press Submit."),
                ToolUiElement.InputField(label = "Test Input", id = "test_input", hint = "Type something..."),
                ToolUiElement.Button(label = "Submit", action = "submit")
            )
        )
        // The framework will resume tool execution with user input after overlay interaction
        return ToolExecutionResult.NeedsConfirmation(
            prompt = "Please complete the test input.",
            details = emptyMap(),
            schema = schema
        )
    }
} 