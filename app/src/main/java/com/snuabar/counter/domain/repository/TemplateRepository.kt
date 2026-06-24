package com.snuabar.counter.domain.repository

import com.snuabar.counter.domain.model.Template
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    suspend fun createTemplate(template: Template): Long
    suspend fun getTemplate(id: Long): Template?
    fun getAllTemplates(): Flow<List<Template>>
    suspend fun getBuiltinTemplates(): List<Template>
}
