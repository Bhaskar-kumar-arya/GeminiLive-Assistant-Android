package com.gamesmith.assistantapp.di

import com.gamesmith.assistantapp.domain.tool.GetCurrentTimeTool
import com.gamesmith.assistantapp.domain.tool.NativeTool
import com.gamesmith.assistantapp.domain.tool.TestTool
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolModule {
    @Binds
    @IntoSet
    abstract fun bindGetCurrentTimeTool(tool: GetCurrentTimeTool): NativeTool

    @Binds
    @IntoSet
    abstract fun bindTestTool(tool: TestTool): NativeTool

    companion object {
        @Provides
        @Singleton
        fun provideGetCurrentTimeTool(): GetCurrentTimeTool = GetCurrentTimeTool()

        @Provides
        @Singleton
        fun provideTestTool(): TestTool = TestTool()
    }
} 