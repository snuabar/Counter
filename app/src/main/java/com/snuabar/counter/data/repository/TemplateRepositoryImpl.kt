package com.snuabar.counter.data.repository

import com.snuabar.counter.data.local.db.dao.TemplateDao
import com.snuabar.counter.data.local.db.entity.TemplateEntity
import com.snuabar.counter.domain.model.ActionType
import com.snuabar.counter.domain.model.SessionMode
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType
import com.snuabar.counter.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TemplateRepositoryImpl @Inject constructor(
    private val templateDao: TemplateDao
) : TemplateRepository {

    override suspend fun createTemplate(template: Template): Long {
        return templateDao.insert(template.toEntity())
    }

    override suspend fun getTemplate(id: Long): Template? {
        return templateDao.getById(id)?.toDomain()
    }

    override fun getAllTemplates(): Flow<List<Template>> {
        return templateDao.getAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTemplatesByUserId(userId: Long): Flow<List<Template>> {
        return templateDao.getByUserId(userId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getBuiltinTemplates(): List<Template> {
        return templateDao.getAll().map { list ->
            list.filter { it.userId == null }.map { it.toDomain() }
        }.let { flow ->
            var result = emptyList<Template>()
            flow.collect { result = it }
            result
        }
    }

    override suspend fun deleteTemplate(id: Long) {
        templateDao.delete(id)
    }

    override suspend fun ensureBuiltinTemplates() {
        val existing = templateDao.getAll().first()
        if (existing.none { it.type == TemplateType.BUILTIN.name }) {
            val builtinTemplates = listOf(
                TemplateEntity(
                    name = "俯卧撑",
                    type = TemplateType.BUILTIN.name,
                    sensorType = SensorType.VISION.name,
                    mode = SessionMode.COUNTING.name,
                    actionType = ActionType.PUSH_UP.name,
                    threshold = 0.7f
                ),
                TemplateEntity(
                    name = "深蹲",
                    type = TemplateType.BUILTIN.name,
                    sensorType = SensorType.VISION.name,
                    mode = SessionMode.COUNTING.name,
                    actionType = ActionType.SQUAT.name,
                    threshold = 0.7f
                ),
                TemplateEntity(
                    name = "平板支撑",
                    type = TemplateType.BUILTIN.name,
                    sensorType = SensorType.VISION.name,
                    mode = SessionMode.TIMER.name,
                    actionType = null,
                    targetSeconds = 60,
                    threshold = 0.7f
                )
            )
            builtinTemplates.forEach { templateDao.insert(it) }
        }
    }

    // Mappers
    private fun Template.toEntity() = TemplateEntity(
        id = id,
        userId = userId,
        name = name,
        type = type.name,
        sensorType = sensorType.name,
        mode = mode.name,
        actionType = actionType?.name,
        targetSeconds = targetSeconds,
        mediaPath = mediaPath,
        featureVector = featureVector,
        threshold = threshold,
        createdAt = createdAt
    )

    private fun TemplateEntity.toDomain() = Template(
        id = id,
        userId = userId,
        name = name,
        type = TemplateType.valueOf(type),
        sensorType = SensorType.valueOf(sensorType),
        mode = SessionMode.valueOf(mode),
        actionType = actionType?.let { ActionType.valueOf(it) },
        targetSeconds = targetSeconds,
        mediaPath = mediaPath,
        featureVector = featureVector,
        threshold = threshold,
        createdAt = createdAt
    )
}
