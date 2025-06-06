package com.gamesmith.assistantapp.service

import com.gamesmith.assistantapp.data.model.ToolUiSchema
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.scopes.ServiceScoped

interface ToolUIManager {
    fun showToolUI(schema: ToolUiSchema, callback: (Map<String, Any>) -> Unit)
    fun dismissToolUI()
    fun showStatusOverlay(text: String)
    fun showCanvasOverlay(elements: List<com.gamesmith.assistantapp.data.model.ToolUiElement>, callback: (Map<String, Any>) -> Unit)
    fun appendToCanvasOverlay(elements: List<com.gamesmith.assistantapp.data.model.ToolUiElement>, callback: (Map<String, Any>) -> Unit)
    fun clearCanvasOverlay()
    fun dismissOverlay(type: String)
}

@Module
@InstallIn(ServiceComponent::class)
abstract class ToolUIManagerModule {
    @Binds
    @ServiceScoped
    abstract fun bindToolUIManager(impl: SystemAlertWindowToolUIManager): ToolUIManager
} 