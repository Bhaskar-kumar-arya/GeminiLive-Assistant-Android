package com.gamesmith.assistantapp.di

import com.gamesmith.assistantapp.domain.tool.CreateCanvasTool
import com.gamesmith.assistantapp.domain.tool.AppendToCanvasTool
import com.gamesmith.assistantapp.domain.tool.NativeTool
import com.gamesmith.assistantapp.domain.tool.FindContactTool
import com.gamesmith.assistantapp.domain.tool.TakePhotoTool
import com.gamesmith.assistantapp.domain.tool.SendMessageTool
import com.gamesmith.assistantapp.service.ToolUIManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.scopes.ServiceScoped
import dagger.multibindings.IntoSet

@Module
@InstallIn(ServiceComponent::class)
abstract class ServiceToolModule {
    @Binds
    @IntoSet
    @ServiceScoped
    abstract fun bindCreateCanvasTool(tool: CreateCanvasTool): NativeTool

    @Binds
    @IntoSet
    @ServiceScoped
    abstract fun bindAppendToCanvasTool(tool: AppendToCanvasTool): NativeTool

    @Binds
    @IntoSet
    @ServiceScoped
    abstract fun bindFindContactTool(tool: FindContactTool): NativeTool

    @Binds
    @IntoSet
    @ServiceScoped
    abstract fun bindTakePhotoTool(tool: TakePhotoTool): NativeTool

    @Binds
    @IntoSet
    @ServiceScoped
    abstract fun bindSendMessageTool(tool: SendMessageTool): NativeTool

    @Binds
    @IntoSet
    @ServiceScoped
    abstract fun bindSendScreenSnapshotTool(tool: com.gamesmith.assistantapp.domain.tool.SendScreenSnapshotTool): NativeTool

    companion object {
        @Provides
        @ServiceScoped
        fun provideCreateCanvasTool(toolUIManager: ToolUIManager): CreateCanvasTool = CreateCanvasTool(toolUIManager)

        @Provides
        @ServiceScoped
        fun provideAppendToCanvasTool(toolUIManager: ToolUIManager): AppendToCanvasTool = AppendToCanvasTool(toolUIManager)

        @Provides
        @ServiceScoped
        fun provideFindContactTool(): FindContactTool = FindContactTool()

        @Provides
        @ServiceScoped
        fun provideTakePhotoTool(): TakePhotoTool = TakePhotoTool()

        @Provides
        @ServiceScoped
        fun provideSendMessageTool(): SendMessageTool = SendMessageTool()

        @Provides
        @ServiceScoped
        fun provideSendScreenSnapshotTool(): com.gamesmith.assistantapp.domain.tool.SendScreenSnapshotTool = com.gamesmith.assistantapp.domain.tool.SendScreenSnapshotTool()

        @Provides
        @ServiceScoped
        fun provideToolExecutor(tools: Set<@JvmSuppressWildcards NativeTool>): com.gamesmith.assistantapp.domain.tool.ToolExecutor = com.gamesmith.assistantapp.domain.tool.ToolExecutor(tools)
    }
} 